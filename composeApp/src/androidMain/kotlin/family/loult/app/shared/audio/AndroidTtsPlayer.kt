package family.loult.app.shared.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Android TtsPlayer backed by ExoPlayer. Audio clips are fetched through the
 * shared (cookie-aware) Ktor HttpClient and cached as files in the app's
 * cacheDir, then enqueued as ExoPlayer MediaItems. ExoPlayer is responsible
 * for sequential playback; we only need to nudge it back to playing when its
 * playlist has fully drained between arrivals.
 */
class AndroidTtsPlayer(
    private val context: Context,
    private val httpClient: HttpClient,
) : TtsPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = Channel<String>(capacity = Channel.UNLIMITED)
    private val main = Handler(Looper.getMainLooper())

    // ExoPlayer must be created and used on the main thread.
    private lateinit var player: ExoPlayer

    @Volatile private var muted = false
    private val downloader: Job

    init {
        main.post {
            player = ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
            }
        }
        downloader = scope.launch {
            for (audioId in downloads) {
                if (muted) continue
                runCatching { downloadAndQueue(audioId) }
                    .onFailure { it.printStackTrace() }
            }
        }
    }

    override fun enqueue(audioId: String) {
        if (muted) return
        downloads.trySend(audioId)
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) {
            main.post {
                if (::player.isInitialized) {
                    player.stop()
                    player.clearMediaItems()
                }
            }
        }
    }

    override fun release() {
        downloads.close()
        scope.cancel()
        main.post {
            if (::player.isInitialized) player.release()
        }
    }

    private suspend fun downloadAndQueue(audioId: String) {
        val url = "https://loult.family/audio/$audioId/"
        val bytes = httpClient.get(url).bodyAsBytes()
        if (bytes.isEmpty()) return
        val file = File(context.cacheDir, "tts-${UUID.randomUUID()}.bin").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        main.post {
            if (muted || !::player.isInitialized) return@post
            val item = MediaItem.fromUri(Uri.fromFile(file))
            val wasIdle = player.playbackState == Player.STATE_IDLE
            val wasEnded = player.playbackState == Player.STATE_ENDED
            val emptyQueue = player.mediaItemCount == 0
            player.addMediaItem(item)
            when {
                // Cold start: nothing prepared yet → kick everything off.
                wasIdle -> {
                    player.prepare()
                    player.playWhenReady = true
                }
                // Queue had drained and player parked at the end. Hop to the
                // newly-added item and resume — otherwise ExoPlayer just sits
                // at STATE_ENDED forever.
                wasEnded -> {
                    val newIndex = player.mediaItemCount - 1
                    player.seekTo(newIndex, 0L)
                    player.playWhenReady = true
                }
                emptyQueue -> {
                    player.prepare()
                    player.playWhenReady = true
                }
                // Otherwise the player is BUFFERING/READY/playing; ExoPlayer
                // will auto-advance into our newly-appended item.
                else -> Unit
            }
        }
    }
}

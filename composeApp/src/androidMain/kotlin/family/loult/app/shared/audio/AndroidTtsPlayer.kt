package family.loult.app.shared.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Android TtsPlayer that mirrors loult.family's mixing behaviour: every
 * incoming clip spawns its own MediaPlayer so concurrent voices overlap
 * instead of queueing.
 *
 * Each clip's lifecycle is independent:
 *   1. Download bytes through the shared (cookie-aware) Ktor client on IO.
 *   2. Write to a unique cache file.
 *   3. Hand off to the main thread, build a MediaPlayer, prepareAsync.
 *   4. On completion / error, release the player and delete the file.
 *
 * Mute hard-stops every active player, drops in-flight downloads, and
 * deletes any clip that lands after the toggle.
 */
class AndroidTtsPlayer(
    private val context: Context,
    private val httpClient: HttpClient,
) : TtsPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var muted = false

    // Touched only from the main thread (MediaPlayer callbacks fire on the
    // looper of the thread the instance was constructed on, which is main).
    private val active = mutableListOf<MediaPlayer>()

    override fun play(audioId: String) {
        if (muted) return
        scope.launch {
            runCatching { downloadAndPlay(audioId) }
                .onFailure { it.printStackTrace() }
        }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) main.post { stopAll() }
    }

    override fun release() {
        scope.cancel()
        main.post { stopAll() }
    }

    private suspend fun downloadAndPlay(audioId: String) {
        if (muted) return
        val url = "https://loult.family/audio/$audioId/"
        val bytes = httpClient.get(url).bodyAsBytes()
        if (bytes.isEmpty() || muted) return
        val file = File(context.cacheDir, "tts-${UUID.randomUUID()}.bin")
        file.writeBytes(bytes)
        main.post { startPlayback(file) }
    }

    private fun startPlayback(file: File) {
        if (muted) {
            file.delete()
            return
        }
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnPreparedListener { player ->
                if (muted) cleanup(player, file) else player.start()
            }
            setOnCompletionListener { player -> cleanup(player, file) }
            setOnErrorListener { player, _, _ ->
                cleanup(player, file)
                true
            }
        }
        active += mp
        runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepareAsync()
        }.onFailure {
            it.printStackTrace()
            cleanup(mp, file)
        }
    }

    private fun cleanup(player: MediaPlayer, file: File) {
        active.remove(player)
        runCatching { player.release() }
        file.delete()
    }

    private fun stopAll() {
        val snapshot = active.toList()
        active.clear()
        snapshot.forEach { runCatching { it.release() } }
    }
}

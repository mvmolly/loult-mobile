package family.loult.app.ui.chat

import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun BnlVideoPreview(url: String, modifier: Modifier) {
    val context = LocalContext.current

    var started by remember(url) { mutableStateOf(false) }
    var shouldPlay by remember(url) { mutableStateOf(false) }
    var firstFrameRendered by remember(url) { mutableStateOf(false) }
    var thumbnail by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    // Extract a thumbnail frame asynchronously so the chat row never blocks.
    LaunchedEffect(url) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(url, emptyMap<String, String>())
                    mmr.frameAtTime?.asImageBitmap()
                } finally {
                    mmr.release()
                }
            }.getOrNull()
        }
    }

    val player: ExoPlayer? = remember(url, started) {
        if (!started) null
        else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrameRendered = true }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) shouldPlay = false
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(player, shouldPlay) {
        val p = player ?: return@LaunchedEffect
        if (shouldPlay) {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.playWhenReady = true
        } else {
            p.playWhenReady = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 1: thumbnail. Stays under the player until the first video
        // frame is rendered, so there's no flash of empty player surface.
        thumbnail?.takeIf { !firstFrameRendered }?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // Layer 2: ExoPlayer surface. Transparent shutter so the thumbnail
        // shows through during buffering.
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        controllerAutoShow = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        @Suppress("DEPRECATION")
                        useArtwork = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                        isClickable = false
                        isFocusable = false
                    }
                },
            )
        }
        // Layer 3: tap target + play overlay.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    if (!started) {
                        started = true
                        shouldPlay = true
                    } else {
                        shouldPlay = !shouldPlay
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!shouldPlay) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Lire",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

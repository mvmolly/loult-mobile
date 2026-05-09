package family.loult.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import family.loult.app.domain.model.ChatMessage
import family.loult.app.domain.model.LoultUser
import family.loult.app.ui.components.PokemonAvatar
import family.loult.app.ui.components.SwipeRightToTrigger
import family.loult.app.ui.theme.LoultPalette

private val RowHorizontal = 16.dp
private val AvatarSize = 44.dp
private val AvatarGap = 12.dp
private val SystemIndent = RowHorizontal + AvatarSize + AvatarGap

private val BnlImageRegex = Regex("""https://bnl\.loult\.family/media/content/image/[a-z0-9]+""")
private val BnlVideoRegex = Regex("""https://bnl\.loult\.family/media/content/video/[a-z0-9]+""")

@Composable
fun MessageRow(
    message: ChatMessage,
    previewImages: Boolean = true,
    muted: Boolean = false,
    onUserClick: (LoultUser) -> Unit = {},
    onToggleMute: (LoultUser) -> Unit = {},
    onCopyText: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sender: LoultUser? = when (message) {
        is ChatMessage.Text -> message.from
        is ChatMessage.Bot -> message.from
        is ChatMessage.Me -> message.from
        is ChatMessage.System -> null
    }
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = RowHorizontal)
                .alpha(if (muted) 0.4f else 1f),
            verticalAlignment = Alignment.Top,
        ) {
            when (message) {
                is ChatMessage.Text -> AvatarLine(message.from, onUserClick) {
                    ChatBody(message.from, message.body, previewImages = previewImages, onUserClick = onUserClick, onCopyText = onCopyText)
                }
                is ChatMessage.Bot -> AvatarLine(message.from, onUserClick) {
                    ChatBody(message.from, message.body, italic = true, previewImages = previewImages, onUserClick = onUserClick, onCopyText = onCopyText)
                }
                is ChatMessage.Me -> AvatarLine(message.from, onUserClick) { ActionBody(message.from, message.body, onCopyText = onCopyText) }
                is ChatMessage.System -> SystemLine(message.text, message.kind)
            }
        }
    }

    if (sender == null || sender.isYou) {
        // System rows and your own messages: no swipe-to-mute.
        Row(modifier = modifier.fillMaxWidth()) { rowContent() }
        return
    }

    SwipeRightToTrigger(
        modifier = modifier,
        onTrigger = { onToggleMute(sender) },
        background = { progress -> MuteSwipeBackground(muted = muted, progress = progress) },
    ) {
        rowContent()
    }
}

@Composable
private fun MuteSwipeBackground(muted: Boolean, progress: Float) {
    val tint = if (muted) LoultPalette.info else LoultPalette.muted
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(tint.copy(alpha = 0.15f + 0.25f * progress))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (muted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (muted) "Réactiver" else "Muet",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AvatarLine(
    from: LoultUser,
    onUserClick: (LoultUser) -> Unit,
    content: @Composable () -> Unit,
) {
    PokemonAvatar(
        img = from.img,
        size = AvatarSize,
        modifier = Modifier.clickable { onUserClick(from) },
    )
    Spacer(Modifier.width(AvatarGap))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun ChatBody(
    from: LoultUser,
    body: String,
    italic: Boolean = false,
    previewImages: Boolean,
    onUserClick: (LoultUser) -> Unit,
    onCopyText: (String) -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.clickable { onUserClick(from) },
    ) {
        Text(
            text = from.name,
            color = parseColorOr(from.color, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        if (from.adjective.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = from.adjective,
                color = LoultPalette.muted,
                style = MaterialTheme.typography.labelMedium,
                fontStyle = FontStyle.Italic,
            )
        }
    }
    val style = MaterialTheme.typography.bodyLarge
    val annotated = linkify(body)
    Text(
        text = annotated,
        style = if (italic) style.copy(fontStyle = FontStyle.Italic) else style,
        color = if (body.startsWith(">")) LoultPalette.greentext else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.pointerInput(body) {
            detectTapGestures(onLongPress = { onCopyText(body) })
        },
    )
    if (previewImages) {
        BnlImageRegex.findAll(body).forEach { match ->
            BnlImagePreview(url = match.value)
        }
        BnlVideoRegex.findAll(body).forEach { match ->
            BnlVideoPreview(url = match.value)
        }
    }
}

@Composable
private fun BnlImagePreview(url: String) {
    val uriHandler = LocalUriHandler.current
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .heightIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { runCatching { uriHandler.openUri(url) } },
    )
}

@Composable
private fun ActionBody(from: LoultUser, body: String, onCopyText: (String) -> Unit = {}) {
    val text = "* ${from.name} $body"
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.pointerInput(text) {
            detectTapGestures(onLongPress = { onCopyText(text) })
        },
    )
}

@Composable
private fun SystemLine(text: String, kind: ChatMessage.System.Kind) {
    val color = when (kind) {
        ChatMessage.System.Kind.Connect, ChatMessage.System.Kind.Disconnect -> LoultPalette.muted
        ChatMessage.System.Kind.AntifloodBanned, ChatMessage.System.Kind.Error -> MaterialTheme.colorScheme.error
        ChatMessage.System.Kind.AntifloodWarning -> MaterialTheme.colorScheme.error
        else -> LoultPalette.info
    }
    Text(
        text = text,
        modifier = Modifier.padding(start = SystemIndent - RowHorizontal),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = color,
    )
}

private fun parseColorOr(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = hex.removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

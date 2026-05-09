package family.loult.app.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Convert a chat-message body into an AnnotatedString with clickable
 * `LinkAnnotation.Url` ranges over each detected URL. Compose's `Text`
 * composable handles the click + browser intent automatically as long as
 * a LinkInteractionListener (the default) is in place.
 */
private val UrlRegex = Regex("""https?://\S+""")

@Composable
fun linkify(text: String, linkColor: Color = MaterialTheme.colorScheme.primary): AnnotatedString {
    val style = remember(linkColor) {
        TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
            ),
        )
    }
    return remember(text, style) {
        buildAnnotatedString {
            var last = 0
            for (match in UrlRegex.findAll(text)) {
                if (match.range.first > last) {
                    append(text.substring(last, match.range.first))
                }
                val url = match.value.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
                val tail = match.value.substring(url.length)
                val idx = pushLink(LinkAnnotation.Url(url, style))
                append(url)
                pop(idx)
                if (tail.isNotEmpty()) append(tail)
                last = match.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
}

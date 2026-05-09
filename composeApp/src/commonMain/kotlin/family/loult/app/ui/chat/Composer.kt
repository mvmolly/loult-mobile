package family.loult.app.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private enum class ComposerAction { Plus, Uploading, Send }

@Composable
fun Composer(
    text: String,
    enabled: Boolean,
    uploading: Boolean,
    placeholder: String = "Message...",
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onPickImage: () -> Unit,
) {
    val send: () -> Unit = {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && enabled) {
            onSend(trimmed)
        }
    }
    val action = when {
        uploading -> ComposerAction.Uploading
        text.isNotBlank() && enabled -> ComposerAction.Send
        else -> ComposerAction.Plus
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        // Top-anchor so the action button stays at the top-right corner when
        // the input pill grows to several lines.
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                )
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    singleLine = false,
                    maxLines = 5,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = style,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = style,
                            )
                        }
                        inner()
                    },
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Crossfade(
            targetState = action,
            modifier = Modifier.size(48.dp),
            label = "composer-action",
        ) { state ->
            when (state) {
                ComposerAction.Plus -> PlusButton(enabled = enabled, onClick = onPickImage)
                ComposerAction.Send -> SendButton(onClick = send)
                ComposerAction.Uploading -> Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlusButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape),
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Ajouter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SendButton(onClick: () -> Unit) {
    // Soft "tonal" surface with a primary-tinted icon — same shape/size as the
    // + button so the right-edge slot reads as one consistent control.
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Envoyer",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

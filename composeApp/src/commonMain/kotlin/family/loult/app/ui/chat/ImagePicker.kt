package family.loult.app.ui.chat

import androidx.compose.runtime.Composable

/**
 * Platform-specific image picker hook. The returned lambda, when invoked,
 * launches the system media chooser; the [onPicked] callback receives the
 * raw bytes plus a best-effort filename.
 */
@Composable
expect fun rememberImagePicker(onPicked: (bytes: ByteArray, filename: String) -> Unit): () -> Unit

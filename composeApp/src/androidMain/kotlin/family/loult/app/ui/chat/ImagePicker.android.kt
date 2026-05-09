package family.loult.app.ui.chat

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberImagePicker(onPicked: (bytes: ByteArray, filename: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val current by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val name = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "upload"
                    bytes to name
                }
                if (bytes != null && bytes.isNotEmpty()) current(bytes, name)
            }
        }
    }
    return remember(launcher) { { launcher.launch("image/*") } }
}

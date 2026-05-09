package family.loult.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Inline player for BNL video links. Implementations are platform-specific
 *  (ExoPlayer on Android). */
@Composable
expect fun BnlVideoPreview(url: String, modifier: Modifier = Modifier)

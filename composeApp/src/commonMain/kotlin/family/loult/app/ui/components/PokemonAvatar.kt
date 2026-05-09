package family.loult.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private const val SPRITE_BASE = "https://loult.family/static/img/pokemon"

enum class PokemonSize(val path: String) {
    Small("small"),
    Medium("medium"),
    Big("big"),
}

/** Bare animated sprite — no bubble, no shadow. */
@Composable
fun PokemonAvatar(
    img: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    sprite: PokemonSize = PokemonSize.Medium,
) {
    if (img.isNullOrBlank()) return
    val ext = if (sprite == PokemonSize.Big) "png" else "gif"
    val url = "$SPRITE_BASE/${sprite.path}/$img.$ext"
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

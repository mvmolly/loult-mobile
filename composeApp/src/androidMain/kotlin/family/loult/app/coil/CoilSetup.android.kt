package family.loult.app.coil

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.crossfade

fun loultImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .components {
        // Animated GIF support — loult avatars are small animated sprites.
        if (android.os.Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
        else add(GifDecoder.Factory())
    }
    .crossfade(true)
    .build()

@Suppress("unused")
internal fun bind(@Suppress("UNUSED_PARAMETER") c: Context) = Unit

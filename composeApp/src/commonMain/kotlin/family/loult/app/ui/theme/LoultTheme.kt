package family.loult.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Loult palette — distilled from /static/style.css.
// Bibw default: light cream surface, dark text, cyan info accent.
private val LoultCream = Color(0xFFF9F9F9)
private val LoultGray = Color(0xFFDDDDDD)
private val LoultBorder = Color(0xFFBBBBBB)
private val LoultInk = Color(0xFF333333)
private val LoultMuted = Color(0xFF999999)
private val LoultCyan = Color(0xFF87B9BD)
private val LoultLink = Color(0xFF0066AA)
private val LoultAttack = Color(0xFFFF3333)
private val LoultGreen = Color(0xFF33CC33)
private val LoultMagenta = Color(0xFFFF66FF)

private val LoultLightScheme = lightColorScheme(
    primary = LoultCyan,
    onPrimary = LoultInk,
    secondary = LoultLink,
    background = LoultCream,
    onBackground = LoultInk,
    surface = LoultCream,
    onSurface = LoultInk,
    surfaceVariant = LoultGray,
    onSurfaceVariant = LoultInk,
    outline = LoultBorder,
    error = LoultAttack,
    tertiary = LoultGreen,
)

// "Night" variant — inverts panels but keeps loult accents.
private val LoultDarkScheme = darkColorScheme(
    primary = LoultCyan,
    onPrimary = Color.Black,
    secondary = Color(0xFF6CB6FF),
    background = Color(0xFF101015),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF18181F),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF26262E),
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = Color(0xFF3A3A44),
    error = LoultAttack,
    tertiary = LoultGreen,
)

private val LoultTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

object LoultPalette {
    val link: Color = LoultLink
    val attack: Color = LoultAttack
    val greentext: Color = LoultGreen
    val info: Color = LoultCyan
    val muted: Color = LoultMuted
    val spoiler: Color = LoultMagenta
}

@Composable
fun LoultTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) LoultDarkScheme else LoultLightScheme,
        typography = LoultTypography,
        content = content,
    )
}

package com.ilyskyo.blancall.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SepiaColors = lightColorScheme(
    primary = Color(0xFF7A5C3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFE3D2),
    onPrimaryContainer = Color(0xFF2C1E11),
    secondary = Color(0xFF6B6259),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF211B14),
    surfaceVariant = Color(0xFFF0E9DE),
    onSurfaceVariant = Color(0xFF51473C),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF211B14),
    outline = Color(0xFFD6CCBD),
)

/** 米白/纯白底：玻璃质感最轻的一组，适合白天与投屏。 */
private val PlainColors = lightColorScheme(
    primary = Color(0xFF6D5136),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3EADC),
    onPrimaryContainer = Color(0xFF2C1E11),
    secondary = Color(0xFF625B52),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1A16),
    surfaceVariant = Color(0xFFF4F2EE),
    onSurfaceVariant = Color(0xFF4F4A43),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1D1A16),
    outline = Color(0xFFDDD8CF),
)

/** 灰底：冷色中性，长时间阅读对比更稳定。 */
private val GrayColors = lightColorScheme(
    primary = Color(0xFF5C574F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E4DF),
    onPrimaryContainer = Color(0xFF1F1C18),
    secondary = Color(0xFF5F5B55),
    surface = Color(0xFFF6F6F5),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE9E8E5),
    onSurfaceVariant = Color(0xFF4C4A46),
    background = Color(0xFFF0F0EE),
    onBackground = Color(0xFF1C1B19),
    outline = Color(0xFFCFCDC8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8B98F),
    onPrimary = Color(0xFF3A2A17),
    primaryContainer = Color(0xFF4A3822),
    onPrimaryContainer = Color(0xFFF4E2C8),
    secondary = Color(0xFFBFB3A4),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFEDE4D8),
    surfaceVariant = Color(0xFF2A241D),
    onSurfaceVariant = Color(0xFFD2C6B7),
    background = Color(0xFF100D0A),
    onBackground = Color(0xFFEDE4D8),
    outline = Color(0xFF4A4035),
)

@Composable
fun BlancallTheme(
    themeMode: String,
    systemDark: Boolean,
    backgroundStyle: String = "sepia",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val light = when (backgroundStyle) {
        "plain" -> PlainColors
        "gray" -> GrayColors
        else -> SepiaColors
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else light,
        content = content,
    )
}

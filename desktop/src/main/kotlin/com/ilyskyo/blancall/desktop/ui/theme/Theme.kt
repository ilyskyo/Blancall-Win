package com.ilyskyo.blancall.desktop.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 当前是否深色。与 Android 的 LocalIsDark 同义，所有自绘组件读它而不是 isSystemInDarkTheme。 */
val LocalIsDark = compositionLocalOf { false }

/** 当前强调色（供自绘指示器、选中态使用）。 */
val LocalAccent = compositionLocalOf { Color(0xFF3B5DE7) }

@Composable
fun isBlancallDark(): Boolean = LocalIsDark.current

/** HSV 色相旋转（纯 Kotlin，不依赖 Android 平台类）。 */
private fun rotateHue(color: Color, degrees: Float): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxV = max(max(r, g), b)
    val minV = min(min(r, g), b)
    val delta = maxV - minV
    var hue = when {
        delta == 0f -> 0f
        maxV == r -> 60f * (((g - b) / delta).mod(6f))
        maxV == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    hue = (hue + degrees).mod(360f)
    val s = if (maxV == 0f) 0f else delta / maxV
    val c = maxV * s
    val x = c * (1f - abs((hue / 60f).mod(2f) - 1f))
    val m = maxV - c
    val (r1, g1, b1) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m, color.alpha)
}

private fun Color.readableOn(background: Color): Color {
    fun channel(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val l = 0.2126f * channel(background.red) + 0.7152f * channel(background.green) + 0.0722f * channel(background.blue)
    return if (l > 0.45f) Color(0xFF1A1A1A) else Color.White
}

private fun darken(color: Color, factor: Float): Color =
    lerp(color, Color.Black, (1f - factor).coerceIn(0f, 1f))

private fun buildLightScheme(accent: AccentPreset, useBeige: Boolean) = run {
    val s = if (useBeige) LightPaper else LightNeutral
    val tertiary = rotateHue(accent.primaryLight, 150f)
    val tertiaryContainer = Color.White.copy(alpha = 0.84f).compositeOver(tertiary)
    lightColorScheme(
        primary = accent.primaryLight,
        onPrimary = accent.onPrimaryLight,
        primaryContainer = accent.primaryContainerLight,
        onPrimaryContainer = accent.onPrimaryContainerLight,
        secondary = accent.primaryLight,
        onSecondary = accent.onPrimaryLight,
        secondaryContainer = accent.primaryContainerLight.copy(alpha = 0.5f).compositeOver(s.surface),
        onSecondaryContainer = accent.onPrimaryContainerLight,
        tertiary = tertiary,
        onTertiary = tertiary.readableOn(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = darken(tertiaryContainer, 0.72f),
        background = s.background,
        onBackground = OnBackgroundLight,
        surface = s.surface,
        onSurface = OnSurfaceLight,
        surfaceVariant = s.surfaceVariant,
        onSurfaceVariant = s.onSurfaceVariant,
        surfaceContainerLowest = s.containerLowest,
        surfaceContainerLow = s.containerLow,
        surfaceContainer = s.container,
        surfaceContainerHigh = s.containerHigh,
        surfaceContainerHighest = s.containerHighest,
        surfaceDim = s.dim,
        surfaceBright = s.bright,
        outline = OutlineLight,
        outlineVariant = s.outlineVariant,
        error = ErrorLight,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410E0B),
        inverseSurface = Color(0xFF322F2A),
        inverseOnSurface = Color(0xFFF5EFE2),
        inversePrimary = Color(0xFF8DA9FF),
        scrim = Color.Black,
    )
}

private fun buildDarkScheme(accent: AccentPreset) = run {
    val tertiary = rotateHue(accent.primaryDark, 150f)
    darkColorScheme(
        primary = accent.primaryDark,
        onPrimary = accent.onPrimaryDark,
        primaryContainer = accent.primaryContainerDark,
        onPrimaryContainer = accent.onPrimaryContainerDark,
        secondary = accent.primaryDark,
        onSecondary = accent.onPrimaryDark,
        secondaryContainer = accent.primaryContainerDark.copy(alpha = 0.5f).compositeOver(SurfaceDark),
        onSecondaryContainer = accent.onPrimaryContainerDark,
        tertiary = tertiary,
        onTertiary = tertiary.readableOn(tertiary),
        tertiaryContainer = darken(tertiary, 0.55f),
        onTertiaryContainer = Color.White.copy(alpha = 0.80f).compositeOver(tertiary),
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        surfaceContainerLowest = SurfaceDark,
        surfaceContainerLow = Color(0xFF161618),
        surfaceContainer = SurfaceVariantDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = Color(0xFF48484A),
        surfaceDim = Color(0xFF141416),
        surfaceBright = SurfaceVariantDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        error = ErrorDark,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE7E5DE),
        inverseOnSurface = Color(0xFF322F2A),
        inversePrimary = Color(0xFF3B5DE7),
        scrim = Color.Black,
    )
}

/**
 * Windows 主题：与 Android 版同一套角色与色值。
 *
 * @param backgroundStyle sepia = 米黄纸张；plain = 纯白；gray 兼容旧设置按纯白处理
 * @param accentIndex AppPrefs.accentIndex（0..5）
 */
@Composable
fun BlancallTheme(
    themeMode: String,
    systemDark: Boolean,
    backgroundStyle: String = "sepia",
    accentIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val accent = AccentPresets[accentIndex.coerceIn(0, AccentPresets.lastIndex)]
    val target = if (dark) buildDarkScheme(accent) else buildLightScheme(accent, backgroundStyle != "plain")

    // 换主题时整页颜色「溶」过去（与 Android 一致），避免白→黑瞬间闪灰。
    val spec = tween<Color>(durationMillis = 180)
    val scheme = target.copy(
        background = animateColorAsState(target.background, spec, label = "bg").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "onBg").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant").value,
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        outline = animateColorAsState(target.outline, spec, label = "outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, spec, label = "outlineVariant").value,
    )

    CompositionLocalProvider(
        LocalIsDark provides dark,
        LocalAccent provides accent.primaryLight,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BlancallTypography,
            shapes = BlancallShapes,
            content = content,
        )
    }
}

package com.ilyskyo.blancall.desktop.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ── 与 Android 版逐位一致的基础色板（ui/theme/Color.kt）──

val OnBackgroundLight = Color(0xFF1E1B18)
val OnSurfaceLight = Color(0xFF1E1B18)
val OutlineLight = Color(0xFF7D7A74)

@Immutable
data class LightSurfaces(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outlineVariant: Color,
    val containerLowest: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
    val dim: Color,
    val bright: Color,
    val onSurfaceVariant: Color,
)

/** 米黄（纸张）：暖、偏黄，正文块读起来像书页。 */
val LightPaper = LightSurfaces(
    background = Color(0xFFF6F0E5),
    surface = Color(0xFFFFFCF8),
    surfaceVariant = Color(0xFFF0EBE3),
    outlineVariant = Color(0xFFC9C3BB),
    containerLowest = Color(0xFFFFFFFF),
    containerLow = Color(0xFFFBF7F0),
    container = Color(0xFFF6F0E5),
    containerHigh = Color(0xFFEFE8DC),
    containerHighest = Color(0xFFE8E0D2),
    dim = Color(0xFFEFE8DC),
    bright = Color(0xFFFFFCF8),
    onSurfaceVariant = Color(0xFF4F4C47),
)

/** 中性（标准软件色）：冷白、干净。 */
val LightNeutral = LightSurfaces(
    background = Color(0xFFF3F3F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9E9EC),
    outlineVariant = Color(0xFFCFCFD4),
    containerLowest = Color(0xFFFFFFFF),
    containerLow = Color(0xFFF9F9FB),
    container = Color(0xFFF3F3F5),
    containerHigh = Color(0xFFEDEDF0),
    containerHighest = Color(0xFFE6E6EA),
    dim = Color(0xFFEDEDF0),
    bright = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF4A4A4F),
)

// ── 深色：纯黑底 + 逐级抬升（与 Android 相同）──
val BackgroundDark = Color(0xFF000000)
val OnBackgroundDark = Color(0xFFF4F5F8)
val SurfaceDark = Color(0xFF1C1C1E)
val OnSurfaceDark = Color(0xFFF4F5F8)
val SurfaceVariantDark = Color(0xFF2C2C2E)
val SurfaceContainerHighDark = Color(0xFF3A3A3C)
val OnSurfaceVariantDark = Color(0xFFCBD0DA)
val OutlineDark = Color(0xFF9097A2)
val OutlineVariantDark = Color(0xFF4A4E57)
val ErrorLight = Color(0xFFBA1A1A)
val ErrorDark = Color(0xFFFFB4AB)

@Immutable
data class AccentPreset(
    val name: String,
    val primaryLight: Color,
    val onPrimaryLight: Color,
    val primaryContainerLight: Color,
    val onPrimaryContainerLight: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryContainerDark: Color,
    val onPrimaryContainerDark: Color,
)

val AccentPresets = listOf(
    AccentPreset(
        name = "靛蓝",
        primaryLight = Color(0xFF3B5DE7),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFDDE1FF),
        onPrimaryContainerLight = Color(0xFF001452),
        primaryDark = Color(0xFF8DA9FF),
        onPrimaryDark = Color(0xFF001B7E),
        primaryContainerDark = Color(0xFF1F36A5),
        onPrimaryContainerDark = Color(0xFFDDE1FF),
    ),
    AccentPreset(
        name = "海蓝",
        primaryLight = Color(0xFF1565C0),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFD1E4FF),
        onPrimaryContainerLight = Color(0xFF001D36),
        primaryDark = Color(0xFF9ECAFF),
        onPrimaryDark = Color(0xFF003258),
        primaryContainerDark = Color(0xFF00497D),
        onPrimaryContainerDark = Color(0xFFD1E4FF),
    ),
    AccentPreset(
        name = "翠绿",
        primaryLight = Color(0xFF006C4C),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFF89F8C7),
        onPrimaryContainerLight = Color(0xFF002114),
        primaryDark = Color(0xFF6CDBA7),
        onPrimaryDark = Color(0xFF003826),
        primaryContainerDark = Color(0xFF005139),
        onPrimaryContainerDark = Color(0xFF89F8C7),
    ),
    AccentPreset(
        name = "暖橙",
        primaryLight = Color(0xFF9C4300),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFFFDBC8),
        onPrimaryContainerLight = Color(0xFF331100),
        primaryDark = Color(0xFFFFB787),
        onPrimaryDark = Color(0xFF532200),
        primaryContainerDark = Color(0xFF763300),
        onPrimaryContainerDark = Color(0xFFFFDBC8),
    ),
    AccentPreset(
        name = "玫红",
        primaryLight = Color(0xFFB31654),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFFFD9E2),
        onPrimaryContainerLight = Color(0xFF3E0018),
        primaryDark = Color(0xFFFFB0CB),
        onPrimaryDark = Color(0xFF65002D),
        primaryContainerDark = Color(0xFF8E0041),
        onPrimaryContainerDark = Color(0xFFFFD9E2),
    ),
    AccentPreset(
        name = "石墨",
        primaryLight = Color(0xFF595959),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFDBDBDB),
        onPrimaryContainerLight = Color(0xFF1A1A1A),
        primaryDark = Color(0xFFC2C2C2),
        onPrimaryDark = Color(0xFF2E2E2E),
        primaryContainerDark = Color(0xFF424242),
        onPrimaryContainerDark = Color(0xFFDBDBDB),
    ),
)

// ── 界面字体：标题用霞鹜文楷，正文用系统无衬线 ──

private val XwwkFamily = FontFamily(Font("fonts/xwwk_regular.ttf", FontWeight.Normal))

val BlancallTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 40.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = XwwkFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
)

// ── 超椭圆圆角（与 Android SmoothShape.kt 相同，n=3）──

@Immutable
class SmoothRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart == 0f && topEnd == 0f && bottomEnd == 0f && bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (ltr) topStart else topEnd
        val topRight = if (ltr) topEnd else topStart
        val bottomRight = if (ltr) bottomEnd else bottomStart
        val bottomLeft = if (ltr) bottomStart else bottomEnd

        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(topLeft, 0f)
            lineTo(w - topRight, 0f)
            appendSuperellipseCorner(this, w, 0f, topRight, -1f, 1f, reversed = true)
            lineTo(w, h - bottomRight)
            appendSuperellipseCorner(this, w, h, bottomRight, -1f, -1f, reversed = false)
            lineTo(bottomLeft, h)
            appendSuperellipseCorner(this, 0f, h, bottomLeft, 1f, -1f, reversed = true)
            lineTo(0f, topLeft)
            appendSuperellipseCorner(this, 0f, 0f, topLeft, 1f, 1f, reversed = false)
            close()
        }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): SmoothRoundedCornerShape = SmoothRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?): Boolean =
        other is SmoothRoundedCornerShape &&
            topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return result
    }
}

private const val CORNER_SEGMENTS = 16
private const val EXPONENT = 3f
private val HALF_PI = (PI / 2).toFloat()

private fun appendSuperellipseCorner(
    path: Path,
    cornerX: Float,
    cornerY: Float,
    radius: Float,
    horizontalSign: Float,
    verticalSign: Float,
    reversed: Boolean,
) {
    if (radius <= 0f) return
    for (i in 1..CORNER_SEGMENTS) {
        val step = if (reversed) CORNER_SEGMENTS - i else i
        val angle = step * HALF_PI / CORNER_SEGMENTS
        val c = cos(angle)
        val s = sin(angle)
        val radial = 1f / (c.pow(EXPONENT) + s.pow(EXPONENT)).pow(1f / EXPONENT)
        val u = 1f - radial * c
        val v = 1f - radial * s
        path.lineTo(cornerX + horizontalSign * u * radius, cornerY + verticalSign * v * radius)
    }
}

fun SmoothRoundedCornerShape(size: Dp): SmoothRoundedCornerShape = SmoothRoundedCornerShape(
    CornerSize(size), CornerSize(size), CornerSize(size), CornerSize(size)
)

fun SmoothRoundedCornerShape(size: Float): SmoothRoundedCornerShape = SmoothRoundedCornerShape(
    CornerSize(size), CornerSize(size), CornerSize(size), CornerSize(size)
)

fun SmoothRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): SmoothRoundedCornerShape = SmoothRoundedCornerShape(
    CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart)
)

val BlancallShapes = Shapes(
    extraSmall = SmoothRoundedCornerShape(12.dp),
    small = SmoothRoundedCornerShape(16.dp),
    medium = SmoothRoundedCornerShape(24.dp),
    large = SmoothRoundedCornerShape(32.dp),
    extraLarge = SmoothRoundedCornerShape(40.dp),
)

// ── 与 Android 一致的马卡龙淡色板（用于首页卡片）──
object Macaron {
    private val lightFill = intArrayOf(
        0xFFFBEFF3.toInt(), 0xFFEAF5EF.toInt(), 0xFFEAF1FB.toInt(),
        0xFFFDF2E0.toInt(), 0xFFF1ECFB.toInt(), 0xFFF4F0EB.toInt(),
    )
    private val lightAccent = intArrayOf(
        0xFFEE9DB4.toInt(), 0xFF7FC6A3.toInt(), 0xFF8FB2E6.toInt(),
        0xFFEFB86E.toInt(), 0xFFB79BE0.toInt(), 0xFFB6AFA4.toInt(),
    )
    private val darkFill = intArrayOf(
        0xFF3A2A30.toInt(), 0xFF233029.toInt(), 0xFF252C38.toInt(),
        0xFF3A3228.toInt(), 0xFF2E2738.toInt(), 0xFF2A2A2C.toInt(),
    )
    private val darkAccent = intArrayOf(
        0xFFE9A9B8.toInt(), 0xFFA9DCC0.toInt(), 0xFFA9C4EC.toInt(),
        0xFFE8C892.toInt(), 0xFFC9B0E8.toInt(), 0xFF9A958C.toInt(),
    )

    fun fill(index: Int, dark: Boolean): Color {
        val i = index.coerceIn(0, lightFill.lastIndex)
        return Color(if (dark) darkFill[i] else lightFill[i])
    }

    fun accent(index: Int, dark: Boolean): Color {
        val i = index.coerceIn(0, lightAccent.lastIndex)
        return Color(if (dark) darkAccent[i] else lightAccent[i])
    }
}

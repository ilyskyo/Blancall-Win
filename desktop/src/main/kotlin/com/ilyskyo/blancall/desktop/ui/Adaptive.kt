package com.ilyskyo.blancall.desktop.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 窗口宽度档位，与 Android 版 `ui/common/Adaptive.kt` 同一套断点：
 * - Compact  < 600dp：手机布局（底部导航 + 单列）
 * - Medium   600–839dp：平板竖屏 / 折叠屏展开（左侧悬浮 rail）
 * - Expanded ≥ 840dp：平板横屏（rail + 多列）
 *
 * 桌面窗口可以任意缩放，断点必须只看**内容可用宽度**，不能看屏幕宽度。
 */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassFor(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/** 由根组合提供；未提供时按手机档兜底，避免预览/测试因缺省崩溃。 */
val LocalWindowWidthClass = compositionLocalOf { WidthClass.Compact }

val LocalIsLargeScreen = compositionLocalOf { false }

/** 阅读正文的可读宽度上限：中文单行 25–35 字最舒适，铺满宽屏会串行。 */
val ReadingMaxWidth = 720.dp

/** 表单 / 设置类页面：避免输入框在宽屏被拉成长条。 */
val FormMaxWidth = 640.dp

/** 练习页（左文右答）的内容上限。 */
val PracticeMaxWidth = 1000.dp

/** 统计 / 多卡片网格的内容上限。 */
val GridMaxWidth = 1100.dp

/** 大屏左侧悬浮导航栏宽度，与 Android 的 NavRailWidth 一致。 */
val NavRailWidth = 76.dp

/** 左文右答所需的最小内容宽度（已扣掉导航栏）与高度。 */
const val TwoPaneMinWidthDp = 720
const val TwoPaneMinHeightDp = 480

/**
 * 首页画布的渲染列数。
 *
 * 卡片存储的 `colSpan` 是绝对列数（1–2，满宽 = 2），渲染列数只改变画布粒度：
 * 列数越多，卡片越窄、一行排得越多。按单卡最小舒适宽 294dp 折算并取偶，
 * 保证半宽卡（1 列）与满宽卡（2 列）都能精确落位。区间 2–6 与 Android 一致。
 */
fun homeGridColumns(availableWidthDp: Float, cardMinWidthDp: Float = 294f): Int {
    if (availableWidthDp <= 0f) return 2
    val even = (availableWidthDp / cardMinWidthDp / 2f).roundToInt() * 2
    return even.coerceIn(2, 6)
}

/** 普通卡片网格列数（文章 / 标签等）。 */
fun gridColumnsFor(availableWidthDp: Float, cardMinWidthDp: Float = 168f): Int {
    if (availableWidthDp <= 0f) return 1
    return (availableWidthDp / cardMinWidthDp).toInt().coerceIn(1, 6)
}

/** 练习页是否切「左文右答」：宽度与高度必须同时达标，手机横屏常常只够其一。 */
fun twoPaneFits(contentWidthDp: Float, heightDp: Float): Boolean =
    contentWidthDp >= TwoPaneMinWidthDp && heightDp >= TwoPaneMinHeightDp

package com.ilyskyo.blancall.algorithm

/**
 * 32-bit ARGB 颜色值（桌面端替代 Compose `Color`，避免 shared 依赖 UI）。
 *
 * [argb] 采用 `0xAARRGGBB` 形式的 Long；[Unspecified] 表示「无颜色/空心」，
 * 渲染层据此画空心框。
 */
@JvmInline
value class HeatColor(val argb: Long) {
    companion object {
        val Unspecified = HeatColor(-1L)
    }
}

// Copyright (c) 2026 ilyskyo
// SPDX-License-Identifier: MIT

package com.ilyskyo.blancall.handwriting

/**
 * 连写拆分：把一行手写按**列投影空白缝**切成单字，供逐字识别。
 *
 * ## 为什么用列投影而不是连通域
 * 汉字由多个互不相连的部件组成（「你」= 亻+尔，「川」= 三竖），连通域会把一个字拆成三四块；
 * 而**字与字之间的空白**通常明显宽于字内部件间的缝隙。列投影找「连续无墨的列」，
 * 只有缝宽达到阈值才切开，更贴近真实书写。
 *
 * 阈值按画布高度取比例（默认 6%）：360px 画布约 21px，能跨过部件缝隙、又不会把正常的
 * 字间距粘在一起。小于 [MIN_SEGMENT_WIDTH] 的碎片（标点、噪声）并入相邻字。
 *
 * 纯函数、无 Android 依赖，可单测。
 */
object HandwritingSegmenter {

    /** 单个拆分结果（左闭右开） */
    data class Segment(val left: Int, val right: Int)

    /** 前景判定阈值：与 [HandwritingPreprocess] 的 FG_THRESHOLD 一致（0 = 黑，255 = 白）。 */
    private const val FG_THRESHOLD = 220

    /** 最小字宽（占画布高度比例）：再窄就当噪声并入相邻字。 */
    private const val MIN_WIDTH_RATIO = 0.06f

    /** 切分缝宽阈值（占画布高度比例）。 */
    private const val GAP_RATIO = 0.06f

    /**
     * @param gray 白底黑字灰度图，行优先，长度 = width*height
     * @return 从左到右的切分区间；没有前景时返回空列表
     */
    fun split(gray: ByteArray, width: Int, height: Int): List<Segment> {
        if (width <= 0 || height <= 0 || gray.size < width * height) return emptyList()
        val columnInk = IntArray(width)
        var any = false
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((gray[row + x].toInt() and 0xFF) < FG_THRESHOLD) {
                    columnInk[x]++
                    any = true
                }
            }
        }
        if (!any) return emptyList()

        val gapThreshold = (height * GAP_RATIO).toInt().coerceAtLeast(2)
        val minWidth = (height * MIN_WIDTH_RATIO).toInt().coerceAtLeast(1)
        val spans = ArrayList<Segment>()
        var start = -1
        var gap = 0
        for (x in 0 until width) {
            if (columnInk[x] > 0) {
                if (start < 0) start = x
                gap = 0
            } else if (start >= 0) {
                gap++
                if (gap >= gapThreshold) {
                    val end = x - gap + 1
                    if (end - start >= minWidth) spans += Segment(start, end)
                    start = -1
                    gap = 0
                }
            }
        }
        if (start >= 0 && width - start >= minWidth) spans += Segment(start, width)

        // 过窄的碎片并入左侧（没有左侧则并入右侧），避免标点/噪声单独成字
        if (spans.size >= 2) {
            val merged = ArrayList<Segment>(spans.size)
            var pending: Segment? = null
            for (segment in spans) {
                if (segment.right - segment.left < minWidth) {
                    if (pending != null) {
                        pending = Segment(pending.left, segment.right)
                    } else if (merged.isNotEmpty()) {
                        val last = merged.removeAt(merged.lastIndex)
                        merged += Segment(last.left, segment.right)
                    } else {
                        pending = segment
                    }
                } else {
                    if (pending != null) {
                        merged += Segment(pending.left, segment.right)
                        pending = null
                    } else {
                        merged += segment
                    }
                }
            }
            pending?.let { p ->
                if (merged.isNotEmpty()) {
                    val last = merged.removeAt(merged.lastIndex)
                    merged += Segment(last.left, p.right)
                } else {
                    merged += p
                }
            }
            return merged
        }
        return spans
    }
}

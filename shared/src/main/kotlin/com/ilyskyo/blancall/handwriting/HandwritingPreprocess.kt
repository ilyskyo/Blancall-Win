// Copyright (c) 2026 ilyskyo
// SPDX-License-Identifier: MIT

package com.ilyskyo.blancall.handwriting

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 手写识别预处理（从 Android `preprocess.c` 逐行移植，口径必须一致）。
 *
 * - 中文：bbox → 长边 56 → 居中贴到 64×64 白底 → 反相并归一化到 [0,1]；
 * - 拉丁：bbox → 长边 20 → 居中贴到 28×28 黑底（反相）→ 归一化到 [-1,1]。
 *
 * 输入灰度图是「白底黑字」，像素 < [FG_THRESHOLD] 视为笔画。
 * 任何改动都要同步核对上游 Ismantic/Handwritten 的 Python/C 实现。
 */
object HandwritingPreprocess {

    const val CANVAS_SIZE = 64
    const val CONTENT_SIZE = 56
    const val LATIN_CANVAS_SIZE = 28
    const val LATIN_CONTENT_SIZE = 20
    const val FG_THRESHOLD = 220

    /** 中文：返回 64×64 归一化 float；无前景返回 null。 */
    fun chinese(gray: ByteArray, w: Int, h: Int): FloatArray? {
        if (w <= 0 || h <= 0 || gray.size < w * h) return null
        val box = boundingBox(gray, w, h) ?: return null
        val cropped = crop(gray, w, box)
        val maxDim = max(box.width, box.height)
        val scale = CONTENT_SIZE.toFloat() / maxDim.toFloat()
        val newW = max(1, iround(box.width * scale))
        val newH = max(1, iround(box.height * scale))
        val resized = resize(cropped, box.width, box.height, newW, newH)

        val canvas = ByteArray(CANVAS_SIZE * CANVAS_SIZE) { 255.toByte() }
        val offX = (CANVAS_SIZE - newW) / 2
        val offY = (CANVAS_SIZE - newH) / 2
        for (y in 0 until newH) {
            System.arraycopy(
                resized,
                y * newW,
                canvas,
                (offY + y) * CANVAS_SIZE + offX,
                newW,
            )
        }
        return FloatArray(CANVAS_SIZE * CANVAS_SIZE) { i ->
            (255 - (canvas[i].toInt() and 0xFF)) / 255f
        }
    }

    /** 拉丁（EMNIST）：返回 28×28 归一化 float；无前景返回 null。 */
    fun latin(gray: ByteArray, w: Int, h: Int): FloatArray? {
        if (w <= 0 || h <= 0 || gray.size < w * h) return null
        val box = boundingBox(gray, w, h) ?: return null
        val cropped = crop(gray, w, box)
        val maxDim = max(box.width, box.height)
        val scale = LATIN_CONTENT_SIZE.toFloat() / maxDim.toFloat()
        val newW = max(1, iround(box.width * scale))
        val newH = max(1, iround(box.height * scale))
        val resized = resize(cropped, box.width, box.height, newW, newH)

        val canvas = ByteArray(LATIN_CANVAS_SIZE * LATIN_CANVAS_SIZE)
        val offX = (LATIN_CANVAS_SIZE - newW) / 2
        val offY = (LATIN_CANVAS_SIZE - newH) / 2
        for (y in 0 until newH) {
            for (x in 0 until newW) {
                canvas[(offY + y) * LATIN_CANVAS_SIZE + offX + x] =
                    (255 - (resized[y * newW + x].toInt() and 0xFF)).toByte()
            }
        }
        return FloatArray(LATIN_CANVAS_SIZE * LATIN_CANVAS_SIZE) { i ->
            ((canvas[i].toInt() and 0xFF) / 255f - 0.5f) / 0.5f
        }
    }

    private data class Box(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
    }

    private fun boundingBox(gray: ByteArray, w: Int, h: Int): Box? {
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val v = gray[row + x].toInt() and 0xFF
                if (v < FG_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else Box(minX, minY, maxX, maxY)
    }

    private fun crop(gray: ByteArray, w: Int, box: Box): ByteArray {
        val out = ByteArray(box.width * box.height)
        for (y in 0 until box.height) {
            System.arraycopy(
                gray,
                (box.minY + y) * w + box.minX,
                out,
                y * box.width,
                box.width,
            )
        }
        return out
    }

    /** 分离式双线性缩放（等价 PIL.Image.BILINEAR，见 C 版 resample_pass_）。 */
    fun resize(src: ByteArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): ByteArray {
        if (srcW == dstW && srcH == dstH) return src.copyOf()
        val tmp = ByteArray(dstW * srcH)
        resamplePass(src, srcW, srcH, tmp, dstW, srcH, horizontal = true)
        val out = ByteArray(dstW * dstH)
        resamplePass(tmp, dstW, srcH, out, dstW, dstH, horizontal = false)
        return out
    }

    private fun resamplePass(
        src: ByteArray,
        srcW: Int,
        srcH: Int,
        dst: ByteArray,
        dstW: Int,
        dstH: Int,
        horizontal: Boolean,
    ) {
        val outDim = if (horizontal) dstW else dstH
        val inDim = if (horizontal) srcW else srcH
        val scale = inDim.toFloat() / outDim.toFloat()
        val filterScale = if (scale > 1f) scale else 1f
        val invFilterScale = 1f / filterScale
        val maxK = (2f * filterScale + 2f).toInt()

        val xmin = IntArray(outDim)
        val xmax = IntArray(outDim)
        val weights = Array(outDim) { FloatArray(maxK) }
        for (xx in 0 until outDim) {
            val center = (xx + 0.5f) * scale
            var xm = max(0, (center - filterScale + 0.5f).toInt())
            var xM = min(inDim, (center + filterScale + 0.5f).toInt())
            if (xM <= xm) xM = min(inDim, xm + 1)
            val len = xM - xm
            val w = weights[xx]
            var sum = 0f
            for (x in 0 until len) {
                val d = ((xm + x) + 0.5f - center) * invFilterScale
                val wv = max(0f, 1f - abs(d))
                w[x] = wv
                sum += wv
            }
            if (sum > 0f) for (x in 0 until len) w[x] /= sum
            xmin[xx] = xm
            xmax[xx] = xM
        }

        for (y in 0 until dstH) {
            for (xx in 0 until dstW) {
                val outIdx = if (horizontal) xx else y
                val srcRow = if (horizontal) y else xx
                val xm = xmin[outIdx]
                val xM = xmax[outIdx]
                val w = weights[outIdx]
                var sum = 0f
                for (x in 0 until (xM - xm)) {
                    val v = if (horizontal) {
                        src[srcRow * srcW + xm + x].toInt() and 0xFF
                    } else {
                        src[(xm + x) * srcW + srcRow].toInt() and 0xFF
                    }
                    sum += v.toFloat() * w[x]
                }
                val value = iround(sum).coerceIn(0, 255)
                dst[y * dstW + xx] = value.toByte()
            }
        }
    }

    private fun iround(x: Float): Int = (x + if (x >= 0f) 0.5f else -0.5f).toInt()
}

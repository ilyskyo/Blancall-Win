// Copyright (c) 2026 ilyskyo
// SPDX-License-Identifier: MIT

package com.ilyskyo.blancall.handwriting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HandwritingSegmenterTest {

    private fun blank(width: Int, height: Int) = ByteArray(width * height) { 255.toByte() }

    private fun fill(gray: ByteArray, width: Int, left: Int, right: Int, top: Int, bottom: Int) {
        for (y in top until bottom) {
            for (x in left until right) gray[y * width + x] = 0
        }
    }

    @Test
    fun `空白图没有切分`() {
        assertTrue(HandwritingSegmenter.split(blank(100, 100), 100, 100).isEmpty())
    }

    @Test
    fun `两个分离的字切成两段`() {
        val w = 120
        val h = 100
        val gray = blank(w, h)
        fill(gray, w, left = 10, right = 45, top = 20, bottom = 80)
        fill(gray, w, left = 75, right = 110, top = 20, bottom = 80)
        val spans = HandwritingSegmenter.split(gray, w, h)
        assertEquals(2, spans.size)
        assertTrue(spans[0].left <= 10 && spans[0].right >= 45)
        assertTrue(spans[1].left <= 75 && spans[1].right >= 110)
    }

    @Test
    fun `部件间窄缝不会被切开`() {
        val w = 100
        val h = 100
        val gray = blank(w, h)
        // 同一字的左右部件，中间只留 2px 缝（小于 6% 阈值）
        fill(gray, w, left = 20, right = 48, top = 20, bottom = 80)
        fill(gray, w, left = 50, right = 80, top = 20, bottom = 80)
        assertEquals(1, HandwritingSegmenter.split(gray, w, h).size)
    }

    @Test
    fun `过窄碎片并入相邻字`() {
        val w = 120
        val h = 100
        val gray = blank(w, h)
        fill(gray, w, left = 10, right = 50, top = 20, bottom = 80)
        fill(gray, w, left = 55, right = 59, top = 40, bottom = 60) // 4px 宽碎片
        fill(gray, w, left = 70, right = 110, top = 20, bottom = 80)
        val spans = HandwritingSegmenter.split(gray, w, h)
        assertEquals(2, spans.size)
        assertTrue(spans[0].right >= 59, "碎片应并入相邻字")
    }

    @Test
    fun `尺寸非法时返回空`() {
        assertTrue(HandwritingSegmenter.split(ByteArray(0), 0, 0).isEmpty())
        assertTrue(HandwritingSegmenter.split(ByteArray(10), -1, 5).isEmpty())
    }
}

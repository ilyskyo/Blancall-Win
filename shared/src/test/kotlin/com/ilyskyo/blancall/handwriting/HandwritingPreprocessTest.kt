package com.ilyskyo.blancall.handwriting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HandwritingPreprocessTest {

    private fun blank(w: Int, h: Int) = ByteArray(w * h) { 255.toByte() }

    @Test
    fun `空白图没有前景`() {
        assertNull(HandwritingPreprocess.chinese(blank(100, 100), 100, 100))
        assertNull(HandwritingPreprocess.latin(blank(100, 100), 100, 100))
    }

    @Test
    fun `中文输出 64x64 且笔画为高值`() {
        val w = 100
        val h = 100
        val gray = blank(w, h)
        for (y in 40..60) for (x in 40..60) gray[y * w + x] = 0
        val out = HandwritingPreprocess.chinese(gray, w, h)
        assertNotNull(out)
        assertEquals(64 * 64, out!!.size)
        assertTrue(out.max() > 0.9f)
        assertTrue(out.min() < 0.1f)
    }

    @Test
    fun `拉丁输出 28x28 且背景为负一`() {
        val w = 60
        val h = 60
        val gray = blank(w, h)
        for (y in 20..40) for (x in 20..40) gray[y * w + x] = 0
        val out = HandwritingPreprocess.latin(gray, w, h)
        assertNotNull(out)
        assertEquals(28 * 28, out!!.size)
        assertTrue(out.max() > 0.5f, "笔画应为白字（正数）")
        assertTrue(out.min() >= -1f)
        assertEquals(-1f, out[0], 1e-6f)
    }

    @Test
    fun `缩放保持常数图`() {
        val src = ByteArray(10 * 10) { 128.toByte() }
        val out = HandwritingPreprocess.resize(src, 10, 10, 5, 7)
        assertEquals(5 * 7, out.size)
        assertTrue(out.all { (it.toInt() and 0xFF) == 128 })
    }

    @Test
    fun `bbox 外的空白不影响结果`() {
        val w = 50
        val h = 50
        val gray = blank(w, h)
        for (y in 10..20) for (x in 10..20) gray[y * w + x] = 0
        val moved = blank(w, h)
        for (y in 30..40) for (x in 30..40) moved[y * w + x] = 0
        val a = HandwritingPreprocess.chinese(gray, w, h)!!
        val b = HandwritingPreprocess.chinese(moved, w, h)!!
        assertTrue(a.contentEquals(b), "同一笔画在不同位置应得到同一张归一化输入")
    }
}

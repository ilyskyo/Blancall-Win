package com.ilyskyo.blancall.algorithm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderOcclusionTest {

    @Test
    fun `空行分段记录原文偏移`() {
        val text = "第一段第一句。\n\n第二段。"
        val paras = ReaderOcclusion.splitParagraphs(text)
        assertEquals(2, paras.size)
        assertEquals("第一段第一句。", paras[0].text)
        assertEquals(0, paras[0].start)
        assertEquals("第二段。", paras[1].text)
        assertEquals(text.indexOf("第二段"), paras[1].start)
    }

    @Test
    fun `长遮挡覆盖整个分句并含句末标点`() {
        val text = "君子曰：学不可以已。青，取之于蓝。"
        val ranges = ReaderOcclusion.localRanges(text, "long")
        assertTrue(ranges.isNotEmpty())
        val covered = ranges.joinToString("") { text.substring(it.start, it.end) }
        assertTrue(covered.contains("学不可以已。"), "实际遮挡：$covered")
        assertTrue(ranges.any { text.substring(it.start, it.end) == "青，" || text.substring(it.start, it.end) == "取之于蓝。" })
    }

    @Test
    fun `短遮挡只盖字词而不是整句`() {
        val text = "锲而不舍，金石可镂。"
        val ranges = ReaderOcclusion.localRanges(text, "short")
        assertTrue(ranges.isNotEmpty())
        assertTrue(
            ranges.all { it.end - it.start <= 3 },
            "短遮挡的每块应不超过 3 字：$ranges",
        )
    }

    @Test
    fun `英文长词也会被短遮挡命中`() {
        val text = "The quick brown fox jumps over the lazy dog."
        val ranges = ReaderOcclusion.localRanges(text, "short")
        assertTrue(ranges.isNotEmpty(), "纯英文段落也应产生遮挡")
        assertTrue(
            ranges.all { range -> range.end - range.start >= 3 },
            "英文只遮长度 >= 3 的词",
        )
    }

    @Test
    fun `编辑单元按两字或单字切分`() {
        val para = "学而时习之"
        val two = ReaderOcclusion.editUnits(para, 1)
        assertEquals(listOf(0 to 2, 2 to 4, 4 to 5), two.map { it.start to it.end })

        val single = ReaderOcclusion.editUnits(para, 2)
        assertEquals(5, single.size)
        assertTrue(single.all { it.end - it.start == 1 })
    }

    @Test
    fun `混合模式区间稳定`() {
        val text = "学而时习之，不亦说乎？有朋自远方来，不亦乐乎？"
        val a = ReaderOcclusion.localRanges(text, "mixed").map { it.start to it.end }
        val b = ReaderOcclusion.localRanges(text, "mixed").map { it.start to it.end }
        assertEquals(a, b, "同一文本的混合遮挡必须稳定，不能在重组间跳动")
    }
}

package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.algorithm.ReaderOcclusion
import com.ilyskyo.blancall.algorithm.SentenceSplitter
import com.ilyskyo.blancall.desktop.ui.collectMaskSpecs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 阅读遮挡 → 自定义挖空坐标：
 * 句子索引与练习侧同口径，句内区间必须在边界内；重叠挡片合并后不能产生重复空。
 */
class MaskToClozeTest {

    @Test
    fun `本地遮挡转成合法句子坐标`() {
        val content = "学而时习之，不亦说乎？有朋自远方来，不亦乐乎？"
        val paragraphs = ReaderOcclusion.splitParagraphs(content)
        val specs = collectMaskSpecs(content, paragraphs, "long", emptyMap())
        assertTrue(specs.isNotEmpty(), "长遮挡应至少产生一个空")
        val sentences = SentenceSplitter.splitWithPositions(content)
        specs.forEach { spec ->
            val sentence = sentences.getOrNull(spec.s)
            assertTrue(sentence != null, "句子索引越界：${spec.s}")
            assertTrue(spec.a >= 0, "起点为负：${spec.a}")
            assertTrue(spec.b > spec.a, "空区间：[$spec.a, ${spec.b})")
            assertTrue(
                spec.b <= sentence.endIndex - sentence.startIndex,
                "终点越过句子边界：${spec.b}",
            )
        }
    }

    @Test
    fun `重叠与相邻挡片合并为一个空`() {
        val content = "天地玄黄，宇宙洪荒。"
        val paragraphs = ReaderOcclusion.splitParagraphs(content)
        val spans = mapOf(
            0 to listOf(
                ReaderOcclusion.Span(0, 3),
                ReaderOcclusion.Span(1, 4),
                ReaderOcclusion.Span(4, 6),
            ),
        )
        val specs = collectMaskSpecs(content, paragraphs, "custom", spans)
        assertTrue(specs.isNotEmpty(), "自定义挡片应产生空")
        val first = specs.first()
        assertTrue(first.a == 0 && first.b == 6, "重叠/相邻区间应合并为 [0,6)，实际 [$first.a,${first.b})")
    }

    @Test
    fun `跨句挡片按句裁剪`() {
        val content = "第一句。第二句。"
        val paragraphs = ReaderOcclusion.splitParagraphs(content)
        val sentences = SentenceSplitter.splitWithPositions(content)
        val secondStart = sentences[1].startIndex
        val spans = mapOf(0 to listOf(ReaderOcclusion.Span(2, secondStart + 2)))
        val specs = collectMaskSpecs(content, paragraphs, "custom", spans)
        assertTrue(specs.size >= 2, "跨句挡片应拆到两句话里")
        assertTrue(specs.map { it.s }.distinct().size >= 2, "应覆盖多个句子索引")
    }
}

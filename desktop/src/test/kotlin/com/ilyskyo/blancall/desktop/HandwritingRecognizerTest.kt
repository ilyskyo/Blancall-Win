package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.desktop.handwriting.HandwritingRecognizer
import com.ilyskyo.blancall.desktop.handwriting.HandwritingScript
import com.ilyskyo.blancall.handwriting.HandwritingSegmenter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File

/**
 * 手写识别端到端：用系统字体渲染一个汉字，走与 Android 相同的预处理 + NCNN 推理。
 *
 * 模型/`ncnn.dll` 不存在时跳过（源码包不带模型也能通过单测）。
 */
class HandwritingRecognizerTest {

    private fun modelsDir(): File? {
        val candidates = listOf(
            File("../models"),
            File("models"),
            File(System.getProperty("user.dir"), "../models"),
        )
        return candidates.map { it.canonicalFile }.firstOrNull {
            File(it, "hccr/model.ncnn.param").isFile && File(it, "ncnn.dll").isFile
        }
    }

    private fun renderGlyph(ch: Char): ByteArray = renderLine(ch.toString(), 400, 400, 300)

    /** 把一行文字居中画到指定画布，返回灰度图（白底黑字）。 */
    private fun renderLine(text: String, width: Int, height: Int, fontSize: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.color = Color.BLACK
            g.font = Font("Microsoft YaHei", Font.PLAIN, fontSize)
            val fm = g.fontMetrics
            val x = (width - fm.stringWidth(text)) / 2
            val y = (height - fm.height) / 2 + fm.ascent
            g.drawString(text, x, y)
        } finally {
            g.dispose()
        }
        return toGray(img)
    }

    private fun toGray(img: BufferedImage): ByteArray {
        val width = img.width
        val height = img.height
        val gray = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val gg = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                gray[y * width + x] = ((r * 299 + gg * 587 + b * 114) / 1000).toByte()
            }
        }
        return gray
    }

    private fun recognizerOrSkip(): HandwritingRecognizer? {
        val dir = modelsDir() ?: return null
        System.setProperty("blancall.models.dir", dir.absolutePath)
        val recognizer = HandwritingRecognizer()
        return recognizer.takeIf { it.available && it.hasChineseModel }
    }

    @Test
    fun `渲染的永字能被识别进前五`() {
        val recognizer = recognizerOrSkip()
        assumeTrue(recognizer != null, "models/ 不存在或 NCNN 不可用")
        val gray = renderGlyph('永')
        val candidates = recognizer!!.recognize(gray, 400, 400, HandwritingScript.Chinese, topK = 5)
        println("top5=" + candidates.joinToString { "${it.char}:${it.confidence}" })
        assertTrue(candidates.isNotEmpty(), "识别结果不能为空")
        assertTrue(candidates.any { it.char == '永' }, "永字应出现在前五：$candidates")
        assertEquals('永', candidates.first().char, "永字应为 top-1：$candidates")
    }

    @Test
    fun `常见汉字渲染后能进入前三`() {
        val recognizer = recognizerOrSkip()
        assumeTrue(recognizer != null, "models/ 不存在或 NCNN 不可用")
        for (ch in listOf('你', '好', '学')) {
            val gray = renderGlyph(ch)
            val top3 = recognizer!!.recognize(gray, 400, 400, HandwritingScript.Chinese, topK = 3)
            println("$ch top3=" + top3.joinToString { "${it.char}:${it.confidence}" })
            assertTrue(top3.any { it.char == ch }, "$ch 应出现在前三：$top3")
        }
    }

    @Test
    fun `英文数字模型能识别 A 与 7`() {
        val recognizer = recognizerOrSkip()
        assumeTrue(recognizer != null, "models/ 不存在或 NCNN 不可用")
        for (ch in listOf('A', '7')) {
            val gray = renderGlyph(ch)
            val top5 = recognizer!!.recognize(gray, 400, 400, HandwritingScript.Latin, topK = 5)
            println("$ch latin top5=" + top5.joinToString { "${it.char}:${it.confidence}" })
            assertTrue(top5.any { it.char == ch }, "$ch 应出现在拉丁模型前五：$top5")
        }
    }

    @Test
    fun `连写拆分后逐字识别能得到两个字`() {
        val recognizer = recognizerOrSkip()
        assumeTrue(recognizer != null, "models/ 不存在或 NCNN 不可用")
        val width = 800
        val height = 400
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.color = Color.BLACK
            g.font = Font("Microsoft YaHei", Font.PLAIN, 280)
            val fm = g.fontMetrics
            // 两字之间留 60px 空白，模拟正常字距
            g.drawString("你", 160, (height - fm.height) / 2 + fm.ascent)
            g.drawString("好", 160 + fm.stringWidth("你") + 60, (height - fm.height) / 2 + fm.ascent)
        } finally {
            g.dispose()
        }
        val gray = toGray(img)
        val segments = HandwritingSegmenter.split(gray, width, height)
        println("segments=" + segments.joinToString { "${it.left}-${it.right}" })
        assertEquals(2, segments.size, "两个字应被拆成两段：$segments")
        val text = buildString {
            for (segment in segments) {
                val croppedWidth = segment.right - segment.left
                val cropped = ByteArray(croppedWidth * height)
                for (y in 0 until height) {
                    System.arraycopy(gray, y * width + segment.left, cropped, y * croppedWidth, croppedWidth)
                }
                recognizer!!.recognize(cropped, croppedWidth, height, HandwritingScript.Chinese, topK = 1)
                    .firstOrNull()?.let { append(it.char) }
            }
        }
        println("连写识别=$text")
        assertEquals("你好", text, "连写拆分 + 识别应得到「你好」")
    }
}

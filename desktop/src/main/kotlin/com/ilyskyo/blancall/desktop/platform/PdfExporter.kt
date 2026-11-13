package com.ilyskyo.blancall.desktop.platform

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.File

/**
 * PDF 试卷导出：PDFBox 3 + 嵌入 OFL 字体（霞鹜文楷 Regular）。
 * 不依赖系统字体，保证在别的机器上打开不缺字。
 */
object PdfExporter {

    data class Request(
        val title: String,
        val author: String = "",
        val body: String,
        /** 答案列表；空表示不生成答案页 */
        val answers: List<String> = emptyList(),
    )

    fun export(request: Request, target: File) {
        val fontStream = PdfExporter::class.java.getResourceAsStream("/fonts/xwwk_regular.ttf")
            ?: throw IllegalStateException("缺少内置字体 fonts/xwwk_regular.ttf")
        PDDocument().use { doc ->
            val font = fontStream.use { PDType0Font.load(doc, it, true) }
            var page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            var stream = PDPageContentStream(doc, page)
            var y = HEIGHT - MARGIN

            fun closeStream() {
                stream.close()
            }

            fun newPage() {
                closeStream()
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                stream = PDPageContentStream(doc, page)
                y = HEIGHT - MARGIN
            }

            fun ensureLine() {
                if (y < MARGIN + LEADING) newPage()
            }

            fun drawLine(text: String, size: Float, bold: Boolean = false) {
                ensureLine()
                stream.beginText()
                stream.setFont(font, size)
                stream.newLineAtOffset(MARGIN, y)
                stream.showText(sanitize(text))
                stream.endText()
                y -= if (bold) size + 8 else LEADING
            }

            drawLine(request.title.ifBlank { "未命名" }, 20f, bold = true)
            if (request.author.isNotBlank()) drawLine(request.author, 11f)
            y -= 8f

            val lines = wrap(request.body, font, BODY_SIZE, CONTENT_WIDTH)
            lines.forEach { line ->
                if (line.isBlank()) {
                    y -= LEADING * 0.6f
                } else {
                    drawLine(line, BODY_SIZE)
                }
            }

            if (request.answers.isNotEmpty()) {
                newPage()
                drawLine("答案", 16f, bold = true)
                request.answers.forEachIndexed { index, answer ->
                    drawLine("${index + 1}. $answer", BODY_SIZE)
                }
            }
            closeStream()
            doc.save(target)
        }
    }

    private fun wrap(text: String, font: PDType0Font, size: Float, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) {
                out.add("")
                return@forEach
            }
            val line = StringBuilder()
            for (ch in paragraph) {
                val candidate = line.toString() + ch
                if (line.isNotEmpty() && width(candidate, font, size) > maxWidth) {
                    out.add(line.toString())
                    line.setLength(0)
                }
                line.append(ch)
            }
            if (line.isNotEmpty()) out.add(line.toString())
        }
        return out
    }

    private fun width(text: String, font: PDType0Font, size: Float): Float {
        return runCatching { font.getStringWidth(text) / 1000f * size }.getOrDefault(0f)
    }

    private fun sanitize(text: String): String =
        text.replace('\n', ' ').replace('\r', ' ')

    private const val WIDTH = 595f
    private const val HEIGHT = 842f
    private const val MARGIN = 56f
    private const val BODY_SIZE = 12f
    private const val LEADING = 20f
    private const val CONTENT_WIDTH = WIDTH - MARGIN * 2
}

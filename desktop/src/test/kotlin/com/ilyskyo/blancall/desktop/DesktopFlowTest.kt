package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.data.model.AnsweredBlank
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.DesktopPrefs
import com.ilyskyo.blancall.desktop.platform.PdfExporter
import com.ilyskyo.blancall.desktop.platform.PdfPreview
import com.ilyskyo.blancall.desktop.platform.TextImporter
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopFlowTest {

    @Test
    fun `DOC 旧格式能抽取正文`() {
        val url = requireNotNull(javaClass.getResource("/fixtures/sample.doc")) {
            "缺少 test resources/fixtures/sample.doc"
        }
        val file = File(url.toURI())
        val text = TextImporter.read(file)
        println("doc text=[$text]")
        assertTrue(text.contains("Word 97"), "DOC 应能抽出正文：$text")
    }

    @Test
    fun `EPUB 按 spine 顺序抽取正文`() {
        val dir = Files.createTempDirectory("blancall-epub").toFile()
        val epub = File(dir, "book.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>""",
            )
            put(
                "OEBPS/content.opf",
                """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>""",
            )
            put("OEBPS/c1.xhtml", "<html><body><h1>第一章</h1><p>第一段正文</p></body></html>")
            put("OEBPS/text/c2.xhtml", "<html><body><p>Second &amp; last</p></body></html>")
        }
        val text = TextImporter.read(epub)
        assertTrue(text.contains("第一段正文"), "应包含第一章正文：$text")
        assertTrue(text.contains("Second & last"), "应解码 HTML 实体：$text")
        assertTrue(text.indexOf("第一段正文") < text.indexOf("Second & last"), "章节顺序应按 spine")
    }

    @Test
    fun `首页布局顺序与隐藏项会持久化`() {
        val dir = Files.createTempDirectory("blancall-home-layout").toFile()
        val prefsFile = File(dir, "settings/ui_prefs.properties")
        val prefs = DesktopPrefs(prefsFile)
        assertEquals(DesktopPrefs.DEFAULT_HOME_ORDER, prefs.homeSectionOrder)

        prefs.updateHomeLayout(listOf("articles", "due", "plan"), setOf("recent"))
        val reloaded = DesktopPrefs(prefsFile)
        // 未知 key 被丢弃，缺失的卡片按默认顺序补到末尾
        assertEquals(listOf("articles", "due", "plan", "daily", "recent"), reloaded.homeSectionOrder)
        assertEquals(setOf("recent"), reloaded.homeSectionHidden)

        reloaded.resetHomeLayout()
        assertEquals(DesktopPrefs.DEFAULT_HOME_ORDER, DesktopPrefs(prefsFile).homeSectionOrder)
    }

    @Test
    fun `练习保存会写记录并推进 FSRS`() = runBlocking {
        val dir = Files.createTempDirectory("blancall-flow").toFile()
        val state = AppState(dir)
        state.articles.awaitLoaded()
        val id = state.articles.insert(
            Article(
                title = "测试文章",
                content = "君子曰：学不可以已。青，取之于蓝，而青于蓝。冰，水为之，而寒于水。",
            )
        )
        val rating = state.savePractice(
            articleId = id,
            mode = "SENTENCE",
            totalBlanks = 1,
            correctCount = 1,
            similarity = 1f,
            mistakes = emptyList(),
            answeredBlanks = emptyList(),
        )
        val record = state.records.records.value.firstOrNull { it.articleId == id }
        assertNotNull(record, "练习记录必须落库")
        assertEquals(1, record!!.correctCount)
        val card = state.fsrs.allStates()[id]
        assertNotNull(card, "FSRS 状态必须建立")
        assertTrue(card!!.reviewCount >= 1)
        assertEquals(rating.value, record.rating)
    }

    @Test
    fun `练习会建立句子级 FSRS 状态`() = runBlocking {
        val dir = Files.createTempDirectory("blancall-sentence-fsrs").toFile()
        val state = AppState(dir)
        state.articles.awaitLoaded()
        val sentence = "青，取之于蓝，而青于蓝。"
        val id = state.articles.insert(Article(title = "句子状态", content = sentence))
        state.savePractice(
            articleId = id,
            mode = "SENTENCE",
            totalBlanks = 1,
            correctCount = 1,
            similarity = 1f,
            mistakes = emptyList(),
            answeredBlanks = listOf(
                AnsweredBlank(
                    blankIndex = 0,
                    correctAnswer = "青于蓝",
                    userAnswer = "青于蓝",
                    errorType = "",
                    sentenceIndex = 0,
                    sentenceText = sentence,
                    similarity = 1f,
                    articleId = id,
                )
            ),
        )
        assertTrue(
            state.fsrs.allSentenceStates().isNotEmpty(),
            "必须建立 s:<articleId>:<hash16> 句子状态",
        )
    }

    @Test
    fun `跨文练习记录 articleId 为 -1 并逐篇推进 FSRS`() = runBlocking {
        val dir = Files.createTempDirectory("blancall-cross").toFile()
        val state = AppState(dir, settingsDir = File(dir, "settings"))
        state.articles.awaitLoaded()
        val first = state.articles.insert(Article(title = "甲", content = "甲句。"))
        val second = state.articles.insert(Article(title = "乙", content = "乙句。"))
        val answered = listOf(
            AnsweredBlank(
                blankIndex = 0,
                correctAnswer = "甲",
                userAnswer = "甲",
                sentenceIndex = 0,
                sentenceText = "甲句。",
                similarity = 1f,
                articleId = first,
            ),
            AnsweredBlank(
                blankIndex = 1,
                correctAnswer = "乙",
                userAnswer = "乙",
                sentenceIndex = 0,
                sentenceText = "乙句。",
                similarity = 1f,
                articleId = second,
            ),
        )
        state.saveCrossPractice(
            sourceArticleIds = listOf(first, second),
            totalBlanks = 2,
            correctCount = 2,
            similarity = 1f,
            mistakes = emptyList(),
            answeredBlanks = answered,
        )
        val record = state.records.records.value.lastOrNull {
            it.articleId == -1L && it.sourceArticleIds.contains(first)
        }
        assertNotNull(record, "跨文练习必须落一条 articleId=-1 的记录")
        assertEquals(listOf(first, second), record!!.sourceArticleIds)
        assertTrue(state.fsrs.allStates()[first]!!.reviewCount >= 1)
        assertTrue(state.fsrs.allStates()[second]!!.reviewCount >= 1)
    }

    @Test
    fun `复习模板切换会配置 FSRS 目标留存率`() {
        val original = com.ilyskyo.blancall.algorithm.FsrsEngine.targetRetention()
        val dir = Files.createTempDirectory("blancall-template").toFile()
        val state = AppState(dir, settingsDir = File(dir, "settings"))
        try {
            state.updateReviewTemplate("sprint")
            assertEquals(0.95, com.ilyskyo.blancall.algorithm.FsrsEngine.targetRetention(), 1e-9)
            state.updateReviewTemplate("deep")
            assertEquals(0.85, com.ilyskyo.blancall.algorithm.FsrsEngine.targetRetention(), 1e-9)
            state.updateReviewTemplate("standard")
            assertEquals(0.90, com.ilyskyo.blancall.algorithm.FsrsEngine.targetRetention(), 1e-9)
        } finally {
            com.ilyskyo.blancall.algorithm.FsrsEngine.configure(
                com.ilyskyo.blancall.algorithm.FsrsEngine.DEFAULT_PARAMS,
                original,
            )
        }
    }

    @Test
    fun `DOCX 只抽取正文段落`() {
        val dir = Files.createTempDirectory("blancall-docx").toFile()
        val docx = File(dir, "sample.docx")
        ZipOutputStream(docx.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>第一段</w:t></w:r></w:p>
                    <w:p><w:r><w:t>第二段</w:t></w:r><w:r><w:t>续写</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()
        }
        val text = TextImporter.read(docx)
        assertTrue(text.contains("第一段"))
        assertTrue(text.contains("第二段续写"))
    }

    @Test
    fun `PDF 文本可抽取`() {
        val dir = Files.createTempDirectory("blancall-pdf").toFile()
        val pdf = File(dir, "sample.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Hello PDF")
                cs.endText()
            }
            doc.save(pdf)
        }
        val text = TextImporter.read(pdf)
        assertTrue(text.contains("Hello PDF"), "实际文本：$text")
    }

    @Test
    fun `TXT 带 BOM 也能读取`() {
        val dir = Files.createTempDirectory("blancall-txt").toFile()
        val txt = File(dir, "sample.txt")
        txt.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "你好 Blancall".toByteArray())
        assertEquals("你好 Blancall", TextImporter.read(txt))
    }

    @Test
    fun `PDF 预览返回页面图与抽取文本`() {
        val dir = Files.createTempDirectory("blancall-pdf-preview").toFile()
        val pdf = File(dir, "preview.pdf")
        PDDocument().use { doc ->
            repeat(2) { index ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
                    cs.newLineAtOffset(50f, 700f)
                    cs.showText("Preview page ${index + 1}")
                    cs.endText()
                }
            }
            doc.save(pdf)
        }
        val preview = PdfPreview.load(pdf, maxPages = 3, targetWidth = 400)
        assertEquals(2, preview.totalPages)
        assertEquals(2, preview.pages.size)
        assertTrue(preview.text.contains("Preview page 1"))
        assertTrue(preview.pages.all { it.width > 0 && it.height > 0 })
    }

    @Test
    fun `PDF 导出包含中文与答案页`() {
        val dir = Files.createTempDirectory("blancall-export").toFile()
        val pdf = File(dir, "paper.pdf")
        PdfExporter.export(
            PdfExporter.Request(
                title = "劝学（节选）",
                author = "荀子",
                body = "君子曰：学不可以已。",
                answers = listOf("学不可以已"),
            ),
            pdf,
        )
        assertTrue(pdf.length() > 1000)
        val doc = Loader.loadPDF(pdf)
        doc.use {
            val text = PDFTextStripper().getText(it)
            assertTrue(text.contains("劝学"), "导出 PDF 必须包含中文标题")
            assertTrue(text.contains("答案"), "导出 PDF 必须包含答案页")
        }
    }
}

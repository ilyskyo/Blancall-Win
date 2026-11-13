package com.ilyskyo.blancall.desktop.platform

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
import java.io.File

/**
 * PDF 导入预览：用 PDFBox 渲染前几页缩略图，同时抽取正文。
 *
 * 与 Android 的「导入前预览」对齐：用户先确认版式与文本顺序，再决定是否导入。
 * 只渲染 [maxPages] 页，避免几百页 PDF 一次性占满内存。
 */
object PdfPreview {

    data class Result(
        val pages: List<BufferedImage>,
        val totalPages: Int,
        val text: String,
    )

    fun load(file: File, maxPages: Int = 3, targetWidth: Int = 640): Result {
        Loader.loadPDF(file).use { doc ->
            val text = PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            val total = doc.numberOfPages
            val renderer = PDFRenderer(doc)
            val pages = ArrayList<BufferedImage>(minOf(maxPages, total))
            for (index in 0 until minOf(maxPages, total)) {
                val page = doc.getPage(index)
                val box = page.cropBox
                val scale = (targetWidth / box.width).coerceIn(0.2f, 3f)
                pages += renderer.renderImage(index, scale)
            }
            return Result(pages, total, text.trim())
        }
    }
}

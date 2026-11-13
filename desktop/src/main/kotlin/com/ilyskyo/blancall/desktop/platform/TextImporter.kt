package com.ilyskyo.blancall.desktop.platform

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 文件导入：PDF / DOCX / TXT / Markdown / HTML / RTF。
 *
 * - PDF 用 Apache PDFBox 3（桌面版），与 Android 的 pdfbox-android 同源；
 * - DOCX 自研 zip + XML，和 Android 的 FileTextExtractor 保持同一套「只取正文」语义；
 * - `.doc`（97-2003）首版明确提示不支持，避免假装解析成功。
 */
object TextImporter {

    fun read(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> readPdf(file)
            "docx" -> readDocx(file)
            "doc" -> readDoc(file)
            "epub" -> readEpub(file)
            "html", "htm" -> stripHtml(readTextSmart(file))
            "rtf" -> stripRtf(readTextSmart(file))
            else -> readTextSmart(file)
        }.trim()
    }

    private fun readPdf(file: File): String {
        Loader.loadPDF(file).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            return stripper.getText(doc)
        }
    }

    private fun readDocx(file: File): String {
        val xmlBytes = ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: throw IllegalArgumentException("不是有效的 DOCX：缺少 word/document.xml")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val out = StringBuilder()
        val body = doc.getElementsByTagNameNS(W_NS, "body").item(0) ?: doc.documentElement
        appendDocxNode(body, out)
        return out.toString()
    }

    private fun appendDocxNode(node: Node, out: StringBuilder) {
        when (node.nodeName) {
            "w:p" -> {
                for (i in 0 until node.childNodes.length) appendDocxNode(node.childNodes.item(i), out)
                out.append('\n')
            }
            "w:t" -> out.append(node.textContent.orEmpty())
            "w:br", "w:cr" -> out.append('\n')
            "w:tab" -> out.append('\t')
            else -> {
                for (i in 0 until node.childNodes.length) appendDocxNode(node.childNodes.item(i), out)
            }
        }
    }

    /** 旧版 Word 97-2003（OLE 复合文档）：用 POI HWPF 抽取正文，避免自研解析漏文本。 */
    private fun readDoc(file: File): String {
        FileInputStream(file).use { input ->
            HWPFDocument(input).use { doc ->
                return WordExtractor(doc).text.trim()
            }
        }
    }

    /**
     * EPUB：按 `container.xml → OPF spine` 顺序抽取各章 XHTML 的正文。
     * 不解析样式与图片；路径做 `..` 归一化并拒绝逃出压缩包根目录的条目。
     */
    private fun readEpub(file: File): String {
        ZipFile(file).use { zip ->
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val containerBytes = zip.entryBytes("META-INF/container.xml")
                ?: throw IllegalArgumentException("不是有效的 EPUB：缺少 META-INF/container.xml")
            val container = factory.newDocumentBuilder().parse(ByteArrayInputStream(containerBytes))
            val rootfile = container.getElementsByTagNameNS("*", "rootfile").item(0)
                ?: throw IllegalArgumentException("不是有效的 EPUB：缺少 rootfile")
            val opfPath = rootfile.attributes.getNamedItem("full-path")?.nodeValue
                ?: throw IllegalArgumentException("不是有效的 EPUB：rootfile 缺少 full-path")
            val opfBytes = zip.entryBytes(opfPath)
                ?: throw IllegalArgumentException("不是有效的 EPUB：找不到 $opfPath")
            val opf = factory.newDocumentBuilder().parse(ByteArrayInputStream(opfBytes))

            val manifest = HashMap<String, String>()
            val items = opf.getElementsByTagNameNS("*", "item")
            for (i in 0 until items.length) {
                val item = items.item(i)
                val id = item.attributes.getNamedItem("id")?.nodeValue ?: continue
                val href = item.attributes.getNamedItem("href")?.nodeValue ?: continue
                manifest[id] = href
            }
            val base = opfPath.substringBeforeLast('/', "")
            val out = StringBuilder()
            val refs = opf.getElementsByTagNameNS("*", "itemref")
            for (i in 0 until refs.length) {
                val idref = refs.item(i).attributes.getNamedItem("idref")?.nodeValue ?: continue
                val href = manifest[idref] ?: continue
                val path = resolveZipPath(base, href) ?: continue
                val bytes = zip.entryBytes(path) ?: continue
                val text = stripHtml(String(bytes, Charsets.UTF_8)).trim()
                if (text.isNotBlank()) {
                    if (out.isNotEmpty()) out.append("\n\n")
                    out.append(text)
                }
            }
            if (out.isBlank()) throw IllegalArgumentException("EPUB 里没有可导入的文字")
            return out.toString()
        }
    }

    private fun ZipFile.entryBytes(name: String): ByteArray? {
        val entry = getEntry(name) ?: return null
        return getInputStream(entry).use { it.readBytes() }
    }

    /** `base` 是 OPF 所在目录；拒绝 `..` 逃出根目录的条目。 */
    private fun resolveZipPath(base: String, href: String): String? {
        val decoded = runCatching {
            java.net.URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        }.getOrElse { href.substringBefore('#') }
        val combined = if (base.isBlank()) decoded else "$base/$decoded"
        val parts = ArrayDeque<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast() else return null
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun readTextSmart(file: File): String {
        val bytes = file.readBytes()
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return runCatching { String(bytes, Charsets.UTF_8) }
            .getOrElse { String(bytes, charset("GB18030")) }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p\\s*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
    }

    private fun stripRtf(rtf: String): String {
        var text = rtf
        text = text.replace(Regex("\\\\'([0-9a-fA-F]{2})")) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        text = text.replace(Regex("\\\\u(-?\\d+)\\??"), "")
        text = text.replace(Regex("\\\\par[d]?"), "\n")
        text = text.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
        text = text.replace(Regex("[{}]"), "")
        return text
    }

    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
}

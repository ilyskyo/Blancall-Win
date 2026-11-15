package com.ilyskyo.blancall.desktop.platform

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.ilyskyo.blancall.desktop.ui.theme.UI_FONT_SANS
import com.ilyskyo.blancall.desktop.ui.theme.UI_FONT_XWWK
import com.ilyskyo.blancall.desktop.ui.theme.uiFontFamily
import java.io.File
import java.util.UUID

/**
 * 用户导入的阅读字体（TTF/OTF）。
 *
 * 与 Android 的口径一致：文件复制到应用数据目录后，以稳定 id 存进 ReaderPrefs.fontId，
 * 渲染时再解析成 FontFamily。字体文件本身不进备份——备份只带偏好 id，
 * 换机后缺失时自动回落到系统衬线，不会因为找不到文件而白屏。
 */
object ImportedFonts {

    const val ID_PREFIX = "imp:"

    data class Entry(val id: String, val name: String, val file: File)

    fun list(): List<Entry> {
        val dir = AppPaths.fontsDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("ttf", "otf") }
            ?.sortedBy { it.name.lowercase() }
            ?.map { file ->
                val raw = file.name.substringAfter('-', file.name)
                Entry(ID_PREFIX + file.name, raw.substringBeforeLast('.'), file)
            }
            .orEmpty()
    }

    fun find(id: String): Entry? {
        if (!id.startsWith(ID_PREFIX)) return null
        val fileName = id.removePrefix(ID_PREFIX)
        return list().firstOrNull { it.id == ID_PREFIX + fileName }
    }

    /** 复制并校验字体；返回 null 表示不是可解析的 TTF/OTF。 */
    fun import(source: File): Entry? {
        if (!source.isFile || source.extension.lowercase() !in setOf("ttf", "otf")) return null
        return runCatching {
            // 至少排除空文件/改后缀的文本；真正解析失败时渲染侧会回落到系统字体。
            if (source.length() < 1024L) return null
            val dir = AppPaths.fontsDir
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val safeName = source.name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            val target = File(dir, "${UUID.randomUUID().toString().take(8)}-$safeName.${source.extension.lowercase()}")
            source.copyTo(target, overwrite = false)
            Entry(ID_PREFIX + target.name, safeName, target)
        }.getOrNull()
    }

    fun delete(id: String): Boolean {
        val entry = find(id) ?: return false
        return runCatching { entry.file.delete() }.getOrDefault(false)
    }

    /** 阅读页字族：导入字体优先；找不到时回落到内置三档，绝不抛异常。 */
    fun resolve(fontId: String, weight: Int = 400): FontFamily {
        val imported = find(fontId)
        if (imported != null) {
            val composeWeight = FontWeight(weight.coerceIn(100, 900))
            return runCatching {
                FontFamily(Font(imported.file, composeWeight, FontStyle.Normal))
            }.getOrElse { uiFontFamily(UI_FONT_SANS) }
        }
        return uiFontFamily(fontId)
    }

    fun isImported(fontId: String): Boolean = fontId.startsWith(ID_PREFIX)

    /** 内置字体 + 导入字体的下拉选项。 */
    fun options(): List<Pair<String, String>> = buildList {
        add(UI_FONT_SANS to "系统黑体")
        add("serif" to "系统衬线")
        add(UI_FONT_XWWK to "霞鹜文楷")
        list().forEach { add(it.id to it.name) }
    }
}

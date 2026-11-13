package com.ilyskyo.blancall.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/**
 * 桌面端多语言（6 套：简中 / 繁中 / 英 / 日 / 韩 / 俄）。
 *
 * ## 为什么用「中文原文」当 key
 * 桌面 UI 的文案是逐页重写的，与 Android 的 1,583 条资源键不是一一对应。
 * 以中文原文为 key（gettext 风格）可以让 `Text("设置")` 这类调用**零改动**接入翻译，
 * 又能在 catalog 里一眼看出缺哪句；未翻译或运行时拼接的文案按中文兜底，不会显示空串。
 *
 * catalog 放在 `resources/i18n/strings_<tag>.properties`（UTF-8），
 * 守卫测试保证 6 套 key 集合一致。
 */
object Strings {

    const val ZH_CN = "zh-CN"
    const val ZH_TW = "zh-TW"
    const val EN = "en"
    const val JA = "ja"
    const val KO = "ko"
    const val RU = "ru"

    data class Language(val tag: String, val label: String)

    val languages = listOf(
        Language(ZH_CN, "简体中文"),
        Language(ZH_TW, "繁體中文"),
        Language(EN, "English"),
        Language(JA, "日本語"),
        Language(KO, "한국어"),
        Language(RU, "Русский"),
    )

    /** 当前语言（Compose State：切换后界面立即重组）。 */
    var locale by mutableStateOf(ZH_CN)
        private set

    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    fun use(tag: String) {
        locale = if (languages.any { it.tag == tag }) tag else ZH_CN
    }

    /** 本地化一条源文案；catalog 缺失时原样返回。 */
    fun t(source: String): String {
        if (locale == ZH_CN || source.isBlank()) return source
        return catalog(locale)[source] ?: source
    }

    /**
     * 动态文案模板：用 `{0}` `{1}` 占位，catalog 里存同一模板的译文。
     *
     * 运行时拼接的文案（`"共 $n 篇"`）无法直接查 catalog，
     * 所以统一改写成 `f("共 {0} 篇", n)`：查表得到译文模板后再填参。
     * 未收录时按中文模板直接填参，不会显示空串；占位符越界则原样保留。
     */
    fun f(template: String, vararg args: Any?): String {
        val pattern = t(template)
        if (args.isEmpty()) return pattern
        val out = StringBuilder(pattern.length + 16)
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '{') {
                val end = pattern.indexOf('}', i + 1)
                if (end > i + 1) {
                    val index = pattern.substring(i + 1, end).toIntOrNull()
                    if (index != null && index in args.indices) {
                        out.append(args[index].toString())
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** 供测试与守卫读取某套 catalog。 */
    fun catalog(tag: String): Map<String, String> {
        if (tag == ZH_CN) return emptyMap()
        return cache.getOrPut(tag) { load(tag) }
    }

    private fun load(tag: String): Map<String, String> {
        val path = "/i18n/strings_$tag.properties"
        val stream = Strings::class.java.getResourceAsStream(path) ?: return emptyMap()
        return try {
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                val map = HashMap<String, String>()
                lines.forEach { raw ->
                    val line = raw.trimEnd('\r')
                    // 注释与空行跳过
                    if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) return@forEach
                    // 只按第一个 '=' 分割：java.util.Properties 会把键里的空格也当分隔符，
                    // 而中文原文键里普遍带空格（"共 {0} 篇"），必须自定义解析才能对上。
                    val sep = line.indexOf('=')
                    if (sep <= 0) return@forEach
                    val key = line.substring(0, sep)
                    val value = line.substring(sep + 1)
                    if (key.isNotBlank() && value.isNotBlank()) map[key] = value
                }
                map
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

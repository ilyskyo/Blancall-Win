package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.desktop.app.Strings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 中文文案覆盖守卫：UI 里出现的每个中文字面量都必须能查到英文词条。
 *
 * 之前只测 `Strings.t("设置")`，结果设置页大半文案根本没进 catalog，
 * 切到英文仍显示中文。这里直接扫源码，和运行时走同一条「中文原文当 key」的路径。
 */
class I18nCoverageTest {

    private val whitelist = setOf(
        // 语言名固定用各自语言写，不翻译
        "简体中文", "繁體中文", "日本語", "한국어", "Русский", "English",
        // 注释/CSV 表头不是 UI
        "共 \$n 篇",
        "时间,文章,模式,正确,总空数,正确率,用时秒,错题数\n",
    )

    @Test
    fun `UI 中文文案都有英文词条`() {
        val enKeys = Strings.catalog(Strings.EN).keys
        val stringRegex = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
        val cjk = Regex("[\u4e00-\u9fff]")
        val root = File("src/main/kotlin")
        assertTrue(root.isDirectory, "找不到 desktop 源码目录")

        val missing = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    stringRegex.findAll(line).forEach { match ->
                        val raw = match.value.removeSurrounding("\"")
                        val text = raw
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                        if (text.isBlank() || text in whitelist || !cjk.containsMatchIn(text)) return@forEach
                        if (text !in enKeys) {
                            missing += "${file.path}:${index + 1}: $text"
                        }
                    }
                }
            }
        assertTrue(
            missing.isEmpty(),
            "以下 UI 文案缺英文词条，切语言时会回落中文：\n${missing.joinToString("\n")}",
        )
    }
}

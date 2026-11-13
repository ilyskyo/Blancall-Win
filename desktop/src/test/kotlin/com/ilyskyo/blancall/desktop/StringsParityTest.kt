package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.desktop.app.Strings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 六语言守卫（对应 Android 的 LocaleParityGuard 思路）：
 * 5 套非简中 catalog 必须与英语 key 集合完全一致、值非空；
 * 切换语言后常用文案必须真的被替换，未知语言回落到简中。
 */
class StringsParityTest {

    private val tags = listOf(Strings.EN, Strings.ZH_TW, Strings.JA, Strings.KO, Strings.RU)

    @Test
    fun `五套语言键集合一致且值非空`() {
        val maps = tags.associateWith { Strings.catalog(it) }
        maps.forEach { (tag, map) ->
            assertTrue(map.isNotEmpty(), "$tag catalog 不能为空")
            assertTrue(map.values.none { it.isBlank() }, "$tag catalog 不能有空值")
        }
        val base = maps.getValue(Strings.EN).keys
        tags.forEach { tag ->
            assertEquals(base, maps.getValue(tag).keys, "$tag 的 key 集合必须与 en 一致")
        }
    }

    @Test
    fun `key 数量达到桌面 UI 覆盖要求`() {
        assertTrue(
            Strings.catalog(Strings.EN).size >= 300,
            "桌面 UI 的简单文案应至少覆盖 300 条，实际 ${Strings.catalog(Strings.EN).size}",
        )
    }

    @Test
    fun `切换语言返回翻译`() {
        try {
            Strings.use(Strings.EN)
            assertEquals("Settings", Strings.t("设置"))
            assertEquals("Home", Strings.t("首页"))
            Strings.use(Strings.JA)
            assertEquals("設定", Strings.t("设置"))
            Strings.use(Strings.RU)
            assertEquals("Настройки", Strings.t("设置"))
        } finally {
            Strings.use(Strings.ZH_CN)
        }
    }

    @Test
    fun `未知语言回落简中`() {
        try {
            Strings.use("xx-YY")
            assertEquals("设置", Strings.t("设置"))
        } finally {
            Strings.use(Strings.ZH_CN)
        }
    }

    @Test
    fun `未收录的运行时文案原样返回`() {
        Strings.use(Strings.EN)
        try {
            assertEquals("已导入 3 篇文章", Strings.t("已导入 3 篇文章"))
        } finally {
            Strings.use(Strings.ZH_CN)
        }
    }

    @Test
    fun `动态模板按占位符填参`() {
        try {
            Strings.use(Strings.ZH_CN)
            assertEquals("3 篇", Strings.f("{0} 篇", 3))
            assertEquals("第 2 空 · 第 5 句", Strings.f("第 {0} 空 · 第 {1} 句", 2, 5))

            Strings.use(Strings.EN)
            assertEquals("3 articles", Strings.f("{0} 篇", 3))
            assertEquals("2/4 correct · rating Good", Strings.f("{0}/{1} 正确 · 评级 {2}", 2, 4, "Good"))

            Strings.use(Strings.JA)
            assertEquals("3 記事", Strings.f("{0} 篇", 3))

            Strings.use(Strings.KO)
            assertEquals("3편", Strings.f("{0} 篇", 3))

            Strings.use(Strings.RU)
            assertEquals("3 статей", Strings.f("{0} 篇", 3))
        } finally {
            Strings.use(Strings.ZH_CN)
        }
    }

    @Test
    fun `动态模板缺失时按中文兜底且不吞占位符`() {
        Strings.use(Strings.EN)
        try {
            assertEquals("未知 7 模板", Strings.f("未知 {0} 模板", 7))
            // 参数不足时保留原占位符，不能抛异常或吞字
            assertEquals("只有 {1}", Strings.f("只有 {1}", 9))
            assertEquals("原样 {0}", Strings.f("原样 {0}"))
        } finally {
            Strings.use(Strings.ZH_CN)
        }
    }

    @Test
    fun `六套 catalog 均收录常用动态模板`() {
        val templates = listOf(
            "{0} 篇",
            "第 {0} 空 · 第 {1} 句",
            "{0}/{1} 正确 · 评级 {2}",
            "保存失败：{0}",
            "距考试 {0} 天 · 今日约 {1} 分钟",
            "已导入《{0}》",
        )
        tags.forEach { tag ->
            val catalog = Strings.catalog(tag)
            templates.forEach { template ->
                assertTrue(
                    catalog.containsKey(template),
                    "$tag 缺少动态模板：$template",
                )
            }
        }
    }
}

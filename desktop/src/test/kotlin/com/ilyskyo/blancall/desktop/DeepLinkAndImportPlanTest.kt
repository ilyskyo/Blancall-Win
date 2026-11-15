package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.algorithm.DataInterchange
import com.ilyskyo.blancall.data.model.PracticeRecord
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.platform.DeepLink
import com.ilyskyo.blancall.desktop.ui.buildImportPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkAndImportPlanTest {

    @Test
    fun `命令行深链解析文章与路由`() {
        assertEquals(
            Screen.Practice(12L),
            DeepLink.parse(arrayOf("--route=practice", "--article=12")),
        )
        assertEquals(Screen.Reader(3L), DeepLink.parse(arrayOf("--article=3")))
        assertEquals(
            Screen.CustomClozeList(7L),
            DeepLink.parse(arrayOf("--route=custom-cloze", "--article=7")),
        )
        assertEquals(
            Screen.MaskConfigEdit(7L, 2L),
            DeepLink.parse(arrayOf("--route=mask-config", "--article=7", "--config=2")),
        )
        assertEquals(Screen.TodayPlan, DeepLink.parse(arrayOf("--route=today")))
        assertNull(DeepLink.parse(arrayOf("--route=unknown")))
    }

    @Test
    fun `CSV 回灌计划按标题兜底并跳过重复与无文章行`() {
        val imported = DataInterchange.ImportedRecord(
            articleId = 99,
            articleTitle = "已有标题",
            mode = "SENTENCE",
            totalBlanks = 3,
            correctCount = 2,
            mistakes = emptyList(),
            timestamp = 1_000L,
            durationMs = 0L,
        )
        val duplicate = imported.copy()
        val orphan = imported.copy(articleId = 55, articleTitle = "不存在")
        val plan = buildImportPlan(
            parsed = DataInterchange.CsvParse(listOf(imported, duplicate, orphan), badRows = 1),
            articles = listOf(1L to "已有标题"),
            existing = listOf(
                PracticeRecord(
                    id = 9,
                    articleId = 1,
                    mode = "SENTENCE",
                    totalBlanks = 3,
                    correctCount = 2,
                    timestamp = 1_000L,
                ),
            ),
        )
        assertEquals(0, plan.ready.size)
        // 两条都命中「已存在」：一条与库内记录重复，另一条与文件内首条重复
        assertEquals(2, plan.duplicate)
        assertEquals(1, plan.noArticle)
        assertEquals(1, plan.badRows)

        val fresh = buildImportPlan(
            parsed = DataInterchange.CsvParse(
                listOf(imported.copy(timestamp = 2_000L), orphan),
                badRows = 0,
            ),
            articles = listOf(1L to "已有标题"),
            existing = emptyList(),
        )
        assertEquals(1, fresh.ready.size)
        assertEquals(1L, fresh.ready.single().articleId)
        assertEquals(1, fresh.noArticle)
    }
}

// Copyright (c) 2026 ilyskyo
// SPDX-License-Identifier: MIT

package com.ilyskyo.blancall.algorithm

import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.data.model.AnsweredBlank
import com.ilyskyo.blancall.data.model.MistakeDetail
import com.ilyskyo.blancall.data.model.PracticeRecord
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 规模预算（对应 PROMPT 的 1,000 篇 / 5 万条记录）：
 * 用真实纯算法路径跑一遍，防止某次重构把 O(n) 变成 O(n²) 而单测看不出来。
 *
 * 阈值刻意放宽（15s / 2s）：这里验证的是**复杂度量级**，不是微基准；
 * 在普通开发机上这两步应在数十毫秒到数百毫秒内完成。
 */
class ScaleBudgetTest {

    private fun article(index: Int): Article = Article(
        id = index.toLong() + 1,
        title = "文章 $index",
        content = buildString {
            repeat(5) { sentence ->
                append("这是第 $index 篇的第 $sentence 句，用来测试大规模聚合不会退化成平方复杂度。")
            }
        },
    )

    private fun record(index: Int, articleId: Long): PracticeRecord {
        val answer = "第${index % 97}个答案"
        return PracticeRecord(
            id = index.toLong() + 1,
            articleId = articleId,
            mode = "SENTENCE",
            totalBlanks = 3,
            correctCount = if (index % 3 == 0) 2 else 3,
            mistakes = if (index % 3 == 0) {
                listOf(
                    MistakeDetail(
                        blankIndex = 0,
                        correctAnswer = answer,
                        userAnswer = "错${index % 13}",
                        errorType = "TYPO",
                        sentenceIndex = 0,
                        similarity = 0.5f,
                        sentenceText = "这是第 $articleId 篇的第 0 句，用来测试大规模聚合不会退化成平方复杂度。",
                        articleId = articleId,
                    ),
                )
            } else {
                emptyList()
            },
            timestamp = 1_700_000_000_000L + index,
            answeredBlanks = listOf(
                AnsweredBlank(
                    blankIndex = 0,
                    correctAnswer = answer,
                    userAnswer = if (index % 3 == 0) "错${index % 13}" else answer,
                    errorType = if (index % 3 == 0) "TYPO" else "",
                    sentenceIndex = 0,
                    sentenceText = "这是第 $articleId 篇的第 0 句，用来测试大规模聚合不会退化成平方复杂度。",
                    similarity = if (index % 3 == 0) 0.5f else 1f,
                    articleId = articleId,
                ),
            ),
        )
    }

    @Test
    fun `五万条记录的错题聚合在预算内完成`() {
        val records = ArrayList<PracticeRecord>(50_000)
        repeat(50_000) { index ->
            records += record(index, (index % 1_000).toLong() + 1)
        }
        val started = System.nanoTime()
        val entries = MistakeNotebook.entries(records, articleId = null)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("50k records -> entries=${entries.size}, elapsed=${elapsedMs}ms")
        assertTrue(entries.isNotEmpty(), "5 万条记录应聚合出错题")
        assertTrue(elapsedMs < 15_000, "错题聚合 5 万条耗时 ${elapsedMs}ms，超出 15s 预算")
    }

    @Test
    fun `一千篇文章的今日计划在预算内完成`() {
        val now = System.currentTimeMillis()
        val articles = (0 until 1_000).map { article(it) }
        val due = articles.mapIndexed { index, a ->
            StudyCoach.DueArticle(
                articleId = a.id,
                title = a.title,
                due = now - index * 60_000L,
                reviewCount = 1,
                estimatedBlanks = 5,
            )
        }
        val input = StudyCoach.Input(
            now = now,
            dueArticles = due,
            todayPracticeCount = 2,
            dailyPracticeGoal = 5,
        )
        val started = System.nanoTime()
        val plan = StudyCoach.buildPlan(input)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("1000 due articles -> plan items=${plan.items.size}, elapsed=${elapsedMs}ms")
        assertTrue(plan.items.isNotEmpty(), "1000 篇到期文章应产出计划")
        assertTrue(elapsedMs < 2_000, "今日计划 1000 篇耗时 ${elapsedMs}ms，超出 2s 预算")
    }
}

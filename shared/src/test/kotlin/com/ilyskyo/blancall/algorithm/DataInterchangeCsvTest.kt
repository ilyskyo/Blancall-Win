// Copyright (c) 2026 ilyskyo
// SPDX-License-Identifier: MIT

package com.ilyskyo.blancall.algorithm

import com.ilyskyo.blancall.data.model.MistakeDetail
import com.ilyskyo.blancall.data.model.PracticeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class DataInterchangeCsvTest {

    private val timestamp = LocalDateTime.of(2026, 11, 15, 9, 30, 5)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `12 列 CSV 可往返并恢复逐空错因`() {
        val record = PracticeRecord(
            id = 1,
            articleId = 2,
            mode = "SENTENCE",
            totalBlanks = 3,
            correctCount = 2,
            mistakes = listOf(
                MistakeDetail(
                    blankIndex = 1,
                    correctAnswer = "已",
                    userAnswer = "己",
                    errorType = "GLYPH_CONFUSED",
                    sentenceIndex = 4,
                    similarity = 0.5f,
                    sentenceText = "学而时习之，不亦说乎。",
                    articleId = 2,
                ),
            ),
            timestamp = timestamp,
            duration = 12_000,
        )

        val csv = DataInterchange.buildCsv(listOf(record), mapOf(2L to "论语"), Locale.ROOT)
        assertTrue("必须带 UTF-8 BOM", csv.startsWith("\uFEFF"))
        val parsed = DataInterchange.parseCsv(csv)
        assertEquals(1, parsed.records.size)
        assertEquals(0, parsed.badRows)
        val imported = parsed.records.single()
        assertEquals(2L, imported.articleId)
        assertEquals("论语", imported.articleTitle)
        assertEquals("SENTENCE", imported.mode)
        assertEquals(3, imported.totalBlanks)
        assertEquals(2, imported.correctCount)
        assertEquals(timestamp, imported.timestamp)
        assertEquals(12_000L, imported.durationMs)
        assertEquals(1, imported.mistakes.size)
        assertEquals("GLYPH_CONFUSED", imported.mistakes[0].errorType)
        assertEquals("己", imported.mistakes[0].userAnswer)
    }

    @Test
    fun `导出时给公式前缀加保护`() {
        val record = PracticeRecord(
            id = 5,
            articleId = 6,
            mode = "WORD",
            totalBlanks = 1,
            correctCount = 0,
            mistakes = listOf(
                MistakeDetail(
                    blankIndex = 0,
                    correctAnswer = "=1+1",
                    userAnswer = "@cmd",
                    errorType = "TYPO",
                ),
            ),
            timestamp = timestamp,
        )
        val csv = DataInterchange.buildCsv(listOf(record), mapOf(6L to "=危险标题"), Locale.ROOT)
        assertTrue(csv.contains("'=危险标题"))
        val parsed = DataInterchange.parseCsv(csv)
        assertEquals("=危险标题", parsed.records.single().articleTitle)
        assertEquals("=1+1", parsed.records.single().mistakes.single().correctAnswer)
    }
}

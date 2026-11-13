// Copyright (c) 2026 ilyskyo
// SPDX-License-Identifier: MIT

package com.ilyskyo.blancall.algorithm

/**
 * 阅读背诵遮挡的本地算法（与 Android `ReaderOcclusion` 同一口径，去掉 Compose 依赖）。
 *
 * 三种粒度：
 * - `short`：短遮挡（字词级）——每分句挑最难的一至三个汉字 / 最长的至多两个拉丁词；
 * - `long`：长遮挡（复句级）——把每个分句作为一整块遮住；
 * - `mixed`：混合——按稳定伪随机逐句在长遮与短遮之间二选一。
 *
 * 返回的区间都是半开 `[start, end)`，坐标相对传入的文本。
 */
object ReaderOcclusion {

    private val CLAUSE_PUNCT = setOf('，', ',', '；', ';', '、', '。', '.', '！', '！', '?', '？', '\n')

    /** 段落（trim 后的文本与其在原文中的起止） */
    data class Para(val start: Int, val end: Int, val text: String)

    /** 一个遮挡区间（半开） */
    data class Span(val start: Int, val end: Int)

    private const val SHORT_MAX_CHARS = 3
    private const val SHORT_THRESHOLD = 0.35f
    private const val SHORT_MAX_WORDS = 2
    private const val LATIN_MIN_WORD_LEN = 3

    /**
     * 将文本按「空行段落」切分为段落，记录每个段落 trim 后在原文中的 [start,end)。
     * 与正文渲染的 `split("\n\\s*\n")` 语义对齐，供遮挡做区间映射。
     */
    fun splitParagraphs(text: String): List<Para> {
        val res = mutableListOf<Para>()
        val n = text.length
        var i = 0
        while (i < n) {
            while (i < n && (text[i].isWhitespace() || text[i] == '\n')) i++
            if (i >= n) break
            val s = i
            while (i < n) {
                if (text[i] == '\n') {
                    var j = i + 1
                    while (j < n && text[j] != '\n' && text[j].isWhitespace()) j++
                    if (j < n && text[j] == '\n') break
                }
                i++
            }
            val e = i
            var ls = s
            while (ls < e && text[ls].isWhitespace()) ls++
            var le = e
            while (le > ls && text[le - 1].isWhitespace()) le--
            if (le > ls) res.add(Para(ls, le, text.substring(ls, le)))
        }
        return res
    }

    private fun hasChineseInRange(text: String, s: Int, e: Int): Boolean {
        for (i in s until e) if (isChinese(text[i])) return true
        return false
    }

    private fun hasLatinWordInRange(text: String, s: Int, e: Int): Boolean {
        var run = 0
        for (i in s until e) {
            val c = text[i]
            if (c.isLetter() && !isChinese(c)) {
                run++
                if (run >= LATIN_MIN_WORD_LEN) return true
            } else {
                run = 0
            }
        }
        return false
    }

    private fun pickHardWords(text: String, s: Int, e: Int): List<Span> {
        val words = mutableListOf<Span>()
        var i = s
        while (i < e) {
            val c = text[i]
            if (c.isLetter() && !isChinese(c)) {
                var j = i
                while (j < e && text[j].isLetter() && !isChinese(text[j])) j++
                if (j - i >= LATIN_MIN_WORD_LEN) words += Span(i, j)
                i = j
            } else {
                i++
            }
        }
        return words.sortedByDescending { it.end - it.start }.take(SHORT_MAX_WORDS)
    }

    private fun pickHardChars(text: String, s: Int, e: Int): List<Span> {
        if (e - s < 2) return emptyList()
        val hard = mutableListOf<Int>()
        for (i in s until e) {
            if (isChinese(text[i])) {
                val d = DifficultyCalculator.calculateCharDifficulty(text[i])
                if (d >= SHORT_THRESHOLD) hard.add(i)
            }
        }
        if (hard.isEmpty()) return emptyList()
        hard.sortByDescending { DifficultyCalculator.calculateCharDifficulty(text[it]) }
        return hard.take(SHORT_MAX_CHARS).map { Span(it, it + 1) }
    }

    private fun mixedUseLong(clauseStart: Int, textLen: Int): Boolean {
        var h = (clauseStart.toLong() * 374761393L + textLen.toLong() * 668265263L) and 0x7FFFFFFFFFFFFFFFL
        h = (h * 2654435761L) and 0x7FFFFFFFFFFFFFFFL
        return (h and 1L) == 0L
    }

    private fun clausesOf(para: String): List<Pair<Int, Int>> {
        val res = mutableListOf<Pair<Int, Int>>()
        var start = 0
        for (i in para.indices) {
            if (para[i] in CLAUSE_PUNCT) {
                res.add(start to i + 1)
                start = i + 1
            }
        }
        if (start < para.length) res.add(start to para.length)
        return res
    }

    /** 段落级本地遮挡（返回段内区间） */
    fun localRangesInPara(para: String, mode: String): List<Span> {
        val out = mutableListOf<Span>()
        for ((s, e) in clausesOf(para)) {
            if (e - s <= 0) continue
            when (mode) {
                "long" -> if (hasChineseInRange(para, s, e) || hasLatinWordInRange(para, s, e)) {
                    out += Span(s, e)
                }
                "short" -> out += pickHardChars(para, s, e) + pickHardWords(para, s, e)
                "mixed" -> if (mixedUseLong(s, para.length)) {
                    if (hasChineseInRange(para, s, e) || hasLatinWordInRange(para, s, e)) {
                        out += Span(s, e)
                    }
                } else {
                    out += pickHardChars(para, s, e) + pickHardWords(para, s, e)
                }
                else -> out += pickHardChars(para, s, e) + pickHardWords(para, s, e)
            }
        }
        return out.distinctBy { it.start }
    }

    /** 段内「分句」区间（含句末标点），供自定义遮挡编辑器点句用 */
    fun clauseRanges(para: String): List<Span> =
        clausesOf(para).map { (s, e) -> Span(s, e) }.filter { it.end > it.start }

    /** 段内「编辑单元」区间（拆词/拆字用），单位与 Android 相同 */
    fun editUnits(para: String, level: Int): List<Span> {
        if (para.isEmpty()) return emptyList()
        if (level <= 0) return listOf(Span(0, para.length))

        val out = mutableListOf<Span>()
        var i = 0
        val n = para.length

        fun isPunct(c: Char) = !isChinese(c) && !c.isLetter()

        val chunk = if (level == 1) 2 else 1
        while (i < n) {
            val c = para[i]
            when {
                isChinese(c) -> {
                    var runEnd = i
                    while (runEnd < n && isChinese(para[runEnd])) runEnd++
                    var s = i
                    while (s < runEnd) {
                        val e = minOf(s + chunk, runEnd)
                        out.add(Span(s, e))
                        s = e
                    }
                    i = runEnd
                }
                c.isLetter() -> {
                    var runEnd = i
                    while (runEnd < n && para[runEnd].isLetter() && !isChinese(para[runEnd])) runEnd++
                    out.add(Span(i, runEnd))
                    i = runEnd
                }
                else -> {
                    i++
                    while (i < n && isPunct(para[i])) i++
                    if (out.isNotEmpty()) {
                        val last = out.removeAt(out.size - 1)
                        out.add(Span(last.start, i))
                    } else if (i > 0) {
                        out.add(Span(0, i))
                    }
                }
            }
        }
        return out.filter { it.end > it.start }
    }

    /** 整篇本地遮挡（返回在 [text] 上的全局区间） */
    fun localRanges(text: String, mode: String): List<Span> {
        val out = mutableListOf<Span>()
        for (p in splitParagraphs(text)) {
            for (sp in localRangesInPara(p.text, mode)) out.add(Span(p.start + sp.start, p.start + sp.end))
        }
        return out
    }

    private fun isChinese(ch: Char): Boolean =
        ch in '\u4e00'..'\u9fff' || ch in '\u3400'..'\u4dbf'
}

package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.algorithm.AnswerChecker
import com.ilyskyo.blancall.algorithm.BlancallGenerator
import com.ilyskyo.blancall.data.model.AnsweredBlank
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.data.model.MistakeDetail
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import kotlinx.coroutines.launch

private data class CrossQuestion(
    val articleId: Long,
    val articleTitle: String,
    val blankedSentence: String,
    val answer: String,
    val sentenceIndex: Int,
    val sentenceText: String,
)

/**
 * 跨文混合练习：从选中的每篇文章各取若干句，混合成一次练习。
 *
 * 记录口径与 Android 一致：`articleId = -1` + `sourceArticleIds`，
 * 逐空带来源文章；FSRS 按来源文章分别推进。
 */
@Composable
fun CrossPracticeScreen(state: AppState, articleIds: List<Long>) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val selected = remember(articleIds, articles) {
        articleIds.mapNotNull { id -> articles.firstOrNull { it.id == id } }
    }
    val questions = remember(selected.map { it.id }, selected.map { it.updatedAt }) {
        buildQuestions(selected)
    }
    val answers = remember { mutableStateMapOf<Int, String>() }
    var submitted by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { state.back() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("跨文练习", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    Strings.f(
                        "{0} 篇 · {1} 空 · 结果会按来源文章分别写回 FSRS",
                        selected.size,
                        questions.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (submitted) {
                Button(onClick = { state.navigate(Screen.CrossPractice(articleIds)) }) { Text("再练一次") }
            }
            TextButton(onClick = { state.navigate(Screen.Articles) }) { Text("返回列表") }
        }

        if (questions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("选中的文章没有可切分的句子。", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { state.navigate(Screen.Articles) }) { Text("重新选择") }
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            questions.forEachIndexed { index, question ->
                item {
                    SectionCard(
                        modifier = Modifier.widthIn(max = 1000.dp),
                        title = Strings.f(
                            "第 {0} 空 · {1}",
                            index + 1,
                            question.articleTitle.ifBlank { Strings.t("未命名") },
                        ),
                        subtitle = if (submitted) Strings.f("正确答案：{0}", question.answer)
                        else "填写横线处的内容",
                    ) {
                        Text(
                            question.blankedSentence,
                            fontSize = state.prefs.readerFontPx.sp,
                            lineHeight = (state.prefs.readerFontPx * state.prefs.readerLineHeight).sp,
                        )
                        OutlinedTextField(
                            value = answers[index].orEmpty(),
                            onValueChange = { answers[index] = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !submitted,
                            singleLine = true,
                            label = { Text("填写被挖掉的内容") },
                        )
                        if (submitted) {
                            val check = AnswerChecker.check(question.answer, answers[index].orEmpty())
                            Text(
                                if (check.result == AnswerChecker.Result.CORRECT) "正确"
                                else Strings.f("错误：{0}", check.result.name),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (check.result == AnswerChecker.Result.CORRECT)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.widthIn(max = 1000.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !submitted && !saving,
                        onClick = {
                            if (submitted || saving) return@Button
                            val checks = questions.mapIndexed { i, q ->
                                AnswerChecker.check(q.answer, answers[i].orEmpty())
                            }
                            val correct = checks.count { it.result == AnswerChecker.Result.CORRECT }
                            val similarity = checks.map { it.similarity }.average().toFloat()
                            val mistakes = checks.mapIndexedNotNull { i, detail ->
                                if (detail.result == AnswerChecker.Result.CORRECT) null else {
                                    val q = questions[i]
                                    MistakeDetail(
                                        blankIndex = i,
                                        correctAnswer = q.answer,
                                        userAnswer = answers[i].orEmpty(),
                                        errorType = detail.result.name,
                                        sentenceIndex = q.sentenceIndex,
                                        similarity = detail.similarity,
                                        sentenceText = q.sentenceText,
                                        articleId = q.articleId,
                                    )
                                }
                            }
                            val answered = checks.mapIndexed { i, detail ->
                                val q = questions[i]
                                AnsweredBlank(
                                    blankIndex = i,
                                    correctAnswer = q.answer,
                                    userAnswer = answers[i].orEmpty(),
                                    errorType = if (detail.result == AnswerChecker.Result.CORRECT) ""
                                    else detail.result.name,
                                    sentenceIndex = q.sentenceIndex,
                                    sentenceText = q.sentenceText,
                                    similarity = detail.similarity,
                                    articleId = q.articleId,
                                )
                            }
                            submitted = true
                            saving = true
                            scope.launch {
                                try {
                                    // 记录耗时
                                    val rating = state.saveCrossPractice(
                                        sourceArticleIds = articleIds,
                                        totalBlanks = questions.size,
                                        correctCount = correct,
                                        similarity = similarity,
                                        mistakes = mistakes,
                                        answeredBlanks = answered,
                                    )
                                    resultText = Strings.f(
                                        "{0}/{1} 正确 · 平均相似度 {2} · 评级 {3}",
                                        correct,
                                        questions.size,
                                        percent(similarity),
                                        rating.name,
                                    )
                                } catch (e: Exception) {
                                    state.showToast(Strings.f("保存失败：{0}", e.message))
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    ) { Text(if (saving) "保存中…" else "提交答案") }
                    if (submitted) {
                        TextButton(onClick = { state.navigate(Screen.Statistics()) }) { Text("查看统计") }
                    }
                }
            }
            resultText?.let { text ->
                item {
                    SectionCard(modifier = Modifier.widthIn(max = 1000.dp), title = "本次结果") {
                        Text(text, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 每篇文章取 2~3 空；整场上限 24 空，避免跨 10 篇时变成一次小考试。 */
private fun buildQuestions(articles: List<Article>): List<CrossQuestion> {
    val out = mutableListOf<CrossQuestion>()
    for (article in articles) {
        if (article.content.isBlank()) continue
        val cloze = runCatching {
            BlancallGenerator.generateSentenceCloze(article.content, count = 3)
        }.getOrNull() ?: continue
        for (blank in cloze.blanks) {
            val sentence = cloze.sentences.getOrNull(blank.sentenceIndex) ?: continue
            val start = blank.startInSentence.coerceIn(0, sentence.length)
            val end = blank.endInSentence.coerceIn(start, sentence.length)
            if (end <= start) continue
            out += CrossQuestion(
                articleId = article.id,
                articleTitle = article.title,
                blankedSentence = sentence.replaceRange(start, end, "____"),
                answer = blank.originalText,
                sentenceIndex = blank.sentenceIndex,
                sentenceText = sentence,
            )
            if (out.size >= 24) return out
        }
    }
    return out
}

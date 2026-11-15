package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.DataInterchange
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.data.model.PracticeRecord
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * 数据互换：人类可读 JSON 导出 + 12 列 CSV 的导出/回灌。
 *
 * 刻意不做 JSON 导入：整包备份是唯一权威恢复路径。
 */
@Composable
fun DataInterchangeScreen(state: AppState) {
    val records by state.records.records.collectAsState(initial = emptyList())
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    val listState = rememberLazyListState()

    fun exportJson() {
        val target = FileDialogs.save(
            "导出学习数据",
            "blancall_export_${LocalDate.now()}.json",
        ) ?: return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val json = DataInterchange.buildExportJson(
                        records = state.records.records.value,
                        articleTitles = state.articles.snapshot.associate { it.id to it.title },
                        sentenceStates = state.fsrs.allSentenceStates(),
                        tags = state.tags.snapshot(),
                    )
                    target.writeText(json, Charsets.UTF_8)
                }
                state.showToast(Strings.f("已导出：{0}", target.name))
            } catch (e: Exception) {
                state.reportStorageError("导出 JSON", e)
            } finally {
                busy = false
            }
        }
    }

    fun exportCsv() {
        val target = FileDialogs.save(
            "导出练习记录",
            "blancall_records_${LocalDate.now()}.csv",
        ) ?: return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val csv = DataInterchange.buildCsv(
                        records = state.records.records.value,
                        articleTitles = state.articles.snapshot.associate { it.id to it.title },
                        locale = Locale.forLanguageTag(state.prefs.language),
                    )
                    target.writeText(csv, Charsets.UTF_8)
                }
                state.showToast(Strings.f("已导出：{0}", target.name))
            } catch (e: Exception) {
                state.reportStorageError("导出 CSV", e)
            } finally {
                busy = false
            }
        }
    }

    fun pickCsv() {
        val source = FileDialogs.open("导入练习记录 CSV", listOf(".csv")) ?: return
        scope.launch {
            busy = true
            try {
                val parsed = withContext(Dispatchers.IO) {
                    DataInterchange.parseCsv(source.readText(Charsets.UTF_8))
                }
                plan = buildImportPlan(
                    parsed = parsed,
                    articles = state.articles.snapshot.map { it.id to it.title },
                    existing = state.records.records.value,
                )
            } catch (e: Exception) {
                state.showToast(Strings.f("导入失败：{0}", e.message))
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth().widthIn(max = 880.dp)) {
                BlancallTopBar(
                    title = "数据互换",
                    subtitle = "JSON 用于查看与分析；CSV 只回灌练习记录，整包恢复请用备份",
                    onBack = { state.back() },
                )
            }
        }
        item {
            SectionCard(
                Modifier.fillMaxWidth().widthIn(max = 880.dp),
                title = "导出",
                subtitle = "记录、句子 FSRS 状态与标签；不含 AI 数据",
            ) {
                Text(
                    Strings.f(
                        "当前有 {0} 篇、{1} 条练习记录。",
                        articles.size,
                        records.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPill(
                        if (busy) "处理中…" else "导出 JSON",
                        enabled = !busy,
                        onClick = { exportJson() },
                    )
                    GlassButton(enabled = !busy, onClick = { exportCsv() }) {
                        Text("导出 CSV（12 列）", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            SectionCard(
                Modifier.fillMaxWidth().widthIn(max = 880.dp),
                title = "导入练习记录",
                subtitle = "按列位解析，不依赖界面语言；重复记录会跳过，找不到文章的行不写入",
            ) {
                Text(
                    "这是练习记录的补充路径，不会覆盖文章、FSRS 状态或标签。要完整恢复请使用 设置 → 数据 → 导入备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(enabled = !busy, onClick = { pickCsv() }) {
                    Text("选择 CSV 文件", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            SectionCard(
                Modifier.fillMaxWidth().widthIn(max = 880.dp),
                title = "格式说明",
                subtitle = "与 Android 版同契约",
            ) {
                Text(
                    "JSON 顶层包含 format / version / exportedAt / records / sentenceStates / tags / tagLinks。" +
                        "CSV 固定 12 列：ID, 文章ID, 标题, 模式, 总空, 正确, 正确率, 用时秒, 时间, 错误数, 错误明细, mistakes_json；第 12 列用于恢复逐空错因。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    plan?.let { current ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { plan = null }) {
            GlassCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text("导入预览", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                KeyValueRow("可写入", current.ready.size.toString())
                KeyValueRow("找不到文章", current.noArticle.toString())
                KeyValueRow("已存在（跳过）", current.duplicate.toString())
                KeyValueRow("无法解析的行", current.badRows.toString())
                Spacer(Modifier.height(8.dp))
                Text(
                    "只追加练习记录，不会改动文章内容；重复判定按「文章 + 时间 + 模式 + 总空 + 正确数」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassButton(onClick = { plan = null }) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                    PrimaryPill(
                        "写入",
                        enabled = current.ready.isNotEmpty() && !busy,
                        onClick = {
                            plan = null
                            scope.launch {
                                busy = true
                                var inserted = 0
                                try {
                                    withContext(Dispatchers.IO) {
                                        current.ready.forEach { imported ->
                                            val total = imported.totalBlanks
                                            val similarity = if (total > 0) {
                                                imported.correctCount.toFloat() / total
                                            } else 0f
                                            runCatching {
                                                state.records.insert(
                                                    PracticeRecord(
                                                        articleId = imported.articleId,
                                                        mode = imported.mode,
                                                        totalBlanks = total,
                                                        correctCount = imported.correctCount,
                                                        mistakes = imported.mistakes,
                                                        timestamp = imported.timestamp,
                                                        duration = imported.durationMs,
                                                        similarity = similarity,
                                                        rating = FsrsEngine.gradeFromSimilarity(similarity).value,
                                                    ),
                                                )
                                            }.onSuccess { inserted++ }
                                        }
                                    }
                                    state.showToast(Strings.f("已导入 {0} 条记录", inserted))
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

internal data class ImportPlan(
    val ready: List<DataInterchange.ImportedRecord>,
    val noArticle: Int,
    val duplicate: Int,
    val badRows: Int,
)

internal fun buildImportPlan(
    parsed: DataInterchange.CsvParse,
    articles: List<Pair<Long, String>>,
    existing: List<PracticeRecord>,
): ImportPlan {
    val byId = articles.toMap()
    val byTitle = articles.groupBy { it.second }
    val seen = existing.map { existingKey(it) }.toHashSet()
    val ready = mutableListOf<DataInterchange.ImportedRecord>()
    var noArticle = 0
    var duplicate = 0
    parsed.records.forEach { record ->
        val mappedId = when {
            byId.containsKey(record.articleId) -> record.articleId
            else -> byTitle[record.articleTitle]?.singleOrNull()?.first
        }
        if (mappedId == null) {
            noArticle++
            return@forEach
        }
        val candidate = record.copy(articleId = mappedId)
        val key = "${candidate.articleId}|${candidate.timestamp}|${candidate.mode}|" +
            "${candidate.totalBlanks}|${candidate.correctCount}"
        if (!seen.add(key)) {
            duplicate++
            return@forEach
        }
        ready += candidate
    }
    return ImportPlan(ready, noArticle, duplicate, parsed.badRows)
}

private fun existingKey(record: PracticeRecord): String =
    "${record.articleId}|${record.timestamp}|${record.mode}|${record.totalBlanks}|${record.correctCount}"

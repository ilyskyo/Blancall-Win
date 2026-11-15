package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.MistakeNotebook
import com.ilyskyo.blancall.data.model.toErrorType
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings

/**
 * 独立错题本：统计页里只显示前 40 条，这里支持全量、按文章筛选、只看未攻克。
 * 聚合与「是否攻克」的判定完全复用 [MistakeNotebook]，不另算一套口径。
 */
@Composable
fun MistakesScreen(state: AppState, initialArticleId: Long? = null) {
    val records by state.records.records.collectAsState(initial = emptyList())
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    var articleId by remember(initialArticleId) { mutableStateOf(initialArticleId ?: 0L) }
    var onlyUnresolved by remember { mutableStateOf(true) }
    val titles = remember(articles) { articles.associate { it.id to it.title } }
    val entries = remember(records, articleId) {
        MistakeNotebook.entries(
            records = records,
            articleId = articleId.takeIf { it > 0 },
            onlyUnresolved = false,
        )
    }
    val shown = remember(entries, onlyUnresolved) {
        if (onlyUnresolved) entries.filter { !it.resolved } else entries
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().touchScrollable(listState, scope),
            state = listState,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 1000.dp)) {
                    BlancallTopBar(
                        title = "错题本",
                        subtitle = Strings.f(
                            "共 {0} 处 · 未攻克 {1} 处",
                            entries.size,
                            entries.count { !it.resolved },
                        ),
                        onBack = { state.back() },
                        actions = {
                            GlassButton(onClick = { state.navigate(Screen.PracticeHistory(articleId.takeIf { it > 0 })) }) {
                                Text("练习历史", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
            }
            item {
                GlassCard(
                    Modifier.fillMaxWidth().widthIn(max = 1000.dp),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("文章", style = MaterialTheme.typography.labelSmall)
                        GlassDropdown(
                            options = buildList {
                                add(0L to "全部文章")
                                articles.sortedByDescending { it.updatedAt }.forEach {
                                    add(it.id to it.title.ifBlank { "未命名" })
                                }
                            },
                            selected = articleId,
                            onSelect = { articleId = it },
                            modifier = Modifier.widthIn(max = 280.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        SettingsRow("只看未攻克") {
                            GlassSwitch(checked = onlyUnresolved, onCheckedChange = { onlyUnresolved = it })
                        }
                    }
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 1000.dp),
                    title = if (onlyUnresolved) "未攻克错题" else "全部错题",
                    subtitle = "同一句 + 同一个答案视为同一处；旧记录无法判断是否攻克时会明示",
                ) {
                    if (shown.isEmpty()) {
                        EmptyState(
                            if (entries.isEmpty()) "还没有错题" else "没有未攻克的错题",
                            if (entries.isEmpty()) "答错的内容会自动收进这里，并按句子聚合。" else "继续保持。",
                        )
                    } else {
                        shown.forEach { entry ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        Strings.f("应填「{0}」", entry.correctAnswer),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        Strings.f(
                                            "{0} · 错 {1}/{2} 次",
                                            mistakeErrorLabel(entry.lastErrorType),
                                            entry.wrongCount,
                                            entry.attempts,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (entry.lastUserAnswer.isNotBlank()) {
                                    Text(
                                        Strings.f("你写「{0}」", entry.lastUserAnswer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (entry.sentenceText.isNotBlank()) {
                                    Text(
                                        entry.sentenceText.trim().let { if (it.length > 120) it.take(120) + "…" else it },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        when {
                                            !entry.exact -> "旧记录 · 无法判断是否攻克"
                                            entry.resolved -> "已攻克"
                                            else -> "未攻克"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            !entry.exact -> MaterialTheme.colorScheme.onSurfaceVariant
                                            entry.resolved -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                    )
                                    Text(
                                        Strings.f("《{0}》", titles[entry.articleId].orEmpty()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (entry.articleId > 0) {
                                        GlassButton(onClick = { state.navigate(Screen.Reader(entry.articleId)) }) {
                                            Text("回到原篇", style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        PrimaryPill("集训", onClick = { state.navigate(Screen.Drill(entry.articleId)) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        DesktopScrollbar(
            listState,
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(12.dp, 12.dp, 4.dp, 12.dp),
        )
    }
}

private fun mistakeErrorLabel(wire: String): String = when (wire.toErrorType()) {
    com.ilyskyo.blancall.data.model.ErrorType.TYPO -> Strings.t("错别字")
    com.ilyskyo.blancall.data.model.ErrorType.GLYPH_CONFUSED -> Strings.t("形近字")
    com.ilyskyo.blancall.data.model.ErrorType.MISSING -> Strings.t("漏字")
    com.ilyskyo.blancall.data.model.ErrorType.EXTRA -> Strings.t("多字")
    com.ilyskyo.blancall.data.model.ErrorType.WRONG_ORDER -> Strings.t("顺序错")
    com.ilyskyo.blancall.data.model.ErrorType.INCORRECT -> Strings.t("内容错")
    com.ilyskyo.blancall.data.model.ErrorType.OTHER -> Strings.t("其他")
}

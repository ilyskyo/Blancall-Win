package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import kotlinx.coroutines.launch

@Composable
fun ArticlesScreen(state: AppState, focusSearch: Boolean = false) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val tagData by state.tags.data.collectAsState()
    val revision by state.fsrs.revision.collectAsState()
    val pausedIds = remember(revision) { state.fsrs.pausedArticleIds() }
    var query by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<Article?>(null) }
    var tagging by remember { mutableStateOf<Article?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    if (focusSearch) {
        androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    val filtered = articles
        .filter { query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true) }
        .filter { article ->
            selectedTags.isEmpty() ||
                tagData.links[article.id].orEmpty().any { it in selectedTags }
        }
        .sortedByDescending { it.updatedAt }

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 1100.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("我的文章", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedTags.isNotEmpty()) {
                        Strings.f(
                            "共 {0} 篇 · 搜索标题或正文 · 已筛选 {1} 篇",
                            articles.size,
                            filtered.size,
                        )
                    } else {
                        Strings.f("共 {0} 篇 · 搜索标题或正文", articles.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectMode) {
                    Button(
                        enabled = selected.size >= 2,
                        onClick = { state.navigate(Screen.CrossPractice(selected.toList())) },
                    ) { Text(Strings.f("跨文练习（{0}）", selected.size)) }
                    TextButton(onClick = {
                        selectMode = false
                        selected = emptySet()
                    }) { Text("取消多选") }
                } else {
                    OutlinedButton(onClick = { selectMode = true }) { Text("多选") }
                    Button(onClick = { state.navigate(Screen.Import) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("导入")
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().widthIn(max = 1100.dp).focusRequester(focusRequester),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索标题 / 正文") },
        )

        if (tagData.tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().widthIn(max = 1100.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "标签筛选",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tagData.tags.forEach { tag ->
                    val selected = tag.id in selectedTags
                    if (selected) {
                        Button(onClick = { selectedTags = selectedTags - tag.id }) {
                            Text(tag.name)
                        }
                    } else {
                        TextButton(onClick = { selectedTags = selectedTags + tag.id }) {
                            Text(tag.name)
                        }
                    }
                }
                if (selectedTags.isNotEmpty()) {
                    TextButton(onClick = { selectedTags = emptySet() }) { Text("清除") }
                }
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(
                if (articles.isEmpty()) "还没有文章" else "没有匹配的文章",
                if (articles.isEmpty()) "导入第一篇，开始挖空背诵。" else "换个关键词试试。",
            ) {
                if (articles.isEmpty()) {
                    Button(onClick = { state.navigate(Screen.Import) }) { Text("导入文章") }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().widthIn(max = 1100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { article ->
                    SectionCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selectMode) {
                                        Checkbox(
                                            checked = article.id in selected,
                                            onCheckedChange = { checked ->
                                                selected = if (checked) selected + article.id else selected - article.id
                                            },
                                        )
                                    }
                                    Text(
                                        article.title.ifBlank { "未命名" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(
                                    listOfNotNull(
                                        article.author.takeIf { it.isNotBlank() },
                                        Strings.f("{0} 字", article.content.length),
                                        Strings.f("更新于 {0}", formatTime(article.updatedAt)),
                                        if (article.id in pausedIds) "已暂停复习" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    article.content.take(90).replace('\n', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val articleTags = tagData.tags.filter {
                                    it.id in tagData.links[article.id].orEmpty()
                                }
                                if (articleTags.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        articleTags.forEach { tag ->
                                            Text(
                                                "#${tag.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { state.navigate(Screen.Reader(article.id)) }) { Text("阅读") }
                                TextButton(onClick = { state.navigate(Screen.Practice(article.id)) }) { Text("练习") }
                                TextButton(onClick = { state.navigate(Screen.Statistics(article.id)) }) { Text("统计") }
                                TextButton(onClick = { tagging = article }) { Text("标签") }
                                if (article.id in pausedIds) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            state.fsrs.resumeArticle(article.id)
                                            state.showToast("已恢复复习")
                                        }
                                    }) { Text("恢复复习") }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            state.fsrs.pauseArticle(article.id)
                                            state.showToast("已暂停复习")
                                        }
                                    }) { Text("暂停复习") }
                                }
                                IconButton(onClick = { pendingDelete = article }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { article ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除文章") },
            text = {
                Text(
                    Strings.f(
                        "确定删除《{0}》？练习记录会保留，但文章正文不可恢复。",
                        article.title.ifBlank { Strings.t("未命名") },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        state.articles.delete(article)
                        pendingDelete = null
                        state.showToast("已删除")
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    tagging?.let { article ->
        TagPickerDialog(state, article) { tagging = null }
    }
}

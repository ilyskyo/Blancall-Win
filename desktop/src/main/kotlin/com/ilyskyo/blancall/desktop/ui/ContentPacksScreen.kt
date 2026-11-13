package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.TextNormalizer
import com.ilyskyo.blancall.data.contentpack.ContentPackStore
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内容包管理：导入 zip（若干 txt/md + 可选 manifest.json），整包或逐篇导入为文章。
 *
 * 安全边界由 shared 的 [ContentPackStore] 负责（路径穿越/炸弹/上限）；这里只做展示与选择。
 */
@Composable
fun ContentPacksScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf<List<ContentPackStore.PackMeta>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var expandedPack by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ContentPackStore.PackMeta?>(null) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun refresh() {
        withContext(Dispatchers.IO) { packs = state.contentPacks.listPacks() }
        loaded = true
    }

    LaunchedEffect(Unit) { refresh() }

    fun importZip(file: File) {
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    state.contentPacks.import(file.inputStream(), file.nameWithoutExtension)
                }
                when (result) {
                    is ContentPackStore.ImportResult.Success -> {
                        refresh()
                        state.showToast(Strings.f("已导入内容包「{0}」", result.pack.name))
                    }
                    is ContentPackStore.ImportResult.Failure ->
                        state.showToast(Strings.f("导入失败：{0}", reasonText(result.reason)))
                }
            } catch (e: Exception) {
                state.showToast(Strings.f("导入失败：{0}", e.message))
            } finally {
                busy = false
            }
        }
    }

    fun importEntries(pack: ContentPackStore.PackMeta, indices: List<Int>) {
        scope.launch {
            busy = true
            try {
                val inserted = withContext(Dispatchers.IO) {
                    val articles = indices.mapNotNull { index ->
                        val entry = pack.entries.getOrNull(index) ?: return@mapNotNull null
                        val body = state.contentPacks.readEntry(pack, index) ?: return@mapNotNull null
                        val normalized = TextNormalizer.normalize(body)
                        if (normalized.isBlank()) return@mapNotNull null
                        Article(
                            title = entry.title.ifBlank { "未命名" },
                            author = entry.author,
                            content = normalized,
                            autoIndent = true,
                        )
                    }
                    if (articles.isEmpty()) emptyList() else state.articles.insertAll(articles)
                }
                if (inserted.isEmpty()) {
                    state.showToast("没有可导入的正文")
                } else {
                    state.showToast(Strings.f("已导入 {0} 篇文章", inserted.size))
                    state.navigate(com.ilyskyo.blancall.desktop.app.Screen.Articles)
                }
            } catch (e: Exception) {
                state.showToast(Strings.f("导入失败：{0}", e.message))
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().widthIn(max = 1000.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text("内容包", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "导入 zip（txt/md，可带 manifest.json）后按篇加入文章库；原 zip 不落盘，正文会复制到数据目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        val file = FileDialogs.open("导入内容包", listOf(".zip")) ?: return@Button
                        importZip(file)
                    },
                ) { Text(if (busy) "处理中…" else "导入内容包") }
            }
        }

        item {
            SectionCard(title = "已安装内容包", subtitle = Strings.f("共 {0} 个", packs.size)) {
                if (!loaded) {
                    Text("正在读取…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (packs.isEmpty()) {
                    EmptyState(
                        "还没有内容包",
                        "把若干 txt/md 打包成 zip 后导入；也可以不带 manifest，文件名会作为标题。",
                    )
                } else {
                    packs.forEach { pack ->
                        PackRow(
                            pack = pack,
                            expanded = expandedPack == pack.id,
                            busy = busy,
                            onToggle = {
                                expandedPack = if (expandedPack == pack.id) null else pack.id
                            },
                            onImportAll = { importEntries(pack, pack.entries.indices.toList()) },
                            onImportOne = { index -> importEntries(pack, listOf(index)) },
                            onDelete = { pendingDelete = pack },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.padding(bottom = 32.dp)) }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除内容包") },
            text = {
                Text(Strings.f("删除「{0}」及其正文缓存？已导入的文章不受影响。", deleting.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { state.contentPacks.deletePack(deleting.id) }
                        pendingDelete = null
                        refresh()
                        state.showToast("已删除内容包")
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PackRow(
    pack: ContentPackStore.PackMeta,
    expanded: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onImportAll: () -> Unit,
    onImportOne: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(pack.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    Strings.f("{0} 篇 · 导入于 {1}", pack.entries.size, formatTime(pack.importedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onToggle) { Text(if (expanded) "收起" else "查看篇目") }
                OutlinedButton(enabled = !busy, onClick = onImportAll) { Text("全部导入") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
        if (expanded) {
            pack.entries.forEachIndexed { index, entry ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.title.ifBlank { "未命名" }, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(
                                entry.author.takeIf { it.isNotBlank() },
                                Strings.f("{0} 字", entry.chars),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(enabled = !busy, onClick = { onImportOne(index) }) { Text("导入") }
                }
            }
        }
    }
}

private fun reasonText(reason: ContentPackStore.Reason): String = when (reason) {
    ContentPackStore.Reason.EMPTY -> "包里没有可导入的 txt/md"
    ContentPackStore.Reason.BAD_PATH -> "包含非法路径（拒绝 .. 或绝对路径）"
    ContentPackStore.Reason.TOO_MANY_ENTRIES -> Strings.f("条目过多（上限 {0}）", ContentPackStore.MAX_ENTRIES)
    ContentPackStore.Reason.TOO_LARGE -> "体积超限（单篇 2MB / 整包 20MB）"
    ContentPackStore.Reason.BAD_MANIFEST -> "manifest.json 格式不正确"
    ContentPackStore.Reason.ENCRYPTED -> "不支持加密 zip"
    ContentPackStore.Reason.READ_FAILED -> "读取失败，文件可能已损坏"
}

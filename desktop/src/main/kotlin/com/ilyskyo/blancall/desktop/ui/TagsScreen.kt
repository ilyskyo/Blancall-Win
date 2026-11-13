package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.data.model.Tag
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PRESET_COLORS = listOf(0xEE9DB4, 0xF6C177, 0x9CC5A1, 0x8AB6D6, 0xB39DDB, 0xD98E8E)

@Composable
fun TagsScreen(state: AppState) {
    val tagData by state.tags.data.collectAsState()
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(PRESET_COLORS[0]) }
    var renaming by remember { mutableStateOf<Tag?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { state.tags.snapshot() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 1000.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { state.back() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("文章标签", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "标签用于筛选文章与组织复习；绑定关系与 Android 的 tags.json 完全一致。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(modifier = Modifier.widthIn(max = 1000.dp), title = "新建标签") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("标签名") },
                )
                PRESET_COLORS.forEach { c ->
                    val selected = newColor == c
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(Color(0xFF000000L or c.toLong()), CircleShape)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                            .clickable { newColor = c },
                    )
                }
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val id = withContext(Dispatchers.IO) { state.tags.createTag(newName, newColor) }
                            if (id == null) {
                                state.showToast("标签名不能为空或与已有标签重名")
                            } else {
                                newName = ""
                                state.showToast("已创建标签")
                            }
                        }
                    },
                ) { Text("新建") }
            }
        }

        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "标签列表",
            subtitle = Strings.f("共 {0} 个", tagData.tags.size),
        ) {
            if (tagData.tags.isEmpty()) {
                EmptyState("还没有标签", "创建标签后，可在文章列表里给文章打标签。")
            } else {
                tagData.tags.forEach { tag ->
                    val count = tagData.links.count { (_, ids) -> tag.id in ids }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { state.navigate(Screen.TagHome(tag.id)) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(14.dp).background(Color(0xFF000000L or tag.color.toLong()), CircleShape))
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            Strings.f("{0} 篇", count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            renaming = tag
                            renameText = tag.name
                        }) { Text("重命名") }
                        IconButton(onClick = { deleting = tag }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.padding(bottom = 32.dp))
    }

    renaming?.let { tag ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名标签") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("标签名") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { state.tags.renameTag(tag.id, renameText) }
                        if (!ok) state.showToast("名称不能为空或与已有标签重名")
                        else renaming = null
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }

    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除标签") },
            text = {
                Text(Strings.f("删除「{0}」会解除它与所有文章的绑定，文章本身不受影响。", tag.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { state.tags.deleteTag(tag.id) }
                        deleting = null
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

/**
 * 标签主页：把某个标签下的文章、到期情况与练习入口收在一处。
 * 与文章列表的标签筛选共用 `tags.json` 绑定关系，不另存状态。
 */
@Composable
fun TagHomeScreen(state: AppState, tagId: Long) {
    val tagData by state.tags.data.collectAsState()
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val revision by state.fsrs.revision.collectAsState()
    val tag = tagData.tags.firstOrNull { it.id == tagId }
    val tagged = articles
        .filter { tagId in tagData.links[it.id].orEmpty() }
        .sortedByDescending { it.updatedAt }
    val states = remember(revision, articles) { state.fsrs.allStates() }
    val paused = remember(revision) { state.fsrs.pausedArticleIds() }
    val dueCount = tagged.count { article ->
        article.id !in paused && states[article.id]?.let { FsrsEngine.isDue(it) } == true
    }

    if (tag == null) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("标签不存在或已被删除", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { state.navigate(Screen.Tags) }) { Text("返回标签列表") }
        }
        return
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Box(Modifier.size(16.dp).background(Color(0xFF000000L or tag.color.toLong()), CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(tag.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        Strings.f("{0} 篇", tagged.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { state.navigate(Screen.Tags) }) { Text("管理标签") }
            }
        }

        item {
            SectionCard(
                modifier = Modifier.widthIn(max = 1000.dp),
                title = "标签概览",
                subtitle = "从文章列表给文章打标签后，这里会汇总文章与练习入口。",
            ) {
                KeyValueRow("文章", Strings.f("{0} 篇", tagged.size))
                KeyValueRow("到期文章", Strings.f("{0} 篇", dueCount))
                KeyValueRow("已暂停复习", Strings.f("{0} 篇", tagged.count { it.id in paused }))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        tagged.size >= 2 -> Button(
                            onClick = { state.navigate(Screen.CrossPractice(tagged.map { it.id })) },
                        ) { Text("跨文练习") }
                        tagged.size == 1 -> Button(
                            onClick = { state.navigate(Screen.Practice(tagged.first().id)) },
                        ) { Text("开始练习") }
                    }
                    OutlinedButton(onClick = { state.navigate(Screen.SentenceCards(tagId = tag.id)) }) {
                        Text("句子卡")
                    }
                    OutlinedButton(onClick = { state.navigate(Screen.Statistics()) }) { Text("查看数据") }
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.widthIn(max = 1000.dp), title = "标签文章") {
                if (tagged.isEmpty()) {
                    Text(
                        "这个标签下还没有文章。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(tagged, key = { it.id }) { article ->
            SectionCard(modifier = Modifier.widthIn(max = 1000.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(article.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append("${article.content.length} 字")
                                if (article.author.isNotBlank()) append(" · ${article.author}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { state.navigate(Screen.Reader(article.id)) }) { Text("阅读") }
                    Button(onClick = { state.navigate(Screen.Practice(article.id)) }) { Text("练习") }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 32.dp)) }
    }
}

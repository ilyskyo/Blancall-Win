package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
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
    var menuFor by remember { mutableStateOf<Article?>(null) }
    var searchOpen by remember { mutableStateOf(focusSearch) }
    var selectedArticleId by remember { mutableLongStateOf(-1L) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState()
    val wideListState = rememberLazyListState()
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (focusSearch) {
        LaunchedEffect(Unit) {
            searchOpen = true
            searchFocus.requestFocus()
        }
    }

    val filtered = articles
        .filter { query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true) }
        .filter { article ->
            selectedTags.isEmpty() ||
                tagData.links[article.id].orEmpty().any { it in selectedTags }
        }
        .sortedByDescending { it.updatedAt }

    val headerActions: @Composable RowScope.() -> Unit = {
        GlassButton(
            onClick = {
                searchOpen = !searchOpen
                if (searchOpen) searchFocus.requestFocus() else query = ""
            },
            modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        if (selectMode) {
            GlassButton(onClick = {
                selectMode = false
                selected = emptySet()
            }) { Text("取消多选", style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.width(8.dp))
            PrimaryPill(
                Strings.f("跨文练习（{0}）", selected.size),
                enabled = selected.size >= 2,
                onClick = { state.navigate(Screen.CrossPractice(selected.toList())) },
            )
        } else {
            GlassButton(onClick = { selectMode = true }) {
                Text("多选", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(8.dp))
            GlassButton(onClick = { state.navigate(Screen.Import) }) {
                Text("导入", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // ARCHITECTURE §8.2：≥1100dp 列表与详情并排；窗口更窄时仍进入独立详情页。
        val twoPane = maxWidth >= 1100.dp
        LaunchedEffect(twoPane, filtered) {
            if (twoPane && filtered.none { it.id == selectedArticleId }) {
                selectedArticleId = filtered.firstOrNull()?.id ?: -1L
            }
        }
        // 选中 id 可能来自上一帧/上一份筛选结果；右栏以当前筛选列表为准，
        // 避免「列表里明明有这篇文章，详情却显示不存在」。
        val effectiveId = filtered.firstOrNull { it.id == selectedArticleId }?.id
            ?: filtered.firstOrNull()?.id
            ?: -1L
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(400.dp).fillMaxHeight().padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(10.dp))
                    RootPageHeader(title = "我的文章", actions = headerActions)
                    Spacer(Modifier.height(12.dp))
                    if (searchOpen || query.isNotBlank()) {
                        SearchInput(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "搜索标题 / 正文",
                            focusRequester = if (focusSearch) searchFocus else null,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (tagData.tags.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TagChip(
                                text = "全部",
                                selected = selectedTags.isEmpty(),
                                onClick = { selectedTags = emptySet() },
                            )
                            tagData.tags.forEach { tag ->
                                TagChip(
                                    text = "#${tag.name}",
                                    selected = tag.id in selectedTags,
                                    onClick = {
                                        selectedTags = if (tag.id in selectedTags) {
                                            selectedTags - tag.id
                                        } else {
                                            selectedTags + tag.id
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (filtered.isEmpty()) {
                        EmptyState(
                            if (articles.isEmpty()) "还没有文章" else "没有匹配的文章",
                            if (articles.isEmpty()) "导入第一篇，开始挖空背诵。" else "换个关键词试试。",
                        ) {
                            if (articles.isEmpty()) {
                                PrimaryPill("导入文章", onClick = { state.navigate(Screen.Import) })
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().touchScrollable(wideListState, scope),
                            state = wideListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                        ) {
                            items(filtered, key = { it.id }) { article ->
                                WideArticleRow(
                                    article = article,
                                    selected = article.id == effectiveId,
                                    paused = article.id in pausedIds,
                                    tags = tagData.tags.filter {
                                        it.id in tagData.links[article.id].orEmpty()
                                    },
                                    onClick = {
                                        if (selectMode) {
                                            selected = if (article.id in selected) {
                                                selected - article.id
                                            } else {
                                                selected + article.id
                                            }
                                        } else {
                                            selectedArticleId = article.id
                                        }
                                    },
                                    onMore = { menuFor = article },
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier.width(1.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val detailArticle = filtered.firstOrNull { it.id == effectiveId }
                    if (detailArticle != null) {
                        ArticleDetailScreen(
                            state,
                            detailArticle.id,
                            embedded = true,
                            articleOverride = detailArticle,
                        )
                    } else {
                        EmptyState("选择一篇文章", "左侧点选文章后，这里显示详情与阅读入口。")
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(10.dp))
                RootPageHeader(title = "我的文章", actions = headerActions)
                Spacer(Modifier.height(14.dp))
                if (searchOpen || query.isNotBlank()) {
                    SearchInput(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索标题 / 正文",
                        focusRequester = if (focusSearch) searchFocus else null,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (tagData.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TagChip(
                            text = "全部",
                            selected = selectedTags.isEmpty(),
                            onClick = { selectedTags = emptySet() },
                        )
                        tagData.tags.forEach { tag ->
                            TagChip(
                                text = "#${tag.name}",
                                selected = tag.id in selectedTags,
                                onClick = {
                                    selectedTags = if (tag.id in selectedTags) {
                                        selectedTags - tag.id
                                    } else {
                                        selectedTags + tag.id
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    EmptyState(
                        if (articles.isEmpty()) "还没有文章" else "没有匹配的文章",
                        if (articles.isEmpty()) "导入第一篇，开始挖空背诵。" else "换个关键词试试。",
                    ) {
                        if (articles.isEmpty()) {
                            PrimaryPill("导入文章", onClick = { state.navigate(Screen.Import) })
                        }
                    }
                } else {
                    val gridColumns = if (LocalIsLargeScreen.current) 2 else 1
                    LazyVerticalStaggeredGrid(
                        state = gridState,
                        columns = StaggeredGridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize().touchScrollable(gridState, scope),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    ) {
                        items(filtered, key = { it.id }) { article ->
                            ArticleCard(
                                article = article,
                                paused = article.id in pausedIds,
                                tags = tagData.tags.filter { it.id in tagData.links[article.id].orEmpty() },
                                selectMode = selectMode,
                                selected = article.id in selected,
                                onSelectToggle = {
                                    selected = if (article.id in selected) selected - article.id else selected + article.id
                                },
                                onOpen = { state.navigate(Screen.Reader(article.id)) },
                                onPractice = { state.navigate(Screen.Practice(article.id)) },
                                onMore = { menuFor = article },
                                onLongStartSelect = {
                                    selectMode = true
                                    selected = selected + article.id
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { article ->
        ArticleActionsDialog(
            state = state,
            article = article,
            paused = article.id in pausedIds,
            onDismiss = { menuFor = null },
            onTag = { tagging = article; menuFor = null },
            onDelete = { pendingDelete = article; menuFor = null },
        )
    }

    pendingDelete?.let { article ->
        GlassConfirmDialog(
            title = "删除文章",
            message = Strings.f(
                "确定删除《{0}》？练习记录会保留，但文章正文不可恢复。",
                article.title.ifBlank { Strings.t("未命名") },
            ),
            confirmText = "删除",
            onConfirm = {
                scope.launch {
                    try {
                        state.articles.delete(article)
                        if (selectedArticleId == article.id) selectedArticleId = -1L
                        pendingDelete = null
                        state.showToast("已删除")
                    } catch (e: Exception) {
                        state.reportStorageError("删除文章", e)
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    tagging?.let { article ->
        TagPickerDialog(state, article) { tagging = null }
    }
}

@Composable
private fun WideArticleRow(
    article: Article,
    selected: Boolean,
    paused: Boolean,
    tags: List<com.ilyskyo.blancall.data.model.Tag>,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().onSecondaryClick(onMore),
        onClick = onClick,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    article.title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (paused) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "已暂停",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                listOfNotNull(
                    tags.firstOrNull()?.let { "#${it.name}" },
                    Strings.f("{0} 字", article.content.length),
                    formatDay(article.updatedAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArticleCard(
    article: Article,
    paused: Boolean,
    tags: List<com.ilyskyo.blancall.data.model.Tag>,
    selectMode: Boolean,
    selected: Boolean,
    onSelectToggle: () -> Unit,
    onOpen: () -> Unit,
    onPractice: () -> Unit,
    onMore: () -> Unit,
    onLongStartSelect: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.onSecondaryClick(onMore),
        onClick = if (selectMode) onSelectToggle else onOpen,
        onLongClick = if (selectMode) null else onLongStartSelect,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        if (tags.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.take(2).forEach { tag ->
                    TagChip(text = "#${tag.name}", compact = true)
                }
                if (tags.size > 2) {
                    Text(
                        "+${tags.size - 2}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectMode) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 标题不截断：长标题换行撑高卡片（与 Android 同一硬性要求）
                    Text(
                        article.title.ifBlank { "未命名" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (paused) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "已暂停",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        article.author.takeIf { it.isNotBlank() },
                        Strings.f("{0} 字", article.content.length),
                        formatTime(if (article.createdAt > 0) article.createdAt else article.updatedAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            // 与 Android 一致：练习是卡片右侧垂直居中的唯一主操作；
            // 更多操作（标签/暂停/删除）改由桌面右键承接，卡面不再放「更多」按钮。
            PrimaryPill("练习", onClick = onPractice)
        }
    }
}

@Composable
private fun ArticleActionsDialog(
    state: AppState,
    article: Article,
    paused: Boolean,
    onDismiss: () -> Unit,
    onTag: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(containerColor = MaterialTheme.colorScheme.surface) {
            Text(article.title.ifBlank { "未命名" }, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            MenuAction("查看统计") { onDismiss(); state.navigate(Screen.Statistics(article.id)) }
            MenuAction("管理标签") { onTag() }
            MenuAction(if (paused) "恢复复习" else "暂停复习") {
                scope.launch {
                    if (paused) state.fsrs.resumeArticle(article.id) else state.fsrs.pauseArticle(article.id)
                    state.showToast(if (paused) "已恢复复习" else "已暂停复习")
                }
                onDismiss()
            }
            MenuAction("删除文章", destructive = true) { onDelete() }
        }
    }
}

@Composable
private fun MenuAction(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun GlassConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(containerColor = MaterialTheme.colorScheme.surface) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GlassButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.labelMedium) }
                Spacer(Modifier.width(8.dp))
                PrimaryPill(confirmText, onClick = onConfirm)
            }
        }
    }
}

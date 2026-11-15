package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.algorithm.BlancallGenerator
import com.ilyskyo.blancall.algorithm.ReaderOcclusion
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import com.ilyskyo.blancall.desktop.platform.ImportedFonts
import com.ilyskyo.blancall.desktop.platform.PdfExporter
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import com.ilyskyo.blancall.desktop.ui.theme.uiFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文章详情页：计划/标签/正文预览 + 四个动作入口。
 *
 * 阅读正文本身在 [ReadingModeScreen]；这里不再把「阅读」和「设置」塞在同一页。
 */
@Composable
fun ArticleDetailScreen(
    state: AppState,
    articleId: Long,
    embedded: Boolean = false,
    articleOverride: Article? = null,
) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val article = articleOverride ?: articles.firstOrNull { it.id == articleId }

    if (article == null) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("文章不存在或已被删除", style = MaterialTheme.typography.titleLarge)
            GlassButton(onClick = { state.navigate(Screen.Articles) }) { Text("返回文章列表") }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val fsrsRevision by state.fsrs.revision.collectAsState()
    val inPlan = remember(article.id, fsrsRevision) { state.fsrs.isInPlan(article.id) }
    val records by state.records.records.collectAsState()
    val articleRecords = remember(records, article.id) { records.filter { it.articleId == article.id } }
    val practiceCount = articleRecords.size
    val totalBlanks = articleRecords.sumOf { it.totalBlanks }
    val correctBlanks = articleRecords.sumOf { it.correctCount }
    val accuracy = if (totalBlanks == 0) null else correctBlanks.toFloat() / totalBlanks

    val tagData by state.tags.data.collectAsState()
    val myTags = remember(tagData.links, tagData.tags, article.id) {
        val ids = tagData.links[article.id].orEmpty()
        tagData.tags.filter { it.id in ids }
    }
    var showTagPicker by remember(article.id) { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var editing by remember(article.id) { mutableStateOf(false) }
    var editTitle by remember(article.id) { mutableStateOf(article.title) }
    var editAuthor by remember(article.id) { mutableStateOf(article.author) }
    var editContent by remember(article.id) { mutableStateOf(article.content) }

    val readerPrefs = remember(article.id) { runCatching { state.readerPrefs.get(article.id) }.getOrNull() }
    val fontId = readerPrefs?.fontId?.ifBlank { "sans" } ?: "sans"
    val fontPx = readerPrefs?.fontPx ?: state.prefs.readerFontPx
    val lineHeight = readerPrefs?.lineHeight ?: state.prefs.readerLineHeight
    val fontWeight = androidx.compose.ui.text.font.FontWeight(
        (readerPrefs?.fontWeight ?: 400).coerceIn(100, 900),
    )
    val indent = state.prefs.paragraphIndent && article.autoIndent
    val paragraphs = remember(article.content) { ReaderOcclusion.splitParagraphs(article.content) }
    val fontFamily = ImportedFonts.resolve(fontId, readerPrefs?.fontWeight ?: 400)

    fun copyContent() {
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                java.awt.datatransfer.StringSelection(article.content),
                null,
            )
            state.showToast("正文已复制到剪贴板")
        }.onFailure { state.showToast(Strings.f("复制失败：{0}", it.message)) }
    }

    fun saveEdit() {
        if (editContent.isBlank()) return
        scope.launch {
            try {
                state.articles.update(
                    article.copy(
                        title = editTitle.trim(),
                        author = editAuthor.trim(),
                        content = editContent.trim(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                editing = false
                state.showToast("已保存")
            } catch (e: Exception) {
                state.reportStorageError("保存文章", e)
            }
        }
    }

    DisposableEffect(editing) {
        if (!editing) return@DisposableEffect onDispose { }
        val remove = state.pushSaveHandler {
            saveEdit()
            true
        }
        onDispose(remove)
    }

    fun exportPdf() {
        val target = FileDialogs.save("导出 PDF", "${article.title.ifBlank { "未命名" }}.pdf") ?: return
        scope.launch {
            try {
                val cloze = runCatching {
                    BlancallGenerator.generateSentenceCloze(article.content, count = 0)
                }.getOrNull()
                withContext(Dispatchers.IO) {
                    PdfExporter.export(
                        PdfExporter.Request(
                            title = article.title,
                            author = article.author,
                            body = cloze?.displayText ?: article.content,
                            answers = cloze?.blanks?.map { it.originalText }.orEmpty(),
                        ),
                        target,
                    )
                }
                state.showToast(Strings.f("PDF 已导出：{0}", target.name))
            } catch (e: Exception) {
                state.showToast(Strings.f("导出失败：{0}", e.message))
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .then(if (embedded) Modifier.fillMaxWidth() else Modifier.widthIn(max = ReadingMaxWidth))
                .fillMaxHeight(),
        ) {
            BlancallTopBar(
                title = article.title.ifBlank { "未命名" },
                subtitle = listOfNotNull(
                    article.author.takeIf { it.isNotBlank() },
                    Strings.f("{0} 字", article.content.length),
                ).joinToString(" · "),
                onBack = if (embedded) null else ({ state.back() }),
                actions = {
                    val moreSrc = remember { MutableInteractionSource() }
                    Box {
                        DesktopTooltip("更多") {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .desktopHover(moreSrc, CircleShape)
                                    .clickable(
                                        interactionSource = moreSrc,
                                        indication = null,
                                    ) { showMore = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = "更多",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showMore,
                            onDismissRequest = { showMore = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑文章", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    showMore = false
                                    editTitle = article.title
                                    editAuthor = article.author
                                    editContent = article.content
                                    editing = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("复制全文", style = MaterialTheme.typography.bodyMedium) },
                                onClick = { showMore = false; copyContent() },
                            )
                            DropdownMenuItem(
                                text = { Text("导出 PDF", style = MaterialTheme.typography.bodyMedium) },
                                onClick = { showMore = false; exportPdf() },
                            )
                            DropdownMenuItem(
                                text = { Text("学习统计", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    showMore = false
                                    state.navigate(Screen.Statistics(article.id))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("自定义挖空", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    showMore = false
                                    state.navigate(Screen.CustomClozeList(article.id))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("遮挡配置", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    showMore = false
                                    state.navigate(Screen.MaskConfigList(article.id))
                                },
                            )
                        }
                    }
                },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .touchScrollable(scroll, scope)
                    .verticalScroll(scroll),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = ReadingMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (inPlan) "已在计划" else "尚未开始",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            val meta = buildList {
                                add(Strings.f("{0} 次练习", practiceCount))
                                accuracy?.let { add(Strings.f("正确率 {0}%", (it * 100).toInt())) }
                            }.joinToString(" · ")
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!inPlan) {
                            GlassButton(onClick = {
                                scope.launch {
                                    val added = withContext(Dispatchers.IO) { state.fsrs.enroll(article.id) }
                                    state.showToast(if (added) "已加入今日计划" else "已在计划中")
                                }
                            }) {
                                Text("加入计划", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (myTags.isEmpty()) {
                            Text(
                                "尚未添加",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            )
                        } else {
                            myTags.take(4).forEach { tag ->
                                TagChip(text = "#${tag.name}", compact = true)
                            }
                            if (myTags.size > 4) {
                                Text(
                                    "+${myTags.size - 4}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        GlassButton(onClick = { showTagPicker = true }) {
                            Text("管理标签", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    GlassCard(contentPadding = PaddingValues(18.dp)) {
                        paragraphs.forEachIndexed { index, para ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            Text(
                                text = para.text,
                                fontSize = fontPx.sp,
                                lineHeight = (fontPx * lineHeight).sp,
                                fontFamily = fontFamily,
                                fontWeight = fontWeight,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textIndent = if (indent) TextIndent(firstLine = (fontPx * 2f).sp) else TextIndent(),
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(84.dp))
                }
            }
            DesktopScrollbar(
                scroll,
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp, end = 4.dp),
            )
            }
        }

        DetailActionBar(
            onStats = { state.navigate(Screen.Statistics(article.id)) },
            onReadingMode = { state.navigate(Screen.ReadingMode(article.id)) },
            onSentenceCards = { state.navigate(Screen.SentenceCards(articleId = article.id)) },
            onPractice = { state.navigate(Screen.Practice(article.id)) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }

    if (showTagPicker) {
        TagPickerDialog(state, article) { showTagPicker = false }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("编辑文章") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editAuthor,
                        onValueChange = { editAuthor = it },
                        label = { Text("作者") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("正文") },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editContent.isNotBlank(),
                    onClick = { saveEdit() },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("取消") }
            },
        )
    }
}

/** 详情页底部动作条：与 Android 详情页的四项入口一致。 */
@Composable
private fun DetailActionBar(
    onStats: () -> Unit,
    onReadingMode: () -> Unit,
    onSentenceCards: () -> Unit,
    onPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = SmoothRoundedCornerShape(28.dp)
    Row(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(56.dp)
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailBarAction("学习统计", onStats)
        DetailBarAction("阅读模式", onReadingMode)
        DetailBarAction("句子卡", onSentenceCards)
        DetailBarAction("开始练习", onPractice, primary = true)
    }
}

@Composable
private fun DetailBarAction(text: String, onClick: () -> Unit, primary: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(CircleShape)
            .desktopHover(src, CircleShape)
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

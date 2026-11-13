package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.algorithm.BlancallGenerator
import com.ilyskyo.blancall.algorithm.ContentFingerprint
import com.ilyskyo.blancall.algorithm.ReaderOcclusion
import com.ilyskyo.blancall.algorithm.ReadingSpeed
import com.ilyskyo.blancall.algorithm.SentenceSplitter
import com.ilyskyo.blancall.data.repository.CustomClozeStore
import com.ilyskyo.blancall.data.repository.MaskConfigStore
import com.ilyskyo.blancall.data.repository.ReaderPrefs
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import com.ilyskyo.blancall.desktop.platform.PdfExporter
import com.ilyskyo.blancall.desktop.platform.ReadingTimeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

@Composable
fun ReaderScreen(state: AppState, articleId: Long) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val article = articles.firstOrNull { it.id == articleId }

    if (article == null) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("文章不存在或已被删除", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { state.navigate(Screen.Articles) }) { Text("返回文章列表") }
        }
        return
    }

    LaunchedEffect(article.id) {
        state.prefs.updateLastArticleId(article.id)
        state.prefs.pushRecentArticle(article.id)
    }
    val scope = rememberCoroutineScope()
    var editing by remember(article.id) { mutableStateOf(false) }
    var editTitle by remember(article.id) { mutableStateOf(article.title) }
    var editAuthor by remember(article.id) { mutableStateOf(article.author) }
    var editContent by remember(article.id) { mutableStateOf(article.content) }
    val fontPx = state.prefs.readerFontPx
    val lineHeight = state.prefs.readerLineHeight
    val indent = state.prefs.paragraphIndent && article.autoIndent
    val scroll = rememberScrollState()

    val savedPrefs = remember(article.id) { runCatching { state.readerPrefs.get(article.id) }.getOrNull() }
    var occlusionEnabled by remember(article.id) {
        mutableStateOf(savedPrefs?.occlusionEnabled ?: false)
    }
    var occlusionMode by remember(article.id) {
        mutableStateOf(savedPrefs?.occlusionMode?.ifBlank { "long" } ?: "long")
    }
    var revealAll by remember(article.id) { mutableStateOf(false) }

    fun persistReaderPrefs() {
        runCatching {
            val base = savedPrefs
            state.readerPrefs.save(
                article.id,
                ReaderPrefs(
                    bgMode = base?.bgMode ?: 1,
                    fontId = base?.fontId ?: "serif",
                    fontWeight = base?.fontWeight ?: 400,
                    fontPx = fontPx,
                    lineHeight = lineHeight,
                    layoutMode = base?.layoutMode ?: 0,
                    occlusionEnabled = occlusionEnabled,
                    occlusionMode = occlusionMode,
                    occlusionColor = base?.occlusionColor ?: 0,
                    occlusionCustomConfigId = base?.occlusionCustomConfigId ?: 0L,
                ),
            )
        }
    }

    // 阅读时长：每 20 秒落一次盘；离开页面补上最后一段（Android 的口径是 20s 落盘）
    var readSeconds by remember(article.id) { mutableLongStateOf(ReadingTimeStore.get(article.id)) }
    var flushAt by remember(article.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(article.id) {
        while (true) {
            delay(20_000)
            val now = System.currentTimeMillis()
            val delta = (now - flushAt) / 1000
            if (delta > 0) {
                ReadingTimeStore.add(article.id, delta)
                readSeconds += delta
            }
            flushAt = now
        }
    }
    DisposableEffect(article.id) {
        onDispose {
            val delta = (System.currentTimeMillis() - flushAt) / 1000
            if (delta > 0) {
                ReadingTimeStore.add(article.id, delta)
                readSeconds += delta
            }
        }
    }

    val paragraphs = remember(article.content) { ReaderOcclusion.splitParagraphs(article.content) }
    val marksRevision by state.readingMarks.revisionFlow.collectAsState()
    val marks = remember(article.id, marksRevision) {
        runCatching { state.readingMarks.getMarks(article.id) }.getOrDefault(emptyList())
    }
    LaunchedEffect(article.id, article.updatedAt) {
        val changed = withContext(Dispatchers.IO) {
            runCatching {
                state.readingMarks.checkContentHash(article.id, ContentFingerprint.md5Hex(article.content))
            }.getOrDefault(false)
        }
        if (changed) state.showToast("正文已修改，旧划线/批注的位置可能已偏移")
    }
    val customSpans = remember(article.id, occlusionEnabled) {
        if (!occlusionEnabled) emptyMap()
        else runCatching {
            val selectedId = state.maskConfig.getSelected(article.id)
            val config = if (selectedId > 0) state.maskConfig.getConfig(article.id, selectedId) else null
            config?.spans?.groupBy { it.p }?.mapValues { (_, spans) ->
                spans.map { ReaderOcclusion.Span(it.a, it.e) }
            }.orEmpty()
        }.getOrDefault(emptyMap())
    }
    val standardChars = remember(article.content) { ReadingSpeed.standardChars(article.content) }
    val progress = if (scroll.maxValue <= 0) 0f else scroll.value.toFloat() / scroll.maxValue
    val remainMinutes = ReadingSpeed.displayMinutes(
        ReadingSpeed.remainingSeconds(standardChars * (1f - progress), ReadingSpeed.DefaultCpm)
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { state.back() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    article.title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    listOfNotNull(
                        article.author.takeIf { it.isNotBlank() },
                        Strings.f("{0} 字", article.content.length),
                        Strings.f("已读 {0} 分 {1} 秒", readSeconds / 60, readSeconds % 60),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Strings.f("字号 {0}", fontPx.toInt()), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { state.prefs.updateReaderFontPx(fontPx - 1f) }) { Text("A-") }
            OutlinedButton(onClick = { state.prefs.updateReaderFontPx(fontPx + 1f) }) { Text("A+") }
            if (occlusionEnabled) {
                listOf("short" to "短遮挡", "long" to "长遮挡", "mixed" to "混合").forEach { (mode, label) ->
                    if (occlusionMode == mode) {
                        Button(onClick = {
                            occlusionMode = mode
                            revealAll = false
                            persistReaderPrefs()
                        }) { Text(label) }
                    } else {
                        TextButton(onClick = {
                            occlusionMode = mode
                            revealAll = false
                            persistReaderPrefs()
                        }) { Text(label) }
                    }
                }
                TextButton(onClick = { revealAll = !revealAll }) {
                    Text(if (revealAll) "重新遮挡" else "全部显示")
                }
            }
            if (occlusionEnabled) {
                Button(onClick = {
                    occlusionEnabled = false
                    persistReaderPrefs()
                }) { Text("关闭遮挡") }
            } else {
                OutlinedButton(onClick = {
                    occlusionEnabled = true
                    persistReaderPrefs()
                }) { Text("背诵遮挡") }
            }
            TextButton(onClick = { state.navigate(Screen.Statistics(article.id)) }) { Text("统计") }
            TextButton(onClick = {
                editTitle = article.title
                editAuthor = article.author
                editContent = article.content
                editing = true
            }) { Text("编辑") }
            TextButton(onClick = {
                runCatching {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        java.awt.datatransfer.StringSelection(article.content),
                        null,
                    )
                    state.showToast("正文已复制到剪贴板")
                }.onFailure { state.showToast(Strings.f("复制失败：{0}", it.message)) }
            }) { Text("复制") }
            TextButton(onClick = {
                val target = FileDialogs.save("导出 PDF", "${article.title.ifBlank { "未命名" }}.pdf") ?: return@TextButton
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
            }) { Text("导出 PDF") }
            Button(onClick = { state.navigate(Screen.Practice(article.id)) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("开始练习")
            }
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy((fontPx * 0.9f).dp),
            ) {
                paragraphs.forEachIndexed { index, para ->
                    val hidden = when {
                        !occlusionEnabled -> emptyList()
                        customSpans.isNotEmpty() -> customSpans[index].orEmpty()
                        else -> ReaderOcclusion.localRangesInPara(para.text, occlusionMode)
                    }
                    if (hidden.isEmpty() || revealAll) {
                        ReadingParagraph(
                            text = para.text,
                            fontPx = fontPx,
                            lineHeight = lineHeight,
                            indent = indent,
                            marks = marks.filter { it.p == index },
                            onAddMark = { start, end, note ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        state.readingMarks.addMark(article.id, index, start, end, note)
                                    }
                                }
                            },
                            onDeleteMark = { markId ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        state.readingMarks.removeMark(article.id, markId)
                                    }
                                }
                            },
                            onEditNote = { markId, note ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        state.readingMarks.updateNote(article.id, markId, note)
                                    }
                                }
                            },
                            onSaveCloze = { a, b ->
                                saveSelectionAsCloze(state, article, paragraphs[index], index, a, b, scope)
                            },
                        )
                    } else {
                        OccludedParagraph(
                            text = para.text,
                            hidden = hidden,
                            fontPx = fontPx,
                            lineHeight = lineHeight,
                            indent = indent,
                            maskColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    if (progress >= 1f) {
                        Strings.f("已读完 · 累计阅读 {0} 分钟", readSeconds / 60)
                    } else {
                        Strings.f(
                            "预计剩余约 {0} 分钟（按 {1} 字/分钟）",
                            remainMinutes,
                            ReadingSpeed.DefaultCpm.toInt(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(bottom = 48.dp))
            }
        }
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
                    onClick = {
                        scope.launch {
                            try {
                                state.articles.update(
                                    article.copy(
                                        title = editTitle.trim(),
                                        author = editAuthor.trim(),
                                        content = editContent.trim(),
                                        updatedAt = System.currentTimeMillis(),
                                    )
                                )
                                editing = false
                                state.showToast("已保存")
                            } catch (e: Exception) {
                                state.showToast(Strings.f("保存失败：{0}", e.message))
                            }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 单段背诵遮挡：原文完整渲染，挡片在 `drawWithContent` 里覆盖在文字之上；
 * 点中挡片揭示，再点遮回。与 Android `OccludedParagraph` 同一交互。
 */
@Composable
private fun OccludedParagraph(
    text: String,
    hidden: List<ReaderOcclusion.Span>,
    fontPx: Float,
    lineHeight: Float,
    indent: Boolean,
    maskColor: Color,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var revealed by remember(text, hidden) { mutableStateOf(setOf<Int>()) }

    Text(
        text = text,
        fontSize = fontPx.sp,
        lineHeight = (fontPx * lineHeight).sp,
        style = MaterialTheme.typography.bodyLarge.copy(
            textIndent = if (indent) TextIndent(firstLine = 2.em) else TextIndent(),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                hidden.filter { it.start !in revealed }.forEach { span ->
                    rangeRects(l, span.start, span.end).forEach { r ->
                        drawRoundRect(
                            color = maskColor,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                }
            }
            .pointerInput(layout, hidden, revealed) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val hit = hidden.firstOrNull { span ->
                        rangeRects(l, span.start, span.end).any { it.contains(pos) }
                    } ?: return@detectTapGestures
                    revealed = if (hit.start in revealed) revealed - hit.start else revealed + hit.start
                }
            },
        onTextLayout = { layout = it },
    )
}

/**
 * 把当前段落里的选区映射成整篇句子坐标，存成一套自定义挖空配置。
 *
 * 段落区间 → 全文区间 → 与全文切句求交，得到 `s`（句索引）+ 句内 `[a,b)`；
 * 与 Android `CustomClozeStore.BlankSpec` 完全同构。
 */
private fun saveSelectionAsCloze(
    state: AppState,
    article: com.ilyskyo.blancall.data.model.Article,
    para: ReaderOcclusion.Para,
    paragraphIndex: Int,
    a: Int,
    b: Int,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val gStart = para.start + a
    val gEnd = para.start + b
    val positioned = SentenceSplitter.splitWithPositions(article.content)
    val specs = positioned.mapIndexedNotNull { index, sentence ->
        val st = maxOf(gStart, sentence.startIndex)
        val en = minOf(gEnd, sentence.endIndex)
        if (en > st) {
            CustomClozeStore.BlankSpec(index, st - sentence.startIndex, en - sentence.startIndex)
        } else null
    }
    if (specs.isEmpty()) {
        state.showToast("选区无法映射到句子")
        return
    }
    val name = "选区挖空 " + java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                state.customCloze.saveConfig(
                    article.id,
                    CustomClozeStore.CustomConfig(
                        id = 0,
                        name = name,
                        createdAt = System.currentTimeMillis(),
                        blanks = specs,
                        mode = "SENTENCE",
                        contentHash = ContentFingerprint.md5Hex(article.content),
                    ),
                )
            }
            state.showToast(
                Strings.f(
                    "已保存自定义挖空（段落 {0}），可在练习 → 自定义挖空中使用",
                    paragraphIndex + 1,
                ),
            )
        }.onFailure { state.showToast(Strings.f("保存挖空失败：{0}", it.message)) }
    }
}

/** [start, end) 在文本布局里占用的逐行矩形（与 Android `rangeRects` 同算法）。 */
private fun rangeRects(layout: TextLayoutResult, start: Int, end: Int): List<Rect> {
    val len = layout.layoutInput.text.length
    if (start >= end || start < 0 || end > len) return emptyList()
    var line = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end - 1)
    val res = mutableListOf<Rect>()
    while (true) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line, visibleEnd = true)
        val cs = maxOf(start, lineStart)
        val ce = minOf(end - 1, lineEnd - 1)
        if (ce >= cs) {
            val left = layout.getBoundingBox(cs).left
            val right = layout.getBoundingBox(ce).right
            var top = layout.getBoundingBox(cs).top
            var bottom = layout.getBoundingBox(cs).bottom
            for (i in cs + 1..ce) {
                val bbox = layout.getBoundingBox(i)
                if (bbox.top < top) top = bbox.top
                if (bbox.bottom > bottom) bottom = bbox.bottom
            }
            val visualInset = (bottom - top) * 0.10f
            res.add(Rect(left, top + visualInset, right, bottom - visualInset))
        }
        line++
        if (line > lastLine) break
    }
    return res
}

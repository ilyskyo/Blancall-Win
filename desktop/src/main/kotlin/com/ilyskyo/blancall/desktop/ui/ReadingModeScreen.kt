package com.ilyskyo.blancall.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ilyskyo.blancall.algorithm.ContentFingerprint
import com.ilyskyo.blancall.algorithm.ReaderOcclusion
import com.ilyskyo.blancall.algorithm.ReadingSpeed
import com.ilyskyo.blancall.algorithm.SectionSplitter
import com.ilyskyo.blancall.algorithm.SentenceSplitter
import com.ilyskyo.blancall.data.repository.CustomClozeStore
import com.ilyskyo.blancall.data.repository.MaskConfigStore
import com.ilyskyo.blancall.data.repository.ReaderPrefs
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import com.ilyskyo.blancall.desktop.platform.ImportedFonts
import com.ilyskyo.blancall.desktop.platform.ReadingTimeStore
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import com.ilyskyo.blancall.desktop.ui.theme.isBlancallDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * 沉浸阅读模式：独立于文章详情页的全屏阅读面。
 *
 * 详情页负责计划/标签/编辑/统计入口；这里只保留正文、进度和阅读设置。
 * 设置用右侧 Inspector（宽窗）或底部面板（窄窗），不再把 Aa 面板塞进正文 Column。
 *
 * @param initialSettingsOpen 供渲染测试/深链直接打开设置；正常入口传 false
 */
@Composable
fun ReadingModeScreen(
    state: AppState,
    articleId: Long,
    initialSettingsOpen: Boolean = false,
) {
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
    val indent = state.prefs.paragraphIndent && article.autoIndent
    val scroll = rememberScrollState()
    val dark = isBlancallDark()

    val savedPrefs = remember(article.id) { runCatching { state.readerPrefs.get(article.id) }.getOrNull() }
    var fontPx by remember(article.id) {
        mutableFloatStateOf(savedPrefs?.fontPx ?: state.prefs.readerFontPx)
    }
    var lineHeight by remember(article.id) {
        mutableFloatStateOf(savedPrefs?.lineHeight ?: state.prefs.readerLineHeight)
    }
    var fontId by remember(article.id) {
        mutableStateOf(savedPrefs?.fontId?.ifBlank { "sans" } ?: "sans")
    }
    var fontWeight by remember(article.id) {
        mutableIntStateOf(savedPrefs?.fontWeight ?: 400)
    }
    var layoutMode by remember(article.id) {
        mutableIntStateOf(savedPrefs?.layoutMode ?: 0)
    }
    var bgMode by remember(article.id) {
        mutableIntStateOf(savedPrefs?.bgMode ?: 0)
    }
    var occlusionEnabled by remember(article.id) {
        mutableStateOf(savedPrefs?.occlusionEnabled ?: false)
    }
    var occlusionMode by remember(article.id) {
        mutableStateOf(savedPrefs?.occlusionMode?.ifBlank { "long" } ?: "long")
    }
    var occlusionColor by remember(article.id) {
        mutableIntStateOf(savedPrefs?.occlusionColor ?: 0)
    }
    var customConfigId by remember(article.id) {
        mutableLongStateOf(savedPrefs?.occlusionCustomConfigId ?: -1L)
    }
    var revealAll by remember(article.id) { mutableStateOf(false) }
    var showPanel by remember(article.id) { mutableStateOf(initialSettingsOpen) }
    var fontOptions by remember(article.id) { mutableStateOf(ImportedFonts.options()) }
    var page by remember(article.id) { mutableIntStateOf(0) }
    var savedProgress by remember(article.id) {
        mutableFloatStateOf((savedPrefs?.progress ?: 0f).coerceIn(0f, 1f))
    }
    var restored by remember(article.id) { mutableStateOf(savedProgress <= 0.001f) }
    val focusRequester = remember { FocusRequester() }

    val fontFamily = remember(fontId, fontWeight) { ImportedFonts.resolve(fontId, fontWeight) }
    val fontWeightValue = FontWeight(fontWeight.coerceIn(100, 900))
    val bgColor = if (dark) {
        MaterialTheme.colorScheme.background
    } else {
        when (bgMode) {
            1 -> Color(0xFFF6F0E5)
            2 -> Color.White
            else -> MaterialTheme.colorScheme.background
        }
    }

    // Esc 只关面板，不退出文章页；Main.kt 的窗口级 Esc 会先问这里。
    DisposableEffect(showPanel) {
        if (!showPanel) return@DisposableEffect onDispose { }
        val remove = state.pushEscapeHandler {
            showPanel = false
            true
        }
        onDispose(remove)
    }

    fun prefsSnapshot(progress: Float): ReaderPrefs = ReaderPrefs(
        bgMode = bgMode,
        fontId = fontId,
        fontWeight = fontWeight,
        fontPx = fontPx,
        lineHeight = lineHeight,
        layoutMode = layoutMode,
        occlusionEnabled = occlusionEnabled,
        occlusionMode = occlusionMode,
        occlusionColor = occlusionColor,
        occlusionCustomConfigId = customConfigId,
        progress = progress.coerceIn(0f, 1f),
    )

    fun persistReaderPrefs(progress: Float = savedProgress) {
        runCatching { state.readerPrefs.save(article.id, prefsSnapshot(progress)) }
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
    val maskRevision by state.maskConfig.revisionFlow.collectAsState()
    val maskConfigs = remember(article.id, maskRevision) {
        runCatching { state.maskConfig.getConfigs(article.id) }.getOrDefault(emptyList())
    }
    val customSpans = remember(article.id, occlusionEnabled, customConfigId, maskRevision) {
        if (!occlusionEnabled) emptyMap()
        else runCatching {
            val selectedId = customConfigId.takeIf { it > 0 }
                ?: state.maskConfig.getSelected(article.id)
            val config = if (selectedId > 0) state.maskConfig.getConfig(article.id, selectedId) else null
            config?.spans?.groupBy { it.p }?.mapValues { (_, spans) ->
                spans.map { ReaderOcclusion.Span(it.a, it.e) }
            }.orEmpty()
        }.getOrDefault(emptyMap())
    }
    val sections = remember(article.content) { SectionSplitter.split(article.content) }
    val sectionParagraphs = remember(sections, paragraphs) {
        sections.map { section ->
            paragraphs.indices.filter { index ->
                val para = paragraphs[index]
                para.start < section.endChar && para.end > section.startChar
            }
        }
    }
    val standardChars = remember(article.content) { ReadingSpeed.standardChars(article.content) }
    val scrollFraction = if (scroll.maxValue <= 0) 1f else scroll.value.toFloat() / scroll.maxValue
    val progress = if (layoutMode == 0) {
        scrollFraction.coerceIn(0f, 1f)
    } else {
        val count = sections.size.coerceAtLeast(1)
        ((page + scrollFraction) / count).coerceIn(0f, 0.999f)
    }
    val remainMinutes = ReadingSpeed.displayMinutes(
        ReadingSpeed.remainingSeconds(standardChars * (1f - progress), ReadingSpeed.DefaultCpm)
    )
    val pageCount = sections.size.coerceAtLeast(1)

    // 断点恢复：滚动模式等首帧 maxValue 出来再滚；翻页模式按比例落到章节。
    LaunchedEffect(article.id, layoutMode, sections.size) {
        if (restored) return@LaunchedEffect
        if (layoutMode == 1) {
            page = (savedProgress * pageCount).toInt().coerceIn(0, pageCount - 1)
            restored = true
        } else {
            repeat(20) {
                if (scroll.maxValue > 0) return@repeat
                delay(50)
            }
            if (savedProgress > 0f && scroll.maxValue > 0) {
                scroll.scrollTo((scroll.maxValue * savedProgress).toInt())
            }
            restored = true
        }
    }
    LaunchedEffect(layoutMode) {
        if (layoutMode == 1) scroll.scrollTo(0)
    }
    LaunchedEffect(progress, restored) {
        if (!restored) return@LaunchedEffect
        delay(900)
        val snapshot = prefsSnapshot(progress)
        savedProgress = progress
        withContext(Dispatchers.IO) { runCatching { state.readerPrefs.save(article.id, snapshot) } }
    }
    LaunchedEffect(
        fontPx, lineHeight, fontId, fontWeight, layoutMode, bgMode,
        occlusionEnabled, occlusionMode, occlusionColor, customConfigId,
    ) {
        delay(450)
        val snapshot = prefsSnapshot(savedProgress)
        withContext(Dispatchers.IO) { runCatching { state.readerPrefs.save(article.id, snapshot) } }
    }
    val latestProgress by rememberUpdatedState(progress)
    DisposableEffect(article.id) {
        onDispose {
            runCatching { state.readerPrefs.save(article.id, prefsSnapshot(latestProgress)) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    @OptIn(ExperimentalComposeUiApi::class)
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.isCtrlPressed && event.key == Key.Equals -> {
                        fontPx = (fontPx + 1f).coerceIn(14f, 36f); true
                    }
                    event.isCtrlPressed && event.key == Key.Minus -> {
                        fontPx = (fontPx - 1f).coerceIn(14f, 36f); true
                    }
                    layoutMode == 1 && event.key == Key.DirectionLeft -> {
                        if (page > 0) page--
                        true
                    }
                    layoutMode == 1 && event.key == Key.DirectionRight -> {
                        if (page < pageCount - 1) page++
                        true
                    }
                    occlusionEnabled && event.key == Key.Spacebar -> {
                        revealAll = !revealAll
                        true
                    }
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                if (!event.keyboardModifiers.isPointerCtrlPressed) return@onPointerEvent
                val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (dy != 0f) {
                    fontPx = (fontPx - dy * 1.2f).coerceIn(14f, 36f)
                    event.changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(Modifier.fillMaxHeight().widthIn(max = ReadingMaxWidth).fillMaxWidth()) {
        BlancallTopBar(
            title = article.title.ifBlank { "未命名" },
            subtitle = listOfNotNull(
                article.author.takeIf { it.isNotBlank() },
                Strings.f("{0} 字", article.content.length),
                Strings.f("已读 {0} 分", readSeconds / 60),
                if (layoutMode == 1) Strings.f("第 {0}/{1} 章", page + 1, pageCount) else null,
            ).joinToString(" · "),
            onBack = { state.back() },
            actions = {
                // 阅读模式的唯一常驻操作是排版设置；编辑/统计/导出都在详情页。
                DesktopTooltip("阅读设置") {
                    GlassButton(onClick = { showPanel = true }) {
                        Text("Aa", style = MaterialTheme.typography.titleSmall)
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
                .background(bgColor)
                .touchScrollable(scroll, scope)
                .verticalScroll(scroll),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy((fontPx * 0.9f).dp),
            ) {
                val visibleIndices = if (layoutMode == 0) {
                    paragraphs.indices.toList()
                } else {
                    sectionParagraphs.getOrNull(page).orEmpty()
                }
                visibleIndices.forEach { index ->
                    val para = paragraphs[index]
                    val hidden = when {
                        !occlusionEnabled -> emptyList()
                        occlusionMode == "custom" || customSpans.isNotEmpty() -> customSpans[index].orEmpty()
                        else -> ReaderOcclusion.localRangesInPara(para.text, occlusionMode)
                    }
                    if (hidden.isEmpty() || revealAll) {
                        ReadingParagraph(
                            text = para.text,
                            fontPx = fontPx,
                            lineHeight = lineHeight,
                            indent = indent,
                            fontFamily = fontFamily,
                            fontWeight = fontWeightValue,
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
                            fontFamily = fontFamily,
                            fontWeight = fontWeightValue,
                            maskColor = readingMaskColors[
                                occlusionColor.coerceIn(0, readingMaskColors.lastIndex)
                            ].copy(alpha = 0.94f),
                        )
                    }
                }
                Spacer(Modifier.padding(bottom = 96.dp))
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
    AnimatedVisibility(
        visible = !showPanel,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(100)),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    ) {
        ReadingProgressPill(
            progress = progress,
            remainMinutes = remainMinutes,
            page = page.takeIf { layoutMode == 1 },
            pageCount = pageCount.takeIf { layoutMode == 1 },
            onPrev = { if (page > 0) page-- },
            onNext = { if (page < pageCount - 1) page++ },
        )
    }

    // 面板必须是根级 overlay：放在正文 Column 里会被 weight 挤压，
    // 遮罩也只能盖住顶栏以下，底部动作条还会画在面板之上（用户截图里的“统计”胶囊漏出）。
    if (showPanel) {
        val practiceFromMask: () -> Unit = {
            val specs = collectMaskSpecs(article.content, paragraphs, occlusionMode, customSpans)
            if (specs.isEmpty()) {
                state.showToast("当前遮挡没有可练习的内容")
            } else {
                scope.launch {
                    val name = Strings.t("遮挡练习") + " " + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    val id = withContext(Dispatchers.IO) {
                        runCatching {
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
                        }.getOrDefault(0L)
                    }
                    val saved = if (id > 0) {
                        withContext(Dispatchers.IO) { state.customCloze.getConfig(article.id, id) }
                    } else null
                    if (saved == null) {
                        state.showToast("保存挖空失败")
                    } else {
                        showPanel = false
                        state.showToast(Strings.f("已生成 {0} 个空，开始练习", specs.size))
                        state.navigate(Screen.PracticeCustom(article.id, id))
                    }
                }
            }
        }
        ReadingSettingsPanel(
            fontPx = fontPx,
            lineHeight = lineHeight,
            fontId = fontId,
            fontWeight = fontWeight,
            layoutMode = layoutMode,
            bgMode = bgMode,
            occlusionEnabled = occlusionEnabled,
            occlusionMode = occlusionMode,
            occlusionColor = occlusionColor,
            revealAll = revealAll,
            fontOptions = fontOptions,
            maskConfigs = maskConfigs,
            selectedMaskId = customConfigId,
            onFontChange = { fontPx = it; state.prefs.updateReaderFontPx(it) },
            onLineHeightChange = { lineHeight = it; state.prefs.updateReaderLineHeight(it) },
            onFontIdChange = {
                fontId = it
                persistReaderPrefs()
            },
            onFontWeightChange = { fontWeight = it },
            onLayoutModeChange = { layoutMode = it },
            onBgModeChange = { bgMode = it },
            onImportFont = {
                val file = FileDialogs.open("导入字体", listOf(".ttf", ".otf")) ?: return@ReadingSettingsPanel
                val entry = ImportedFonts.import(file)
                if (entry == null) {
                    state.showToast("字体无法读取")
                } else {
                    fontOptions = ImportedFonts.options()
                    fontId = entry.id
                    state.showToast(Strings.f("已导入字体「{0}」", entry.name))
                }
            },
            onOcclusionColorChange = { occlusionColor = it },
            onOcclusionToggle = {
                occlusionEnabled = it
                revealAll = false
                persistReaderPrefs()
            },
            onModeChange = {
                occlusionMode = it
                revealAll = false
                persistReaderPrefs()
            },
            onMaskSelect = { id ->
                customConfigId = id
                if (id > 0) {
                    occlusionEnabled = true
                    occlusionMode = "custom"
                    val config = maskConfigs.firstOrNull { it.id == id }
                    if (config?.contentHash != null &&
                        config.contentHash != ContentFingerprint.md5Hex(article.content)
                    ) {
                        state.showToast("正文已修改，这组遮挡的位置可能偏移")
                    }
                }
                revealAll = false
                persistReaderPrefs()
            },
            onManageMasks = {
                showPanel = false
                state.navigate(Screen.MaskConfigList(article.id))
            },
            onPracticeFromMask = practiceFromMask,
            onRevealToggle = { revealAll = !revealAll },
            onDismiss = { showPanel = false },
        )
    }
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
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontWeight: FontWeight = FontWeight.Normal,
    maskColor: Color,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var revealed by remember(text, hidden) { mutableStateOf(setOf<Int>()) }

    Text(
        text = text,
        fontSize = fontPx.sp,
        lineHeight = (fontPx * lineHeight).sp,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        style = MaterialTheme.typography.bodyLarge.copy(
            textIndent = if (indent) TextIndent(firstLine = (fontPx * 2f).sp) else TextIndent(),
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
 * 把当前阅读遮挡转成自定义挖空坐标（`s` 句索引 + 句内 `[a,b)`）。
 *
 * 与渲染共用同一套 span 口径：自定义配置优先，否则用本地算法按 [occlusionMode] 现算。
 * 同句内重叠/相邻的挡片先合并，避免练习里出现重复空。
 */
internal fun collectMaskSpecs(
    content: String,
    paragraphs: List<ReaderOcclusion.Para>,
    occlusionMode: String,
    customSpans: Map<Int, List<ReaderOcclusion.Span>>,
): List<CustomClozeStore.BlankSpec> {
    val positioned = SentenceSplitter.splitWithPositions(content)
    val perSentence = HashMap<Int, MutableList<IntArray>>()
    paragraphs.forEachIndexed { index, para ->
        val hidden = if (occlusionMode == "custom" || customSpans.isNotEmpty()) {
            customSpans[index].orEmpty()
        } else {
            ReaderOcclusion.localRangesInPara(para.text, occlusionMode)
        }
        hidden.forEach { span ->
            val gStart = para.start + span.start
            val gEnd = para.start + span.end
            positioned.forEachIndexed { sentenceIndex, sentence ->
                val start = maxOf(gStart, sentence.startIndex)
                val end = minOf(gEnd, sentence.endIndex)
                if (end > start) {
                    perSentence.getOrPut(sentenceIndex) { mutableListOf() }
                        .add(intArrayOf(start - sentence.startIndex, end - sentence.startIndex))
                }
            }
        }
    }
    return perSentence.toSortedMap().flatMap { (sentenceIndex, ranges) ->
        val merged = mutableListOf<IntArray>()
        ranges.sortedBy { it[0] }.forEach { range ->
            val last = merged.lastOrNull()
            if (last != null && range[0] <= last[1]) {
                last[1] = maxOf(last[1], range[1])
            } else {
                merged.add(range.copyOf())
            }
        }
        merged.map { CustomClozeStore.BlankSpec(sentenceIndex, it[0], it[1]) }
    }
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

/** 阅读模式底部进度：不抢正文注意力，只回答「读到哪、还剩多久」。 */
@Composable
private fun ReadingProgressPill(
    progress: Float,
    remainMinutes: Long,
    page: Int?,
    pageCount: Int?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val shape = SmoothRoundedCornerShape(18.dp)
    val pct = (progress * 100).toInt().coerceIn(0, 100)
    Column(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (page != null) {
                PillNav("‹", enabled = page > 0, onClick = onPrev)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                Strings.f("已读 {0}%", pct),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (page != null && pageCount != null) {
                Text(
                    Strings.f("{0}/{1} 章", page + 1, pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                PillNav("›", enabled = page < pageCount - 1, onClick = onNext)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                Strings.f("剩余约 {0} 分钟", remainMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun PillNav(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.9f else 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private val readingMaskColors = listOf(
    0xFFEE9DB4, 0xFF7FC6A3, 0xFF8FB2E6, 0xFFEFB86E, 0xFFB79BE0, 0xFFB6AFA4,
).map { Color(it.toInt()) }

/**
 * 阅读设置：连续值走滑杆，字族走下拉菜单。
 *
 * 宽窗是右侧 Inspector，窄窗是底部面板；两层都不占用正文 Column 的高度。
 */
@Composable
private fun ReadingSettingsPanel(
    fontPx: Float,
    lineHeight: Float,
    fontId: String,
    fontWeight: Int,
    layoutMode: Int,
    bgMode: Int,
    occlusionEnabled: Boolean,
    occlusionMode: String,
    occlusionColor: Int,
    revealAll: Boolean,
    fontOptions: List<Pair<String, String>>,
    maskConfigs: List<MaskConfigStore.MaskConfig>,
    selectedMaskId: Long,
    onFontChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontIdChange: (String) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onLayoutModeChange: (Int) -> Unit,
    onBgModeChange: (Int) -> Unit,
    onImportFont: () -> Unit,
    onOcclusionColorChange: (Int) -> Unit,
    onOcclusionToggle: (Boolean) -> Unit,
    onModeChange: (String) -> Unit,
    onMaskSelect: (Long) -> Unit,
    onManageMasks: () -> Unit,
    onPracticeFromMask: () -> Unit,
    onRevealToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(10f)) {
        val wide = maxWidth >= 900.dp
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val cardModifier = if (wide) {
            Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .width(380.dp)
                .fillMaxHeight()
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(200.dp))
        }
        GlassCard(
            cardModifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
            shape = SmoothRoundedCornerShape(26.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("阅读设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    GlassButton(onClick = onDismiss) {
                        Text("完成", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("字号", style = MaterialTheme.typography.bodyMedium)
                GlassSlider(
                    value = fontPx,
                    onValueChange = { onFontChange(it.coerceIn(14f, 36f)) },
                    valueRange = 14f..36f,
                    valueLabel = { Strings.f("{0} px", it.toInt()) },
                )
                Spacer(Modifier.height(8.dp))
                Text("行距", style = MaterialTheme.typography.bodyMedium)
                GlassSlider(
                    value = lineHeight,
                    onValueChange = { onLineHeightChange(it.coerceIn(1.2f, 2.6f)) },
                    valueRange = 1.2f..2.6f,
                    valueLabel = { "%.1f".format(it) },
                )
                Spacer(Modifier.height(12.dp))
                SettingsRow("字体") {
                    GlassDropdown(
                        options = fontOptions,
                        selected = fontId,
                        onSelect = onFontIdChange,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassButton(onClick = onImportFont) {
                        Text("导入字体", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "导入 TTF/OTF 后只在本机可用；字体文件不进备份。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("字重", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                SegmentedPills(
                    options = listOf(
                        400 to "常规",
                        500 to "中等",
                        600 to "半粗",
                        700 to "粗体",
                    ),
                    selected = fontWeight,
                    onSelect = onFontWeightChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("布局", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                SegmentedPills(
                    options = listOf(0 to "整篇滚动", 1 to "章节翻页"),
                    selected = layoutMode,
                    onSelect = onLayoutModeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("背景", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                SegmentedPills(
                    options = listOf(0 to "跟随主题", 1 to "米黄", 2 to "纯白"),
                    selected = bgMode,
                    onSelect = onBgModeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsRow("背诵遮挡", "点击挡片露出原文，再点遮回") {
                    GlassSwitch(checked = occlusionEnabled, onCheckedChange = onOcclusionToggle)
                }
                if (occlusionEnabled) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassDropdown(
                            options = listOf(
                                "short" to "短遮挡",
                                "long" to "长遮挡",
                                "mixed" to "混合",
                                "custom" to "自定义配置",
                            ),
                            selected = occlusionMode,
                            onSelect = onModeChange,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(onClick = onRevealToggle) {
                            Text(
                                if (revealAll) "重新遮挡" else "全部显示",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (occlusionMode == "custom") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GlassDropdown(
                                options = buildList {
                                    add(-1L to "未选择")
                                    maskConfigs.forEach { add(it.id to it.name) }
                                },
                                selected = selectedMaskId,
                                onSelect = onMaskSelect,
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(onClick = onManageMasks) {
                                Text("管理配置", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (maskConfigs.isEmpty()) {
                            Text(
                                "还没有自定义遮挡配置；先新建一套，再回到这里选择。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("挡片颜色", style = MaterialTheme.typography.labelSmall)
                        readingMaskColors.forEachIndexed { index, color ->
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (index == occlusionColor) 2.dp else 0.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape,
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onOcclusionColorChange(index) }
                                    .padding(3.dp)
                                    .width(26.dp)
                                    .height(26.dp),
                            )
                        }
                    }
                    // 遮挡不仅用于阅读：当前挡片位置可直接转成一套自定义挖空去练习。
                    GlassButton(
                        onClick = onPracticeFromMask,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("用当前遮挡开始练习", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "在正文里拖动鼠标可划线、写批注；选区也可以保存成自定义挖空。Ctrl+滚轮可缩放字号。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

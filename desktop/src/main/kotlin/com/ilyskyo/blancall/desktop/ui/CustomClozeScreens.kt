package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ilyskyo.blancall.algorithm.ContentFingerprint
import com.ilyskyo.blancall.algorithm.MaskToCloze
import com.ilyskyo.blancall.algorithm.ReaderOcclusion
import com.ilyskyo.blancall.algorithm.SentenceSplitter
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.data.repository.CustomClozeStore
import com.ilyskyo.blancall.data.repository.MaskConfigStore
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val customModes = listOf(
    "SENTENCE" to "句子挖空",
    "WORD" to "字词挖空",
    "REVERSE" to "反向默写",
)

private val customLevels = listOf(
    0 to "整句",
    1 to "词（约 2 字）",
    2 to "单字",
)

@Composable
fun CustomClozeListScreen(state: AppState, articleId: Long) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val article = articles.firstOrNull { it.id == articleId }
    if (article == null) {
        ArticleMissing(state)
        return
    }
    var deleteTarget by remember { mutableStateOf<CustomClozeStore.CustomConfig?>(null) }
    val configs = remember(article.id, deleteTarget) {
        runCatching { state.customCloze.getConfigs(article.id) }.getOrDefault(emptyList())
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val contentHash = remember(article.content) { ContentFingerprint.md5Hex(article.content) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().touchScrollable(listState, scope),
            state = listState,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                    BlancallTopBar(
                        title = "自定义挖空",
                        subtitle = Strings.f("《{0}》 · 共 {1} 套配置", article.title.ifBlank { Strings.t("未命名") }, configs.size),
                        onBack = { state.back() },
                        actions = {
                            PrimaryPill("新建配置", onClick = {
                                state.navigate(Screen.CustomClozeEdit(article.id))
                            })
                        },
                    )
                }
            }
            item {
                Text(
                    "配置按「句子索引 + 句内区间」保存，与 Android 的 custom_cloze.json 同格式；可以在阅读页拖选文字快速生成，也可以在这里精确编辑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp),
                )
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    title = "配置列表",
                    subtitle = "内容指纹失配时会提示，避免在改过的正文上用旧坐标",
                ) {
                    if (configs.isEmpty()) {
                        EmptyState(
                            "还没有自定义挖空",
                            "新建一套配置，或在阅读页拖选文字后保存。",
                        ) {
                            PrimaryPill("新建配置", onClick = { state.navigate(Screen.CustomClozeEdit(article.id)) })
                        }
                    } else {
                        configs.forEach { config ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            config.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            Strings.f(
                                                "{0} 空 · {1} · {2}",
                                                config.blanks.size,
                                                customModeLabel(config.mode),
                                                formatDay(config.createdAt),
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (config.contentHash != null && config.contentHash != contentHash) {
                                            Text(
                                                "正文已修改：保存时按当前内容重新生成坐标前，位置可能偏移",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    PrimaryPill(
                                        "练习",
                                        enabled = config.blanks.isNotEmpty(),
                                        onClick = { state.navigate(Screen.PracticeCustom(article.id, config.id)) },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    GlassButton(onClick = {
                                        state.navigate(Screen.CustomClozeEdit(article.id, config.id))
                                    }) { Text("编辑", style = MaterialTheme.typography.labelMedium) }
                                    Spacer(Modifier.width(8.dp))
                                    GlassButton(onClick = { deleteTarget = config }) {
                                        Text("删除", style = MaterialTheme.typography.labelMedium)
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

    deleteTarget?.let { config ->
        GlassConfirmDialog(
            title = "删除配置",
            message = Strings.f("确定删除「{0}」？已保存的配置不可恢复。", config.name),
            confirmText = "删除",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { state.customCloze.deleteConfig(article.id, config.id) }
                    }
                    deleteTarget = null
                    state.showToast("已删除")
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomClozeEditScreen(state: AppState, articleId: Long, configId: Long) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val article = articles.firstOrNull { it.id == articleId }
    if (article == null) {
        ArticleMissing(state)
        return
    }
    val existing = remember(article.id, configId) {
        if (configId > 0) runCatching { state.customCloze.getConfig(article.id, configId) }.getOrNull() else null
    }
    val sentences = remember(article.content) { SentenceSplitter.split(article.content) }
    var name by remember { mutableStateOf(existing?.name ?: Strings.f("选区挖空 {0}", sentences.size)) }
    var mode by remember { mutableStateOf(existing?.mode ?: "WORD") }
    var level by remember { mutableStateOf(existing?.levels?.firstOrNull() ?: 1) }
    var blanks by remember {
        mutableStateOf<Map<Int, Set<IntRange>>>(
            existing?.blanks.orEmpty()
                .groupBy { it.s }
                .mapValues { (_, list) -> list.map { it.a until it.b }.toSet() },
        )
    }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    fun toggle(sentence: Int, range: IntRange) {
        val current: Set<IntRange> = blanks[sentence].orEmpty()
        val next: Set<IntRange> = if (current.any { it.first == range.first && it.last == range.last }) {
            current.filterNot { it.first == range.first && it.last == range.last }.toSet()
        } else {
            current + setOf(range)
        }
        blanks = if (next.isEmpty()) blanks - sentence else blanks + (sentence to next)
    }

    fun autoFill() {
        val map = mutableMapOf<Int, Set<IntRange>>()
        sentences.forEachIndexed { index, sentence ->
            val units = ReaderOcclusion.editUnits(sentence, level)
            val unit = units.firstOrNull { r -> sentence.substring(r.start, r.end).any { it.isLetterOrDigit() } }
                ?: units.firstOrNull()
            if (unit != null) map[index] = setOf(unit.start until unit.end)
        }
        blanks = map
    }

    fun save() {
        if (saving) return
        val specs = blanks.entries
            .flatMap { (s, ranges) -> ranges.map { CustomClozeStore.BlankSpec(s, it.first, it.last + 1) } }
            .sortedWith(compareBy({ it.s }, { it.a }))
        if (specs.isEmpty()) {
            state.showToast("至少选择一个空")
            return
        }
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    state.customCloze.saveConfig(
                        article.id,
                        CustomClozeStore.CustomConfig(
                            id = existing?.id ?: 0L,
                            name = name.trim().ifBlank { Strings.f("自定义挖空 {0}", formatDay(System.currentTimeMillis())) },
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            blanks = specs,
                            mode = mode,
                            levels = if (mode == "WORD") List(sentences.size) { level } else emptyList(),
                            contentHash = ContentFingerprint.md5Hex(article.content),
                        ),
                    )
                }
                state.showToast("配置已保存")
                state.back()
            } catch (e: Exception) {
                state.reportStorageError("保存自定义挖空", e)
            } finally {
                saving = false
            }
        }
    }

    DisposableEffect(Unit) {
        val remove = state.pushSaveHandler {
            save()
            true
        }
        onDispose(remove)
    }

    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().touchScrollable(listState, scope),
            state = listState,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                    BlancallTopBar(
                        title = if (configId > 0) "编辑自定义挖空" else "新建自定义挖空",
                        subtitle = article.title.ifBlank { Strings.t("未命名") },
                        onBack = { state.back() },
                        actions = {
                            GlassButton(enabled = !saving, onClick = { save() }) {
                                Text(if (saving) "保存中…" else "保存", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
            }
            item {
                GlassCard(
                    Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "配置名称",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("练习模式", style = MaterialTheme.typography.labelSmall)
                            GlassDropdown(
                                options = customModes,
                                selected = mode,
                                onSelect = { mode = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("编辑粒度", style = MaterialTheme.typography.labelSmall)
                            GlassDropdown(
                                options = customLevels,
                                selected = level,
                                onSelect = { level = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassButton(onClick = { autoFill() }) {
                            Text("按当前粒度自动填充", style = MaterialTheme.typography.labelMedium)
                        }
                        GlassButton(onClick = { blanks = emptyMap() }) {
                            Text("清空", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            Strings.f(
                                "已选 {0} 个空 · 共 {1} 句",
                                blanks.values.sumOf { it.size },
                                sentences.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            items(sentences.size) { index ->
                val sentence = sentences[index]
                GlassCard(
                    Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(
                        Strings.f("第 {0} 句", index + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val units = remember(sentence, level) { ReaderOcclusion.editUnits(sentence, level) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        units.forEach { range ->
                            val selected = blanks[index].orEmpty().any {
                                it.first == range.start && it.last == range.end - 1
                            }
                            val text = sentence.substring(range.start, range.end)
                            Box(
                                Modifier
                                    .clip(SmoothRoundedCornerShape(6.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        SmoothRoundedCornerShape(6.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { toggle(index, range.start until range.end) }
                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
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

@Composable
fun MaskConfigListScreen(state: AppState, articleId: Long) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val article = articles.firstOrNull { it.id == articleId }
    if (article == null) {
        ArticleMissing(state)
        return
    }
    var selectedId by remember(article.id) { mutableStateOf(state.maskConfig.getSelected(article.id)) }
    var refresh by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<MaskConfigStoreConfig?>(null) }
    val configs = remember(article.id, refresh) {
        runCatching { state.maskConfig.getConfigs(article.id) }.getOrDefault(emptyList())
    }
    val contentHash = remember(article.content) { ContentFingerprint.md5Hex(article.content) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun useConfig(configId: Long) {
        scope.launch {
            withContext(Dispatchers.IO) { state.maskConfig.setSelected(article.id, configId) }
            selectedId = configId
            state.showToast("已切换为当前遮挡配置")
        }
    }

    fun drill(config: MaskConfigStoreConfig) {
        val sentences = SentenceSplitter.splitWithPositions(article.content)
        val paragraphs = ReaderOcclusion.splitParagraphs(article.content)
        val specs = config.spans.groupBy { it.p }.flatMap { (p, spans) ->
            val para = paragraphs.getOrNull(p) ?: return@flatMap emptyList()
            MaskToCloze.convert(
                paragraphStart = para.start,
                spans = spans.map { it.a until it.e },
                sentences = sentences,
            ).map { CustomClozeStore.BlankSpec(it.sentenceIndex, it.a, it.b) }
        }.distinct()
        if (specs.isEmpty()) {
            state.showToast("这组遮挡无法映射到当前正文")
            return
        }
        scope.launch {
            val name = Strings.f("{0} · 遮挡练", config.name)
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
                }.getOrNull()
            }
            if (id != null) state.navigate(Screen.PracticeCustom(article.id, id))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().touchScrollable(listState, scope),
            state = listState,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
                    BlancallTopBar(
                        title = "遮挡配置",
                        subtitle = Strings.f("《{0}》 · 共 {1} 套", article.title.ifBlank { Strings.t("未命名") }, configs.size),
                        onBack = { state.back() },
                        actions = {
                            PrimaryPill("新建配置", onClick = { state.navigate(Screen.MaskConfigEdit(article.id)) })
                        },
                    )
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 980.dp),
                    title = "配置列表",
                    subtitle = "在阅读设置里把遮挡切到「自定义」后使用选中的配置",
                ) {
                    if (configs.isEmpty()) {
                        EmptyState(
                            "还没有遮挡配置",
                            "新建一套，或在阅读设置里点「管理配置」。",
                        ) {
                            PrimaryPill("新建配置", onClick = { state.navigate(Screen.MaskConfigEdit(article.id)) })
                        }
                    } else {
                        configs.forEach { config ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                config.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            if (config.id == selectedId) {
                                                Spacer(Modifier.width(8.dp))
                                                TagChip("使用中", selected = true)
                                            }
                                        }
                                        Text(
                                            Strings.f("{0} 块 · {1}", config.spans.size, formatDay(config.createdAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (config.contentHash != null && config.contentHash != contentHash) {
                                            Text(
                                                "正文已修改，遮挡位置可能偏移",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    if (config.id != selectedId) {
                                        GlassButton(onClick = { useConfig(config.id) }) {
                                            Text("使用", style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    GlassButton(onClick = { drill(config) }) {
                                        Text("练这一段", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    GlassButton(onClick = { state.navigate(Screen.MaskConfigEdit(article.id, config.id)) }) {
                                        Text("编辑", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    GlassButton(onClick = { deleteTarget = config }) {
                                        Text("删除", style = MaterialTheme.typography.labelMedium)
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

    deleteTarget?.let { config ->
        GlassConfirmDialog(
            title = "删除遮挡配置",
            message = Strings.f("确定删除「{0}」？", config.name),
            confirmText = "删除",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { state.maskConfig.deleteConfig(article.id, config.id) } }
                    if (selectedId == config.id) selectedId = -1L
                    deleteTarget = null
                    refresh++
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

private typealias MaskConfigStoreConfig = com.ilyskyo.blancall.data.repository.MaskConfigStore.MaskConfig

private val maskColors = listOf(
    0xFFEE9DB4, 0xFF7FC6A3, 0xFF8FB2E6, 0xFFEFB86E, 0xFFB79BE0, 0xFFB6AFA4,
).map { androidx.compose.ui.graphics.Color(it.toInt()) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaskConfigEditScreen(state: AppState, articleId: Long, configId: Long) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val article = articles.firstOrNull { it.id == articleId }
    if (article == null) {
        ArticleMissing(state)
        return
    }
    val existing = remember(article.id, configId) {
        if (configId > 0) runCatching { state.maskConfig.getConfig(article.id, configId) }.getOrNull() else null
    }
    val paragraphs = remember(article.content) { ReaderOcclusion.splitParagraphs(article.content) }
    var name by remember { mutableStateOf(existing?.name ?: "遮挡配置") }
    var level by remember { mutableStateOf(1) }
    var colorIndex by remember { mutableStateOf(0) }
    var spans by remember {
        mutableStateOf<Map<Int, Set<Pair<IntRange, Int>>>>(
            existing?.spans.orEmpty()
                .groupBy { it.p }
                .mapValues { (_, list) -> list.map { (it.a until it.e) to it.c }.toSet() },
        )
    }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toggle(paragraph: Int, range: IntRange) {
        val current = spans[paragraph].orEmpty()
        val next = if (current.any { it.first == range }) {
            current.filterNot { it.first == range }.toSet()
        } else {
            current + (range to colorIndex)
        }
        spans = if (next.isEmpty()) spans - paragraph else spans + (paragraph to next)
    }

    fun cycleColor(paragraph: Int, range: IntRange) {
        val current = spans[paragraph].orEmpty()
        val updated = current.map {
            if (it.first == range) it.first to ((it.second + 1) % maskColors.size) else it
        }.toSet()
        spans = spans + (paragraph to updated)
    }

    fun save() {
        if (saving) return
        val specs = spans.entries.flatMap { (p, list) ->
            list.map { MaskConfigStore.MaskSpan(p, it.first.first, it.first.last + 1, it.second) }
        }
        if (specs.isEmpty()) {
            state.showToast("至少遮住一段")
            return
        }
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val id = state.maskConfig.saveConfig(
                        article.id,
                        MaskConfigStore.MaskConfig(
                            id = existing?.id ?: 0L,
                            name = name.trim().ifBlank { "遮挡配置" },
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            spans = specs,
                            contentHash = ContentFingerprint.md5Hex(article.content),
                        ),
                    )
                    state.maskConfig.setSelected(article.id, id)
                }
                state.showToast("遮挡配置已保存")
                state.back()
            } catch (e: Exception) {
                state.reportStorageError("保存遮挡配置", e)
            } finally {
                saving = false
            }
        }
    }

    DisposableEffect(Unit) {
        val remove = state.pushSaveHandler {
            save()
            true
        }
        onDispose(remove)
    }

    val listState = rememberLazyListState()
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
                        title = if (configId > 0) "编辑遮挡配置" else "新建遮挡配置",
                        subtitle = article.title.ifBlank { Strings.t("未命名") },
                        onBack = { state.back() },
                        actions = {
                            GlassButton(enabled = !saving, onClick = { save() }) {
                                Text(if (saving) "保存中…" else "保存", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
            }
            item {
                GlassCard(
                    Modifier.fillMaxWidth().widthIn(max = 1000.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "配置名称",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Column {
                        Text("编辑粒度", style = MaterialTheme.typography.labelSmall)
                        GlassDropdown(
                            options = customLevels,
                            selected = level,
                            onSelect = { level = it },
                            modifier = Modifier.widthIn(min = 200.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("新挡片颜色", style = MaterialTheme.typography.labelSmall)
                        maskColors.forEachIndexed { index, color ->
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (index == colorIndex) 2.dp else 0.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape,
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { colorIndex = index }
                                    .padding(3.dp)
                                    .width(26.dp)
                                    .height(26.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            Strings.f("已遮 {0} 块 · 共 {1} 段", spans.values.sumOf { it.size }, paragraphs.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点字词遮住 / 再点取消；点色块本身可循环换色。保存后会自动设为当前文章的遮挡配置。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(paragraphs.size) { index ->
                val para = paragraphs[index]
                GlassCard(
                    Modifier.fillMaxWidth().widthIn(max = 1000.dp),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(
                        Strings.f("第 {0} 段", index + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val units = remember(para.text, level) { ReaderOcclusion.editUnits(para.text, level) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        units.forEach { range ->
                            val selected = spans[index].orEmpty().firstOrNull { it.first == range }
                            val text = para.text.substring(range.start, range.end)
                            Row(
                                Modifier
                                    .clip(SmoothRoundedCornerShape(6.dp))
                                    .background(
                                        selected?.let { maskColors[it.second.coerceIn(0, maskColors.lastIndex)] }
                                            ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { toggle(index, range.start until range.end) }
                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                if (selected != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier
                                            .width(10.dp)
                                            .height(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { cycleColor(index, range.start until range.end) },
                                    )
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

@Composable
private fun ArticleMissing(state: AppState) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("文章不存在或已被删除", style = MaterialTheme.typography.titleLarge)
        PrimaryPill("返回文章列表", onClick = { state.navigate(Screen.Articles) })
    }
}

internal fun customModeLabel(mode: String): String = when (mode) {
    "SENTENCE" -> "句子挖空"
    "WORD" -> "字词挖空"
    "REVERSE" -> "反向默写"
    else -> mode
}

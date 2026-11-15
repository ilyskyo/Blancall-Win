package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.algorithm.AnswerChecker
import com.ilyskyo.blancall.algorithm.BlancallGenerator
import com.ilyskyo.blancall.algorithm.DictationScorer
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.algorithm.MistakeNotebook
import com.ilyskyo.blancall.algorithm.RangeOps
import com.ilyskyo.blancall.algorithm.SentenceSplitter
import com.ilyskyo.blancall.data.model.AnsweredBlank
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.data.model.MistakeDetail
import com.ilyskyo.blancall.data.repository.CustomClozeStore
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.PracticeSessionStore
import com.ilyskyo.blancall.desktop.platform.TaskbarProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PracticeModeUi(val label: String, val wire: String) {
    SENTENCE("句子挖空", "SENTENCE"),
    WORD("字词挖空", "WORD"),
    DICTATION("反向默写", "REVERSE"),
    CUSTOM("自定义挖空", "CUSTOM"),
}

@Composable
fun PracticeScreen(
    state: AppState,
    articleId: Long,
    initialCustomConfigId: Long? = null,
) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val article = articles.firstOrNull { it.id == articleId }
    var mode by remember(initialCustomConfigId) {
        mutableStateOf(
            if (initialCustomConfigId != null) PracticeModeUi.CUSTOM else PracticeModeUi.SENTENCE,
        )
    }

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

    DisposableEffect(article.id) {
        TaskbarProgress.indeterminate()
        onDispose { TaskbarProgress.clear() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.fillMaxHeight().widthIn(max = PracticeMaxWidth).fillMaxWidth()) {
        BlancallTopBar(
            title = Strings.f("练习 · {0}", article.title.ifBlank { Strings.t("未命名") }),
            subtitle = "填完提交，结果会写入练习记录与 FSRS 排期",
            onBack = { state.back() },
            actions = {
                GlassButton(onClick = { state.navigate(Screen.Reader(article.id)) }) {
                    Text("返回阅读", style = MaterialTheme.typography.labelMedium)
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SegmentedPills(
                options = PracticeModeUi.entries.map { it to it.label },
                selected = mode,
                onSelect = { mode = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))

        key(mode) {
            when (mode) {
                PracticeModeUi.SENTENCE -> SentencePractice(state, article)
                PracticeModeUi.WORD -> WordPractice(state, article)
                PracticeModeUi.DICTATION -> DictationPractice(state, article)
                PracticeModeUi.CUSTOM -> CustomPractice(state, article, initialCustomConfigId)
            }
        }
    }
    }
}

@Composable
private fun SentencePractice(
    state: AppState,
    article: Article,
    customConfig: CustomClozeStore.CustomConfig? = null,
    onExitCustom: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val cloze = remember(article.id, article.content, customConfig?.id) {
        runCatching {
            if (customConfig == null) {
                BlancallGenerator.generateSentenceCloze(article.content, count = 0)
            } else {
                val sentences = SentenceSplitter.split(article.content)
                val bySentence = customConfig.blanks
                    .filter { it.s in sentences.indices }
                    .groupBy { it.s }
                    .mapValues { (s, list) ->
                        RangeOps.normalizeClampedRanges(
                            sentences[s].length,
                            list.map { it.a until it.b },
                        )
                    }
                    .filterValues { it.isNotEmpty() }
                if (bySentence.isEmpty()) null
                else BlancallGenerator.buildCustomSentenceCloze(article.content, bySentence)
            }
        }.getOrNull()
    }
    val blanks = cloze?.blanks.orEmpty()
    val answers = remember { mutableStateMapOf<Int, String>() }
    val undoStack = remember { ArrayDeque<Map<Int, String>>() }
    val redoStack = remember { ArrayDeque<Map<Int, String>>() }
    fun setAnswer(index: Int, value: String) {
        if (answers[index].orEmpty() == value) return
        undoStack.addLast(answers.toMap())
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        answers[index] = value
    }
    fun undoAnswer() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(answers.toMap())
        answers.clear()
        previous.forEach { (k, v) -> answers[k] = v }
    }
    fun redoAnswer() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(answers.toMap())
        answers.clear()
        next.forEach { (k, v) -> answers[k] = v }
    }
    var submitted by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<List<AnswerChecker.CheckDetail>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    var strongUsed by remember { mutableStateOf(0) }
    var weakUsed by remember { mutableIntStateOf(0) }
    var handwritingFor by remember { mutableStateOf<Int?>(null) }
    val autoReveal = remember { mutableStateMapOf<Int, Int>() }
    var lastActivity by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val startedAt = remember(article.id) { System.currentTimeMillis() }
    val saved = remember(article.id) { PracticeSessionStore.load(state.dataDir, article.id) }

    LaunchedEffect(Unit) {
        if (saved?.mode == "SENTENCE") {
            saved.answers.forEach { (index, value) -> answers[index] = value }
            state.showToast("已恢复上次练习进度")
        }
    }
    LaunchedEffect(answers.toMap()) {
        if (!submitted) lastActivity = System.currentTimeMillis()
    }
    // 两级提示的自动档：10 秒无输入就再淡显一个字（与 Android 的练习页同语义）。
    // 自动提示计入弱提示次数，结果页与记录里能看出「这次靠提示完成」。
    LaunchedEffect(submitted, state.prefs.hintsEnabled) {
        if (!state.prefs.hintsEnabled) return@LaunchedEffect
        while (!submitted) {
            delay(1_000)
            if (System.currentTimeMillis() - lastActivity < 10_000) continue
            val target = blanks.firstOrNull { blank ->
                answers[blank.index].orEmpty().isBlank() &&
                    (autoReveal[blank.index] ?: 0) < blank.originalText.length
            } ?: continue
            autoReveal[target.index] = (autoReveal[target.index] ?: 0) + 1
            weakUsed++
            lastActivity = System.currentTimeMillis()
        }
    }
    LaunchedEffect(answers.toMap(), submitted) {
        if (!submitted && answers.isNotEmpty()) {
            delay(700)
            PracticeSessionStore.save(
                state.dataDir,
                article.id,
                "SENTENCE",
                blanks.size,
                answers.toMap(),
                startedAt = startedAt,
            )
        }
    }

    if (cloze == null || blanks.isEmpty()) {
        NoPracticeContent(state, article)
        return
    }

    fun submit() {
        if (submitted || saving) return
        val checks = blanks.map { AnswerChecker.check(it.originalText, answers[it.index].orEmpty()) }
        val correct = checks.count { it.result == AnswerChecker.Result.CORRECT }
        val similarity = checks.map { it.similarity }.average().toFloat()
        val mistakes = checks.mapIndexedNotNull { i, d ->
            if (d.result == AnswerChecker.Result.CORRECT) null else MistakeDetail(
                blankIndex = blanks[i].index,
                correctAnswer = blanks[i].originalText,
                userAnswer = answers[blanks[i].index].orEmpty(),
                errorType = d.result.name,
                sentenceIndex = blanks[i].sentenceIndex,
                similarity = d.similarity,
                sentenceText = cloze.sentences.getOrNull(blanks[i].sentenceIndex).orEmpty(),
                articleId = article.id,
            )
        }
        val answered = blanks.mapIndexed { i, b ->
            AnsweredBlank(
                blankIndex = b.index,
                correctAnswer = b.originalText,
                userAnswer = answers[b.index].orEmpty(),
                errorType = if (checks[i].result == AnswerChecker.Result.CORRECT) "" else checks[i].result.name,
                sentenceIndex = b.sentenceIndex,
                sentenceText = cloze.sentences.getOrNull(b.sentenceIndex).orEmpty(),
                similarity = checks[i].similarity,
                articleId = article.id,
            )
        }
        details = checks
        submitted = true
        saving = true
        scope.launch {
            try {
                val rating = state.savePractice(
                    articleId = article.id,
                    mode = "SENTENCE",
                    totalBlanks = blanks.size,
                    correctCount = correct,
                    similarity = similarity,
                    mistakes = mistakes,
                    answeredBlanks = answered,
                    weakHints = weakUsed,
                    strongHints = strongUsed,
                )
                PracticeSessionStore.clear(state.dataDir, article.id)
                resultText = Strings.f(
                    "{0}/{1} 正确 · 评级 {2}",
                    correct,
                    blanks.size,
                    rating.name,
                )
            } catch (e: Exception) {
                state.showToast(Strings.f("保存失败：{0}", e.message))
            } finally {
                saving = false
            }
        }
    }

    val sentenceListState = rememberLazyListState()
    val leftScroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()

    // 卡片内容抽成局部组合 lambda：宽屏双栏与窄屏单列复用同一份，
    // 避免两套布局各写一遍导致行为分叉。
    val textCard: @Composable () -> Unit = {
        SectionCard(
            modifier = Modifier.fillMaxWidth(),
            title = if (customConfig == null) "句子挖空"
            else Strings.f("自定义挖空 · {0}", customConfig.name),
            subtitle = Strings.f(
                "共 {0} 空 · 已填 {1}",
                blanks.size,
                blanks.count { answers[it.index].orEmpty().isNotBlank() },
            ),
            trailing = if (onExitCustom == null) null else {
                { TextButton(onClick = onExitCustom) { Text("换配置") } }
            },
        ) {
            Text(
                cloze.displayText,
                fontSize = state.prefs.readerFontPx.sp,
                lineHeight = (state.prefs.readerFontPx * state.prefs.readerLineHeight).sp,
            )
        }
    }

    val answerCard: @Composable (Int) -> Unit = { i ->
        val blank = blanks[i]
        val detail = details?.getOrNull(i)
        SectionCard(
            modifier = Modifier.fillMaxWidth(),
            title = Strings.f("第 {0} 空 · 第 {1} 句", i + 1, blank.sentenceIndex + 1),
            subtitle = when {
                detail == null ->
                    if (state.prefs.hintsEnabled) {
                        val auto = autoReveal[blank.index] ?: 0
                        if (auto > 0) {
                            Strings.f(
                                "弱提示：{0} 字 · 已淡显 {1} 字",
                                blank.originalText.length,
                                auto,
                            )
                        } else {
                            Strings.f("弱提示：{0} 字", blank.originalText.length)
                        }
                    } else null
                detail.result == AnswerChecker.Result.CORRECT -> "正确"
                else -> Strings.f("错误：{0}", detail.result.name)
            },
        ) {
            OutlinedTextField(
                value = answers[blank.index].orEmpty(),
                onValueChange = { setAnswer(blank.index, it) },
                modifier = Modifier.fillMaxWidth().onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when {
                        event.isCtrlPressed && event.key == Key.Z -> {
                            undoAnswer(); true
                        }
                        event.isCtrlPressed && event.key == Key.Y -> {
                            redoAnswer(); true
                        }
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                            submit(); true
                        }
                        else -> false
                    }
                },
                enabled = !submitted,
                singleLine = true,
                label = { Text("填写被挖掉的内容") },
            )
            val autoShown = autoReveal[blank.index] ?: 0
            if (autoShown > 0 && !submitted && answers[blank.index].orEmpty().isBlank()) {
                Text(
                    Strings.f("自动淡显：{0}…", blank.originalText.take(autoShown)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!submitted && state.prefs.hintsEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (revealed[blank.index] != true) {
                            revealed[blank.index] = true
                            strongUsed++
                        }
                    }) { Text("强提示：显示首字") }
                    if (revealed[blank.index] == true) {
                        Text(
                            Strings.f("首字：{0}", blank.originalText.firstOrNull() ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (state.handwriting.hasChineseModel) {
                        TextButton(onClick = { handwritingFor = blank.index }) { Text("手写") }
                    }
                }
            }
            if (detail != null) {
                Text(
                    Strings.f("正确答案：{0}", blank.originalText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    val submitRow: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = !submitted && !saving, onClick = { submit() }) {
                Text(if (saving) "保存中…" else "提交答案")
            }
            if (submitted) {
                Button(onClick = { state.navigate(Screen.Practice(article.id)) }) { Text("再练一次") }
                TextButton(onClick = { state.navigate(Screen.Statistics(article.id)) }) { Text("查看统计") }
            }
        }
    }

    val resultCard: @Composable () -> Unit = {
        SectionCard(modifier = Modifier.fillMaxWidth(), title = "本次结果") {
            Text(resultText!!, style = MaterialTheme.typography.titleMedium)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 与 Android 同一双栏判据：内容宽 ≥720dp 且高 ≥480dp；
        // 手机横屏常只满足宽度，仍保持单列，否则作答区会被挤出可视区。
        val wide = twoPaneFits(maxWidth.value, maxHeight.value) && !submitted
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(leftScroll),
                ) {
                    textCard()
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                )
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight().touchScrollable(sentenceListState, scrollScope),
                    state = sentenceListState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(blanks) { i, _ -> answerCard(i) }
                    item { submitRow() }
                    if (resultText != null) item { resultCard() }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().touchScrollable(sentenceListState, scrollScope),
                state = sentenceListState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { textCard() }
                itemsIndexed(blanks) { i, _ -> answerCard(i) }
                item { submitRow() }
                if (resultText != null) {
                    item { resultCard() }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    val handwritingIndex = handwritingFor
    if (handwritingIndex != null && !submitted) {
        val blank = blanks.firstOrNull { it.index == handwritingIndex }
        if (blank != null) {
            HandwritingDialog(
                state = state,
                answer = blank.originalText,
                onPick = { char ->
                    setAnswer(handwritingIndex, answers[handwritingIndex].orEmpty() + char)
                    handwritingFor = null
                },
                onPickText = { text ->
                    setAnswer(handwritingIndex, answers[handwritingIndex].orEmpty() + text)
                    handwritingFor = null
                },
                onDismiss = { handwritingFor = null },
            )
        }
    }
}

@Composable
private fun CustomPractice(
    state: AppState,
    article: Article,
    initialConfigId: Long? = null,
) {
    val configs = remember(article.id) {
        runCatching { state.customCloze.getConfigs(article.id) }.getOrDefault(emptyList())
    }
    var active by remember(article.id, initialConfigId) {
        mutableStateOf(
            initialConfigId?.let { id -> configs.firstOrNull { it.id == id } },
        )
    }
    val current = active
    if (current != null) {
        SentencePractice(
            state = state,
            article = article,
            customConfig = current,
            onExitCustom = { active = null },
        )
        return
    }

    val customListState = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().touchScrollable(customListState, rememberCoroutineScope()),
        state = customListState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SectionCard(
                modifier = Modifier.widthIn(max = 1000.dp),
                title = "自定义挖空",
                subtitle = "选择一套配置开始练习；与 Android 的 custom_cloze.json 同格式",
            ) {
                if (configs.isEmpty()) {
                    Text(
                        "这篇文章还没有自定义挖空配置。可以在 Android 版创建后随备份导入，或在阅读页拖选文字后保存（后续版本提供编辑器）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    configs.forEach { config ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(config.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text(
                                    Strings.f("{0} 空 · 模式 {1}", config.blanks.size, config.mode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = { active = config }) { Text("开始") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordPractice(state: AppState, article: Article) {
    val scope = rememberCoroutineScope()
    val result = remember(article.id, article.content) {
        runCatching { BlancallGenerator.generateWordCloze(article.content, count = 0) }.getOrNull()
    }
    val blanks = result?.blanks.orEmpty()
    val answers = remember { mutableStateMapOf<Int, String>() }
    val undoStack = remember { ArrayDeque<Map<Int, String>>() }
    val redoStack = remember { ArrayDeque<Map<Int, String>>() }
    fun setAnswer(index: Int, value: String) {
        if (answers[index].orEmpty() == value) return
        undoStack.addLast(answers.toMap())
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        answers[index] = value
    }
    fun undoAnswer() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(answers.toMap())
        answers.clear()
        previous.forEach { (k, v) -> answers[k] = v }
    }
    fun redoAnswer() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(answers.toMap())
        answers.clear()
        next.forEach { (k, v) -> answers[k] = v }
    }
    var submitted by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<List<AnswerChecker.CheckDetail>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    var strongUsed by remember { mutableIntStateOf(0) }
    var weakUsed by remember { mutableIntStateOf(0) }
    var handwritingFor by remember { mutableStateOf<Int?>(null) }
    val startedAt = remember(article.id) { System.currentTimeMillis() }
    val saved = remember(article.id) { PracticeSessionStore.load(state.dataDir, article.id) }

    LaunchedEffect(Unit) {
        if (saved?.mode == "WORD") {
            saved.answers.forEach { (index, value) -> answers[index] = value }
            state.showToast("已恢复上次练习进度")
        }
    }
    LaunchedEffect(answers.toMap(), submitted) {
        if (!submitted && answers.isNotEmpty()) {
            delay(700)
            PracticeSessionStore.save(
                state.dataDir,
                article.id,
                "WORD",
                blanks.size,
                answers.toMap(),
                startedAt = startedAt,
            )
        }
    }

    if (result == null || blanks.isEmpty()) {
        NoPracticeContent(state, article)
        return
    }

    fun submit() {
        if (submitted || saving) return
        val checks = blanks.mapIndexed { i, b ->
            AnswerChecker.check(b.originalChar, answers[i].orEmpty())
        }
        val correct = checks.count { it.result == AnswerChecker.Result.CORRECT }
        val similarity = checks.map { it.similarity }.average().toFloat()
        val mistakes = checks.mapIndexedNotNull { i, d ->
            if (d.result == AnswerChecker.Result.CORRECT) null else MistakeDetail(
                blankIndex = i,
                correctAnswer = blanks[i].originalChar,
                userAnswer = answers[i].orEmpty(),
                errorType = d.result.name,
                similarity = d.similarity,
                articleId = article.id,
            )
        }
        val answered = checks.mapIndexed { i, d ->
            AnsweredBlank(
                blankIndex = i,
                correctAnswer = blanks[i].originalChar,
                userAnswer = answers[i].orEmpty(),
                errorType = if (d.result == AnswerChecker.Result.CORRECT) "" else d.result.name,
                similarity = d.similarity,
                articleId = article.id,
            )
        }
        details = checks
        submitted = true
        saving = true
        scope.launch {
            try {
                val rating = state.savePractice(
                    articleId = article.id,
                    mode = "WORD",
                    totalBlanks = blanks.size,
                    correctCount = correct,
                    similarity = similarity,
                    mistakes = mistakes,
                    answeredBlanks = answered,
                    weakHints = weakUsed,
                    strongHints = strongUsed,
                )
                PracticeSessionStore.clear(state.dataDir, article.id)
                resultText = Strings.f(
                    "{0}/{1} 正确 · 评级 {2}",
                    correct,
                    blanks.size,
                    rating.name,
                )
            } catch (e: Exception) {
                state.showToast(Strings.f("保存失败：{0}", e.message))
            } finally {
                saving = false
            }
        }
    }

    val wordListState = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().touchScrollable(wordListState, rememberCoroutineScope()),
        state = wordListState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SectionCard(
                modifier = Modifier.widthIn(max = 1000.dp),
                title = "字词挖空",
                subtitle = Strings.f(
                    "共 {0} 空 · 已填 {1}",
                    blanks.size,
                    blanks.indices.count { answers[it].orEmpty().isNotBlank() },
                ),
            ) {
                Text(
                    result.displayText,
                    fontSize = state.prefs.readerFontPx.sp,
                    lineHeight = (state.prefs.readerFontPx * state.prefs.readerLineHeight).sp,
                )
            }
        }
        item {
            SectionCard(modifier = Modifier.widthIn(max = 1000.dp), title = "填空") {
                blanks.forEachIndexed { i, blank ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(Strings.f("第 {0} 空", i + 1), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                        OutlinedTextField(
                            value = answers[i].orEmpty(),
                            onValueChange = { setAnswer(i, it) },
                            modifier = Modifier.weight(1f).onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when {
                                    event.isCtrlPressed && event.key == Key.Z -> {
                                        undoAnswer(); true
                                    }
                                    event.isCtrlPressed && event.key == Key.Y -> {
                                        redoAnswer(); true
                                    }
                                    event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                                        submit(); true
                                    }
                                    else -> false
                                }
                            },
                            enabled = !submitted,
                            singleLine = true,
                        )
                        if (!submitted && state.prefs.hintsEnabled) {
                            Text(
                                if (revealed[i] == true) {
                                    Strings.f("首字：{0}", blank.originalChar.firstOrNull() ?: "")
                                } else {
                                    Strings.f("{0} 字", blank.originalChar.length)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = {
                                if (revealed[i] != true) {
                                    revealed[i] = true
                                    strongUsed++
                                }
                            }) { Text("提示") }
                            if (state.handwriting.hasChineseModel) {
                                TextButton(onClick = { handwritingFor = i }) { Text("手写") }
                            }
                        }
                        if (details != null) {
                            Text(
                                if (details!![i].result == AnswerChecker.Result.CORRECT) "正确"
                                else Strings.f("应为「{0}」", blank.originalChar),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (details!![i].result == AnswerChecker.Result.CORRECT)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.widthIn(max = 1000.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !submitted && !saving, onClick = { submit() }) {
                    Text(if (saving) "保存中…" else "提交答案")
                }
                if (submitted) {
                    Button(onClick = { state.navigate(Screen.Practice(article.id)) }) { Text("再练一次") }
                    TextButton(onClick = { state.navigate(Screen.Statistics(article.id)) }) { Text("查看统计") }
                }
            }
        }
        if (resultText != null) {
            item {
                SectionCard(modifier = Modifier.widthIn(max = 1000.dp), title = "本次结果") {
                    Text(resultText!!, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    val handwritingIndex = handwritingFor
    if (handwritingIndex != null && !submitted) {
        val blank = blanks.getOrNull(handwritingIndex)
        if (blank != null) {
            HandwritingDialog(
                state = state,
                answer = blank.originalChar,
                onPick = { char ->
                    setAnswer(handwritingIndex, answers[handwritingIndex].orEmpty() + char)
                    handwritingFor = null
                },
                onPickText = { text ->
                    setAnswer(handwritingIndex, answers[handwritingIndex].orEmpty() + text)
                    handwritingFor = null
                },
                onDismiss = { handwritingFor = null },
            )
        }
    }
}

@Composable
private fun DictationPractice(state: AppState, article: Article) {
    val scope = rememberCoroutineScope()
    val dictation = remember(article.id, article.content) {
        runCatching { BlancallGenerator.generateDictation(article.content) }.getOrNull()
    }
    val expected = dictation?.clauses?.joinToString("").orEmpty()
    var input by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var similarity by remember { mutableStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val startedAt = remember(article.id) { System.currentTimeMillis() }
    val saved = remember(article.id) { PracticeSessionStore.load(state.dataDir, article.id) }

    LaunchedEffect(Unit) {
        if (saved?.mode == "REVERSE" && saved.dictationInput.isNotBlank()) {
            input = saved.dictationInput
            state.showToast("已恢复上次默写进度")
        }
    }
    LaunchedEffect(input, submitted) {
        if (!submitted && input.isNotBlank()) {
            delay(700)
            PracticeSessionStore.save(
                state.dataDir,
                article.id,
                "REVERSE",
                1,
                emptyMap(),
                dictationInput = input,
                startedAt = startedAt,
            )
        }
    }

    if (dictation == null || expected.isBlank()) {
        NoPracticeContent(state, article)
        return
    }

    fun submit() {
        if (submitted || saving) return
        val score = DictationScorer.score(expected, input)
        similarity = score
        submitted = true
        saving = true
        val mistake = if (score >= 0.99f) emptyList() else listOf(
            MistakeDetail(
                blankIndex = 0,
                correctAnswer = expected,
                userAnswer = input,
                errorType = if (score < 0.5f) "INCORRECT" else "TYPO",
                similarity = score,
                articleId = article.id,
            )
        )
        val answered = listOf(
            AnsweredBlank(
                blankIndex = 0,
                correctAnswer = expected,
                userAnswer = input,
                errorType = if (score >= 0.99f) "" else mistake.first().errorType,
                similarity = score,
                articleId = article.id,
            )
        )
        scope.launch {
            try {
                val rating = state.savePractice(
                    articleId = article.id,
                    mode = "REVERSE",
                    totalBlanks = 1,
                    correctCount = if (score >= 0.99f) 1 else 0,
                    similarity = score,
                    mistakes = mistake,
                    answeredBlanks = answered,
                )
                PracticeSessionStore.clear(state.dataDir, article.id)
                resultText = Strings.f("相似度 {0} · 评级 {1}", percent(score), rating.name)
            } catch (e: Exception) {
                state.showToast(Strings.f("保存失败：{0}", e.message))
            } finally {
                saving = false
            }
        }
    }

    val dictScroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .touchScrollable(dictScroll, rememberCoroutineScope())
            .verticalScroll(dictScroll)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "反向默写",
            subtitle = "根据打乱的分句线索，默写完整原文（标点与顺序都要对）",
        ) {
            Text(
                dictation.shuffledClauses.joinToString("  /  ") { it.displayText },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().height(260.dp),
                enabled = !submitted,
                label = { Text("默写原文") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !submitted && !saving, onClick = { submit() }) {
                    Text(if (saving) "保存中…" else "提交默写")
                }
                if (submitted) {
                    Button(onClick = { state.navigate(Screen.Practice(article.id)) }) { Text("再练一次") }
                    TextButton(onClick = { state.navigate(Screen.Statistics(article.id)) }) { Text("查看统计") }
                }
            }
            if (submitted) {
                Text("正确答案", style = MaterialTheme.typography.titleSmall)
                Text(expected, style = MaterialTheme.typography.bodyMedium)
                Text(
                    Strings.f("相似度 {0}", percent(similarity)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (resultText != null) {
                Text(resultText!!, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * 错题集训：把错题本里能在当前正文定位的错处收成一次练习。
 * 复用自定义挖空的构造与判分路径（不落盘配置，进程内一次有效）。
 */
@Composable
fun DrillScreen(state: AppState, articleId: Long) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val records by state.records.records.collectAsState(initial = emptyList())
    val article = articles.firstOrNull { it.id == articleId }

    if (article == null) {
        NoPracticeContent(state, Article(title = "", content = ""))
        return
    }
    val entries = remember(article.id, records) {
        MistakeNotebook.entries(records, articleId).filter { !it.resolved }
    }
    val drill = remember(article.content, entries) {
        MistakeNotebook.buildDrill(article.content, articleId, entries)
    }
    if (drill.blanks.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("没有可定位的错题", style = MaterialTheme.typography.titleLarge)
            Text(
                "错题对应的句子可能已被编辑或删除。回到统计页查看错题本。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { state.navigate(Screen.Statistics(articleId)) }) { Text("查看统计") }
        }
        return
    }
    val config = remember(drill) {
        CustomClozeStore.CustomConfig(
            id = -1,
            name = Strings.f("错题集训（{0} 空）", drill.blanks.size),
            createdAt = 0L,
            blanks = drill.blanks.map {
                CustomClozeStore.BlankSpec(it.sentenceIndex, it.start, it.endExclusive)
            },
            mode = "SENTENCE",
        )
    }
    SentencePractice(
        state = state,
        article = article,
        customConfig = config,
        onExitCustom = { state.back() },
    )
}

@Composable
private fun NoPracticeContent(state: AppState, article: Article) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("没有可练习的内容", style = MaterialTheme.typography.titleLarge)
        Text(
            "这篇正文太短或无法切分。返回阅读，换一篇更长或有标点的文章。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { state.navigate(Screen.Reader(article.id)) }) { Text("返回阅读") }
    }
}

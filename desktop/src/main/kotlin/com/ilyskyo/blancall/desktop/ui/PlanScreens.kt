package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ilyskyo.blancall.algorithm.StudyCoach
import com.ilyskyo.blancall.data.model.PracticeRecord
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ── 今日计划 ──

@Composable
fun TodayPlanScreen(state: AppState) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val records by state.records.records.collectAsState(initial = emptyList())
    val revision by state.fsrs.revision.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<StudyCoach.Snapshot?>(null) }
    LaunchedEffect(articles.size, records.size, revision) {
        while (true) {
            withContext(Dispatchers.IO) { snapshot = state.studySnapshot() }
            delay(30_000)
            tick++
        }
    }
    val plan = remember(snapshot) { snapshot?.let { StudyCoach.buildPlan(it.input) } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().touchScrollable(listState, scope),
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                    BlancallTopBar(
                        title = "今日计划",
                        subtitle = LocalDate.now().toString(),
                        onBack = { state.back() },
                        actions = {
                            GlassButton(onClick = { state.navigate(Screen.DueList) }) {
                                Text("待复习清单", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                            GlassButton(onClick = { state.navigate(Screen.PracticeHistory()) }) {
                                Text("练习历史", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlanStat("到期文章", snapshot?.dueArticleCount ?: 0, Modifier.weight(1f))
                    PlanStat("已逾期", snapshot?.overdueArticleCount ?: 0, Modifier.weight(1f))
                    PlanStat("到期句子", snapshot?.sentenceOtherDue ?: 0, Modifier.weight(1f))
                    PlanStat("未攻克错题", snapshot?.mistakeCount ?: 0, Modifier.weight(1f))
                    PlanStat("未完成练习", snapshot?.resumables?.size ?: 0, Modifier.weight(1f))
                }
            }
            item {
                SectionCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    title = "今天做什么",
                    subtitle = if (plan == null) "正在计算…" else Strings.f(
                        "按到期、未完成与薄弱项排好，约 {0} 分钟",
                        plan.estimatedMinutes,
                    ),
                ) {
                    val currentPlan = plan
                    if (currentPlan == null || currentPlan.items.isEmpty()) {
                        EmptyState(
                            "今天没有必须完成的内容",
                            "自由阅读也可以；新导入的文章在第一次练习后进入记忆排期。",
                        ) {
                            PrimaryPill("去导入", onClick = { state.navigate(Screen.Import) })
                        }
                    } else {
                        currentPlan.items.forEach { item ->
                            PlanItemRow(item) { activatePlanItem(state, item) }
                        }
                    }
                }
            }
            item {
                SectionCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    title = "到期文章",
                    subtitle = Strings.f("此刻到期 {0} 篇，其中逾期 {1} 篇", snapshot?.dueArticleCount ?: 0, snapshot?.overdueArticleCount ?: 0),
                ) {
                    val due = snapshot?.input?.dueArticles.orEmpty()
                    if (due.isEmpty()) {
                        Text(
                            "没有到期的文章。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        due.forEach { article ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        article.title.ifBlank { Strings.t("未命名") },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        dueLabel(article.due),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (article.due < startOfToday()) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                GlassButton(onClick = { state.navigate(Screen.Reader(article.articleId)) }) {
                                    Text("阅读", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.width(8.dp))
                                PrimaryPill("复习", onClick = { state.navigate(Screen.Practice(article.articleId)) })
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                    title = "未完成练习",
                    subtitle = "退出练习后会自动保存，下次可以接着填",
                ) {
                    val resumables = snapshot?.resumables.orEmpty()
                    if (resumables.isEmpty()) {
                        Text(
                            "没有未完成的练习。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        resumables.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title.ifBlank { Strings.t("未命名") },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        Strings.f(
                                            "已填 {0}/{1} 空 · {2}",
                                            item.answered,
                                            item.total,
                                            formatTime(item.lastTime),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                PrimaryPill("继续", onClick = { state.navigate(Screen.Practice(item.articleId)) })
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        DesktopScrollbar(
            listState,
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 12.dp, bottom = 12.dp, end = 4.dp),
        )
    }
}

@Composable
private fun PlanStat(label: String, value: Int, modifier: Modifier = Modifier) {
    GlassCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanItemRow(item: StudyCoach.Item, onStart: () -> Unit) {
    val src = remember(item) { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = null, onClick = onStart)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                planItemText(item),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (item.title.isNotBlank()) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            Strings.f("约 {0} 分钟", item.estimatedMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        PrimaryPill("开始", onClick = onStart)
    }
}

internal fun planItemText(item: StudyCoach.Item): String = when (item.kind) {
    StudyCoach.Kind.RESUME -> Strings.f("继续练习 · 还剩 {0} 空", item.count)
    StudyCoach.Kind.REVIEW_ARTICLE -> Strings.f("复习 · {0} 空", item.count)
    StudyCoach.Kind.START_ARTICLE -> Strings.f("开始学习 · {0} 空", item.count)
    StudyCoach.Kind.WEAK_ARTICLE -> Strings.f("薄弱篇 · {0} 处错", item.count)
    StudyCoach.Kind.SENTENCE_CARDS -> Strings.f("句子卡 · {0} 句", item.count)
    StudyCoach.Kind.MISTAKE_DRILL -> Strings.f("错题集训 · {0} 处", item.count)
    StudyCoach.Kind.DAILY_GOAL -> "补足今天的练习次数"
}

internal fun activatePlanItem(state: AppState, item: StudyCoach.Item) {
    when (item.kind) {
        StudyCoach.Kind.RESUME,
        StudyCoach.Kind.REVIEW_ARTICLE,
        StudyCoach.Kind.START_ARTICLE,
        StudyCoach.Kind.WEAK_ARTICLE -> if (item.articleId > 0) state.navigate(Screen.Practice(item.articleId))
        StudyCoach.Kind.SENTENCE_CARDS -> state.navigate(Screen.SentenceCards())
        StudyCoach.Kind.MISTAKE_DRILL -> state.navigate(Screen.Statistics())
        StudyCoach.Kind.DAILY_GOAL -> state.navigate(Screen.Articles)
    }
}

// ── 待复习清单 ──

@Composable
fun DueListScreen(state: AppState) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val records by state.records.records.collectAsState(initial = emptyList())
    val revision by state.fsrs.revision.collectAsState()
    var snapshot by remember { mutableStateOf<StudyCoach.Snapshot?>(null) }
    LaunchedEffect(articles.size, records.size, revision) {
        withContext(Dispatchers.IO) { snapshot = state.studySnapshot() }
    }
    val due = snapshot?.input?.dueArticles.orEmpty()
    val startToday = startOfToday()
    val overdue = due.filter { it.due < startToday }
    val today = due.filter { it.due >= startToday }
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
                Column(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
                    BlancallTopBar(
                        title = "待复习清单",
                        subtitle = Strings.f(
                            "逾期 {0} 篇 · 今天到期 {1} 篇 · 今日稍后 {2} 篇",
                            overdue.size,
                            today.size,
                            snapshot?.laterTodayArticleCount ?: 0,
                        ),
                        onBack = { state.back() },
                    )
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    title = "已逾期",
                    subtitle = "按到期时间从早到晚",
                ) {
                    if (overdue.isEmpty()) {
                        Text("没有逾期文章。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        overdue.forEach { DueArticleRow(state, it, overdue = true) }
                    }
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    title = "今天到期",
                    subtitle = "此刻已到点",
                ) {
                    if (today.isEmpty()) {
                        Text("今天此刻没有新到期的文章。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        today.forEach { DueArticleRow(state, it, overdue = false) }
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
private fun DueArticleRow(state: AppState, article: StudyCoach.DueArticle, overdue: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(CircleShape)
                .background(
                    if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                article.title.ifBlank { Strings.t("未命名") },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(dueLabel(article.due))
                    if (article.reviewCount > 0) {
                        append(" · ")
                        append(Strings.f("复习 {0} 次", article.reviewCount))
                    }
                    if (article.lapses > 0) {
                        append(" · ")
                        append(Strings.f("遗忘 {0} 次", article.lapses))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlassButton(onClick = { state.navigate(Screen.Reader(article.articleId)) }) {
            Text("阅读", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(8.dp))
        PrimaryPill("开始复习", onClick = { state.navigate(Screen.Practice(article.articleId)) })
    }
}

// ── 练习历史 ──

private val historyModes = listOf(
    "" to "全部模式",
    "SENTENCE" to "句子挖空",
    "WORD" to "字词挖空",
    "REVERSE" to "反向默写",
    "CUSTOM" to "自定义挖空",
)

@Composable
fun PracticeHistoryScreen(state: AppState, articleId: Long?) {
    val records by state.records.records.collectAsState(initial = emptyList())
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val titles = remember(articles) { articles.associate { it.id to it.title } }
    var fromDay by remember { mutableStateOf(0L) }
    var toDay by remember { mutableStateOf(0L) }
    var mode by remember { mutableStateOf("") }
    var onlyMistakes by remember { mutableStateOf(false) }
    var crossDetail by remember { mutableStateOf<PracticeRecord?>(null) }
    var selectedArticleId by remember(articleId) { mutableStateOf(articleId ?: 0L) }

    val filtered = remember(records, fromDay, toDay, mode, onlyMistakes, selectedArticleId) {
        records.asSequence()
            .filter { selectedArticleId <= 0L || it.articleId == selectedArticleId }
            .filter { mode.isBlank() || it.mode == mode }
            .filter { !onlyMistakes || it.mistakes.isNotEmpty() }
            .filter { record ->
                val local = Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                (fromDay <= 0L || local >= fromDay) && (toDay <= 0L || local <= toDay)
            }
            .sortedByDescending { it.timestamp }
            .toList()
    }
    val totalBlanks = filtered.sumOf { it.totalBlanks }
    val correct = filtered.sumOf { it.correctCount }
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
                        title = "练习历史",
                        subtitle = Strings.f(
                            "筛选后 {0} 条 · 正确率 {1}",
                            filtered.size,
                            if (totalBlanks == 0) "—" else percent(correct.toFloat() / totalBlanks),
                        ),
                        onBack = { state.back() },
                        actions = {
                            GlassButton(onClick = { state.navigate(Screen.Mistakes(selectedArticleId.takeIf { it > 0 })) }) {
                                Text("错题本", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                            GlassButton(onClick = { state.navigate(Screen.DataInterchange) }) {
                                Text("数据互换", style = MaterialTheme.typography.labelMedium)
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
                                articles.sortedByDescending { it.updatedAt }.forEach { add(it.id to it.title.ifBlank { "未命名" }) }
                            },
                            selected = selectedArticleId,
                            onSelect = { selectedArticleId = it },
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                        Text("模式", style = MaterialTheme.typography.labelSmall)
                        GlassDropdown(
                            options = historyModes,
                            selected = mode,
                            onSelect = { mode = it },
                            modifier = Modifier.widthIn(max = 150.dp),
                        )
                        SettingsRow(
                            "只看有错题的记录",
                            modifier = Modifier.weight(1f),
                        ) {
                            GlassSwitch(checked = onlyMistakes, onCheckedChange = { onlyMistakes = it })
                        }
                        GlassButton(onClick = {
                            fromDay = 0L
                            toDay = 0L
                            mode = ""
                            onlyMistakes = false
                            selectedArticleId = articleId ?: 0L
                        }) { Text("清除筛选", style = MaterialTheme.typography.labelMedium) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("从", style = MaterialTheme.typography.labelSmall)
                        GlassDateField(epochDay = fromDay, onSelect = { fromDay = it })
                        Text("到", style = MaterialTheme.typography.labelSmall)
                        GlassDateField(epochDay = toDay, onSelect = { toDay = it })
                    }
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 1000.dp),
                    title = "记录",
                    subtitle = "点击记录查看单篇统计；跨文练习列出来源文章",
                ) {
                    if (filtered.isEmpty()) {
                        EmptyState("没有匹配的练习记录", "调整筛选条件，或先完成一次练习。")
                    } else {
                        var previousDay: LocalDate? = null
                        filtered.forEach { record ->
                            val time = Instant.ofEpochMilli(record.timestamp)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            if (time != previousDay) {
                                previousDay = time
                                Text(
                                    time.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                            HistoryRow(
                                record = record,
                                title = titles[record.articleId].orEmpty(),
                                onClick = {
                                    if (record.articleId > 0) {
                                        state.navigate(Screen.Statistics(record.articleId))
                                    } else {
                                        crossDetail = record
                                    }
                                },
                            )
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

    crossDetail?.let { record ->
        val sources = record.sourceArticleIds.mapNotNull { id -> articles.firstOrNull { it.id == id } }
        GlassConfirmDialog(
            title = "跨文练习来源",
            message = if (sources.isEmpty()) "来源文章已删除。" else sources.joinToString("\n") { it.title.ifBlank { "未命名" } },
            confirmText = "关闭",
            onConfirm = { crossDetail = null },
            onDismiss = { crossDetail = null },
        )
    }
}

@Composable
private fun HistoryRow(record: PracticeRecord, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember(record.id) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (record.articleId > 0) title.ifBlank { Strings.t("未命名") } else "跨文混合练习",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(historyModeLabel(record.mode))
                    append(" · ")
                    append(formatTime(record.timestamp))
                    if (record.duration > 0) {
                        append(" · ")
                        append(Strings.f("{0} 秒", record.duration / 1000))
                    }
                    if (record.mistakes.isNotEmpty()) {
                        append(" · ")
                        append(Strings.f("{0} 处错", record.mistakes.size))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Strings.f("{0}/{1}", record.correctCount, record.totalBlanks),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (record.totalBlanks <= 0) "—" else percent(record.correctCount.toFloat() / record.totalBlanks),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(48.dp),
        )
    }
}

internal fun historyModeLabel(mode: String): String = when (mode) {
    "SENTENCE" -> "句子挖空"
    "WORD" -> "字词挖空"
    "REVERSE" -> "反向默写"
    "CUSTOM" -> "自定义挖空"
    else -> mode.ifBlank { "练习" }
}

private fun dueLabel(due: Long): String {
    val today = LocalDate.now()
    val date = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> Strings.f("逾期 {0} 天 · {1}", -days, formatTime(due))
        days == 0L -> Strings.f("今天 · {0}", formatTime(due))
        else -> formatTime(due)
    }
}

private fun startOfToday(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.StudyCoach
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.data.repository.SentenceCardStore
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 首页可配置卡片；key 与 `DesktopPrefs.DEFAULT_HOME_ORDER` 对应。 */
private enum class HomeSection(val key: String, val label: String) {
    PLAN("plan", "今日计划"),
    DAILY("daily", "今日一句"),
    DUE("due", "到期与计划"),
    RECENT("recent", "最近打开"),
    ARTICLES("articles", "最近文章"),
}

@Composable
fun HomeScreen(state: AppState) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val records by state.records.records.collectAsState(initial = emptyList())
    val revision by state.fsrs.revision.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<StudyCoach.Snapshot?>(null) }
    var sentence by remember { mutableStateOf<SentenceCardStore.Snapshot?>(null) }
    var showLayoutEditor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        state.articles.awaitLoaded()
        state.records.awaitLoaded()
        while (true) {
            delay(30_000)
            tick++
        }
    }
    LaunchedEffect(articles, records, revision, tick) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            snapshot = state.studySnapshot(now)
            sentence = state.ensureTodaySentence(now)
        }
    }

    val plan = remember(snapshot) { snapshot?.let { StudyCoach.buildPlan(it.input) } }
    val due = snapshot?.input?.dueArticles.orEmpty()
    val recent = articles.sortedByDescending { it.updatedAt }.take(6)
    val totalBlanks = records.sumOf { it.totalBlanks }
    val totalCorrect = records.sumOf { it.correctCount }
    val todayCount = snapshot?.input?.todayPracticeCount ?: 0
    val goal = state.reminderPrefs.dailyPracticeGoal
    val visibleSections = state.prefs.homeSectionOrder
        .filter { it !in state.prefs.homeSectionHidden }
        .mapNotNull { key -> HomeSection.entries.firstOrNull { it.key == key } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth().widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Blancall", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "填上空白，想起知识。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { showLayoutEditor = true }) { Text("编辑布局") }
                        OutlinedButton(onClick = { state.navigate(Screen.Search) }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("搜索")
                        }
                        Button(onClick = { state.navigate(Screen.Import) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("导入文章")
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatTile("文章", articles.size.toString(), Modifier.weight(1f))
                    StatTile("今日待复习", due.size.toString(), Modifier.weight(1f))
                    StatTile("今日已练", todayCount.toString(), Modifier.weight(1f))
                    StatTile(
                        "连续学习",
                        Strings.f("{0} 天", state.reminderPrefs.studyStreakDays),
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "总正确率",
                        if (totalBlanks <= 0) "—" else "${(totalCorrect * 100 / totalBlanks)}%",
                        Modifier.weight(1f),
                    )
                }

                GoalBar(todayCount = todayCount, goal = goal, enabled = state.reminderPrefs.showDailyGoalOnCard)
            }
        }

        visibleSections.forEach { section ->
            when (section) {
                HomeSection.PLAN -> item {
                    SectionCard(
                        title = "今日计划",
                subtitle = plan?.let { p ->
                    when {
                        p.items.isEmpty() -> "今天没有必须完成的内容，自由阅读也可以。"
                        p.examDaysLeft != null -> Strings.f(
                            "距考试 {0} 天 · 今日约 {1} 分钟",
                            p.examDaysLeft,
                            p.estimatedMinutes,
                        )
                        else -> Strings.f("按到期、未完成与薄弱项排好，约 {0} 分钟", p.estimatedMinutes)
                    }
                } ?: "正在计算今日计划…",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { state.navigate(Screen.StudyFlow) }) { Text("任务流") }
                        if (due.isNotEmpty()) {
                            TextButton(onClick = { state.navigate(Screen.Statistics()) }) { Text("查看数据") }
                        }
                    }
                },
            ) {
                val items = plan?.items.orEmpty()
                if (items.isEmpty()) {
                    Text(
                        "没有到期内容。新导入的文章会在完成第一次练习后进入记忆排期。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    items.forEach { item ->
                        PlanRow(
                            item = item,
                            onStart = {
                                when (item.kind) {
                                    StudyCoach.Kind.MISTAKE_DRILL -> state.navigate(Screen.Statistics())
                                    StudyCoach.Kind.SENTENCE_CARDS -> state.navigate(Screen.SentenceCards())
                                    StudyCoach.Kind.DAILY_GOAL -> state.navigate(Screen.Articles)
                                    else -> if (item.articleId > 0) {
                                        state.navigate(Screen.Practice(item.articleId))
                                    } else {
                                        state.navigate(Screen.Articles)
                                    }
                                }
                            },
                        )
                    }
                }
            }
                }

                HomeSection.DAILY -> if (sentence != null) {
                    item {
                SectionCard(
                    title = "今日一句",
                    subtitle = sentence!!.title.ifBlank { "未命名文章" },
                    trailing = {
                        TextButton(onClick = { state.navigate(Screen.Reader(sentence!!.articleId)) }) {
                            Text("查看原句")
                        }
                    },
                ) {
                    Text(
                        sentence!!.text,
                        style = MaterialTheme.typography.titleMedium,
                        lineHeight = MaterialTheme.typography.titleMedium.fontSize * 1.7,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { state.navigate(Screen.SentenceCards()) }) { Text("开始句子卡") }
                        TextButton(onClick = { state.navigate(Screen.Articles) }) { Text("去文章列表") }
                        Text(
                            "每天抽一句，复习时优先回到这篇。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
                }

                HomeSection.DUE -> item {
                    SectionCard(title = "到期与计划", subtitle = Strings.f("已到期 {0} 篇，按最逾期排序", due.size)) {
                if (due.isEmpty()) {
                    Text(
                        "今天没有到期文章。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val byId = articles.associateBy { it.id }
                    due.take(6).forEach { item ->
                        val article = byId[item.articleId]
                        if (article != null) {
                            ArticleRow(
                                article = article,
                                onOpen = { state.navigate(Screen.Reader(article.id)) },
                                onPractice = { state.navigate(Screen.Practice(article.id)) },
                            )
                        } else {
                            KeyValueRow(item.title.ifBlank { "未命名文章" }, "已到期")
                        }
                    }
                }
            }
                }

                HomeSection.RECENT -> if (state.prefs.recentArticleIds.isNotEmpty()) {
                    item {
                SectionCard(title = "最近打开", subtitle = "换设备/重开后快速回到上次读的地方") {
                    val byId = articles.associateBy { it.id }
                    state.prefs.recentArticleIds.mapNotNull { byId[it] }.take(5).forEach { article ->
                        ArticleRow(
                            article = article,
                            onOpen = { state.navigate(Screen.Reader(article.id)) },
                            onPractice = { state.navigate(Screen.Practice(article.id)) },
                        )
                    }
                }
            }
                }

                HomeSection.ARTICLES -> item {
                    SectionCard(title = "最近文章", subtitle = "继续上次的阅读与练习") {
                if (recent.isEmpty()) {
                    EmptyState(
                        "还没有文章",
                        "从粘贴文本、TXT 或 PDF 导入第一篇，开始挖空背诵。",
                    ) {
                        Button(onClick = { state.navigate(Screen.Import) }) { Text("导入第一篇") }
                    }
                } else {
                    recent.forEach { article ->
                        ArticleRow(
                            article = article,
                            onOpen = { state.navigate(Screen.Reader(article.id)) },
                            onPractice = { state.navigate(Screen.Practice(article.id)) },
                        )
                    }
                    TextButton(onClick = { state.navigate(Screen.Articles) }) { Text("查看全部文章 →") }
                }
            }
                }
            }
        }
    }

    if (showLayoutEditor) {
        HomeLayoutDialog(state = state, onDismiss = { showLayoutEditor = false })
    }
}

@Composable
private fun HomeLayoutDialog(state: AppState, onDismiss: () -> Unit) {
    val order = state.prefs.homeSectionOrder
    val hidden = state.prefs.homeSectionHidden
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("首页布局") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.forEachIndexed { index, key ->
                    val section = HomeSection.entries.firstOrNull { it.key == key } ?: return@forEachIndexed
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(section.label, Modifier.weight(1f))
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val next = order.toMutableList().also { it.add(index - 1, it.removeAt(index)) }
                                state.prefs.updateHomeLayout(next, hidden)
                            },
                        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
                        IconButton(
                            enabled = index < order.lastIndex,
                            onClick = {
                                val next = order.toMutableList().also { it.add(index + 1, it.removeAt(index)) }
                                state.prefs.updateHomeLayout(next, hidden)
                            },
                        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        Switch(
                            checked = key !in hidden,
                            onCheckedChange = { checked ->
                                state.prefs.updateHomeLayout(
                                    order,
                                    if (checked) hidden - key else hidden + key,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = {
            TextButton(onClick = { state.prefs.resetHomeLayout() }) { Text("恢复默认") }
        },
    )
}

@Composable
private fun GoalBar(todayCount: Int, goal: Int, enabled: Boolean) {
    if (!enabled || goal <= 0) return
    val fraction = (todayCount.toFloat() / goal).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            Strings.f("今日目标 {0}/{1} 次", todayCount, goal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier.weight(1f).height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
        }
        Text(
            if (todayCount >= goal) "已完成" else Strings.f("还差 {0} 次", goal - todayCount),
            style = MaterialTheme.typography.bodySmall,
            color = if (todayCount >= goal) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanRow(item: StudyCoach.Item, onStart: () -> Unit) {
    val fallbackTitle = Strings.t("未命名")
    val (label, detail) = when (item.kind) {
        StudyCoach.Kind.RESUME -> Strings.f(
            "继续练习 · {0}",
            item.title.ifBlank { fallbackTitle },
        ) to Strings.f(
            "已完成 {0}/{1} 空，还剩 {2} 空",
            item.answered,
            item.total,
            item.count,
        )
        StudyCoach.Kind.REVIEW_ARTICLE -> Strings.f(
            "复习 · {0}",
            item.title.ifBlank { fallbackTitle },
        ) to Strings.f("约 {0} 分钟", item.estimatedMinutes)
        StudyCoach.Kind.START_ARTICLE -> Strings.f(
            "开始第一遍 · {0}",
            item.title.ifBlank { fallbackTitle },
        ) to "计划中还没练过"
        StudyCoach.Kind.WEAK_ARTICLE -> Strings.f(
            "薄弱篇 · {0}",
            item.title.ifBlank { fallbackTitle },
        ) to Strings.f("薄弱 {0} 处", item.count)
        StudyCoach.Kind.MISTAKE_DRILL -> "错题集训" to Strings.f("全库 {0} 处错题", item.count)
        StudyCoach.Kind.SENTENCE_CARDS -> "句子卡" to Strings.f("{0} 句到期", item.count)
        StudyCoach.Kind.DAILY_GOAL -> "再练一篇" to Strings.f("今天还差 {0} 次", item.count)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onStart) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("开始")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ArticleRow(
    article: Article,
    onOpen: () -> Unit,
    onPractice: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(article.title.ifBlank { "未命名" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(
                    article.author.takeIf { it.isNotBlank() },
                    Strings.f("{0} 字", article.content.length),
                    formatDay(article.updatedAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onOpen) { Text("阅读") }
            FilledTonalButton(onClick = onPractice) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("练习")
            }
        }
    }
}

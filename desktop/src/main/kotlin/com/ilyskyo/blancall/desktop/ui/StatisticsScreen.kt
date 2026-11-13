package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.AchievementManager
import com.ilyskyo.blancall.algorithm.DayBucket
import com.ilyskyo.blancall.algorithm.ForgettingPredictor
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.algorithm.HeatColor
import com.ilyskyo.blancall.algorithm.MemoryHeatmap
import com.ilyskyo.blancall.algorithm.MistakeNotebook
import com.ilyskyo.blancall.algorithm.ReviewTemplate
import com.ilyskyo.blancall.algorithm.heatmapGrid
import com.ilyskyo.blancall.data.model.ErrorType
import com.ilyskyo.blancall.data.model.PracticeRecord
import com.ilyskyo.blancall.data.model.toErrorType
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import com.ilyskyo.blancall.desktop.platform.ShareImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StatisticsScreen(state: AppState, articleId: Long?) {
    val scope = rememberCoroutineScope()
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val records by state.records.records.collectAsState(initial = emptyList())
    val revision by state.fsrs.revision.collectAsState()
    val article = articleId?.let { id -> articles.firstOrNull { it.id == id } }
    val scoped = remember(records, articleId) {
        if (articleId == null) records else records.filter { it.articleId == articleId }
    }
    val totalBlanks = scoped.sumOf { it.totalBlanks }
    val totalCorrect = scoped.sumOf { it.correctCount }
    val accuracy = if (totalBlanks <= 0) 0f else totalCorrect.toFloat() / totalBlanks
    val states = remember(revision, articles) { state.fsrs.allStates() }
    val dueCount = articles.count { a -> states[a.id]?.let { FsrsEngine.isDue(it) } == true }
    var onlyUnresolved by remember { mutableStateOf(false) }
    var crossDetail by remember { mutableStateOf<PracticeRecord?>(null) }
    val mistakes = remember(scoped, articleId) { MistakeNotebook.entries(scoped, articleId) }
    val shownMistakes = remember(mistakes, onlyUnresolved) {
        if (onlyUnresolved) mistakes.filter { !it.resolved } else mistakes
    }
    val snapshot = remember(revision, articles, records) { state.studySnapshot() }
    val mistakeByType = remember(scoped) { countErrorsByType(scoped) }
    val achievements = remember(scoped, revision) { evaluateAchievements(state, scoped) }
    var unlockedTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(achievements) {
        withContext(Dispatchers.IO) {
            state.achievements.recordUnlocked(achievements.filter { it.unlocked }.map { it.id }.toSet())
            unlockedTimes = state.achievements.snapshot()
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
                Modifier.fillMaxWidth().widthIn(max = 1100.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (articleId != null) {
                    IconButton(onClick = { state.back() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (article == null) "数据" else Strings.f("《{0}》统计", article.title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (articleId == null) "全局练习记录与记忆状态"
                        else Strings.f("{0} 次练习", scoped.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (articleId != null) {
                    TextButton(onClick = { state.navigate(Screen.Reader(articleId)) }) { Text("返回阅读") }
                } else {
                    TextButton(onClick = { state.navigate(Screen.Articles) }) { Text("文章列表") }
                }
                OutlinedButton(
                    onClick = {
                        val target = FileDialogs.save(
                            "导出分享图",
                            "blancall_stats_${LocalDate.now()}.png",
                        ) ?: return@OutlinedButton
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ShareImageExporter.export(
                                        target = target,
                                        title = if (article == null) "我的学习数据"
                                        else article.title.ifBlank { Strings.t("未命名") },
                                        subtitle = Strings.f(
                                            "累计 {0} 次练习 · 正确率 {1}",
                                            scoped.size,
                                            if (totalBlanks == 0) "—" else percent(accuracy),
                                        ),
                                        lines = listOf(
                                            Strings.t("练习次数") to scoped.size.toString(),
                                            Strings.t("填空正确率") to if (totalBlanks == 0) "—" else percent(accuracy),
                                            Strings.t("连续学习") to Strings.f("{0} 天", state.reminderPrefs.studyStreakDays),
                                            Strings.t("最长连续") to Strings.f("{0} 天", state.reminderPrefs.longestStreakDays),
                                            Strings.t("待复习") to dueCount.toString(),
                                        ),
                                    )
                                }
                                state.showToast(Strings.f("分享图已导出：{0}", target.name))
                            }.onFailure { state.showToast(Strings.f("导出失败：{0}", it.message)) }
                        }
                    },
                ) { Text("导出分享图") }
                OutlinedButton(
                    onClick = {
                        val target = FileDialogs.save(
                            "导出练习记录",
                            "blancall_records_${LocalDate.now()}.csv",
                        ) ?: return@OutlinedButton
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    exportCsv(target, scoped, articles.associate { it.id to it.title })
                                }
                                state.showToast(Strings.f("已导出：{0}", target.name))
                            }.onFailure { state.showToast(Strings.f("导出失败：{0}", it.message)) }
                        }
                    },
                ) { Text("导出 CSV") }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().widthIn(max = 1100.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatBlock("文章", articles.size.toString(), Modifier.weight(1f))
                StatBlock("练习次数", scoped.size.toString(), Modifier.weight(1f))
                StatBlock("填空正确率", if (totalBlanks == 0) "—" else percent(accuracy), Modifier.weight(1f))
                StatBlock("今日待复习", dueCount.toString(), Modifier.weight(1f))
                StatBlock("连续学习", Strings.f("{0} 天", state.reminderPrefs.studyStreakDays), Modifier.weight(1f))
            }
        }

        if (articleId == null) {
            item {
                SectionCard(
                    title = "学习日历",
                    subtitle = Strings.f(
                        "近 12 周 · 共 {0} 次练习 · 最长连续 {1} 天",
                        scoped.size,
                        state.reminderPrefs.longestStreakDays,
                    ),
                ) {
                    PracticeHeatmap(scoped)
                }
            }
        }

        if (article != null) {
            item {
                val heat = remember(article.content, scoped, revision) {
                    MemoryHeatmap.generate(
                        content = article.content,
                        records = scoped,
                        sentenceStates = state.fsrs.allSentenceStates(),
                        articleId = article.id,
                    )
                }
                SectionCard(
                    title = "记忆热力图",
                    subtitle = "按句子级 FSRS 留存率着色：越红越接近遗忘，灰色表示样本不足",
                ) {
                    if (heat.sentences.isEmpty()) {
                        Text("暂无可切分的句子。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        heat.sentences.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HeatDot(item.heatColor)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (item.lowSample) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        buildString {
                                            append(
                                                if (item.retention < 0f) Strings.t("留存率 —")
                                                else Strings.f("留存率 {0}", percent(item.retention)),
                                            )
                                            append(" · ")
                                            append(
                                                Strings.f(
                                                    "错 {0} · 作答 {1} 次",
                                                    percent(item.errorRate),
                                                    item.practiceCount,
                                                ),
                                            )
                                            if (item.lowSample) {
                                                append(" · ")
                                                append(Strings.t("样本不足"))
                                            }
                                            if (item.estimated) {
                                                append(" · ")
                                                append(Strings.t("位置为估算"))
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MemoryHeatmap.legend().forEach { (band, color) ->
                                LegendItem(color, bandLabel(band))
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "记忆状态", subtitle = "文章级 FSRS 排期（完成练习后自动更新）") {
                if (articleId == null) {
                    Text(
                        Strings.f("共 {0} 篇文章有记忆状态。进入单篇统计可查看具体到期时间。", states.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    articles.filter { states.containsKey(it.id) }
                        .sortedBy { states[it.id]?.due ?: Long.MAX_VALUE }
                        .take(8)
                        .forEach { a ->
                            val card = states[a.id]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(a.title.ifBlank { "未命名" }, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (card == null) "—"
                                    else Strings.f(
                                        "到期 {0} · 复习 {1} 次",
                                        formatTime(card.due),
                                        card.reviewCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                } else {
                    val card = article?.let { states[it.id] }
                    if (card == null) {
                        Text("还没有记忆状态，先完成一次练习。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        KeyValueRow("练习次数", card.reviewCount.toString())
                        KeyValueRow("遗忘次数", card.lapses.toString())
                        KeyValueRow("稳定性", Strings.f("{0} 天", "%.2f".format(card.stability)))
                        KeyValueRow("难度", "%.2f".format(card.difficulty))
                        KeyValueRow("下次到期", formatTime(card.due))
                        KeyValueRow("当前留存率", percent(FsrsEngine.retentionRate(card).toFloat()))
                    }
                }
            }
        }

        if (article != null) {
            item {
                val prediction = remember(article.id, scoped, revision) {
                    runCatching {
                        ForgettingPredictor.predict(
                            articles = listOf(article),
                            allRecords = scoped,
                            template = ReviewTemplate.fromId(state.prefs.reviewTemplateId),
                            fsrsStates = states,
                        ).firstOrNull { it.articleId == article.id }
                    }.getOrNull()
                }
                SectionCard(
                    title = "遗忘曲线",
                    subtitle = "未来 30 天留存率预测（FSRS-6 幂律曲线，虚线为目标留存率）",
                ) {
                    val curve = prediction?.decayCurve.orEmpty()
                    if (curve.isEmpty()) {
                        Text("还没有足够的记忆状态来预测曲线。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        DecayCurve(curve, FsrsEngine.targetRetention().toFloat())
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(Strings.f("当前 {0}", percent(curve.first())), style = MaterialTheme.typography.bodySmall)
                            Text(
                                Strings.f("明日 {0}", percent(curve.getOrElse(1) { curve.first() })),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(Strings.f("第 30 天 {0}", percent(curve.last())), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (articleId == null) {
            item {
                SectionCard(
                    title = "需加强的文章",
                    subtitle = if (snapshot.weakest != null) "按收缩错误率排序（至少 3 空才入榜）"
                    else "练满 3 空后开始给出建议",
                ) {
                    val weakest = snapshot.weakest
                    if (weakest == null) {
                        Text(
                            "还没有足够的练习数据来判断薄弱篇。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(weakest.title.ifBlank { "未命名" }, fontWeight = FontWeight.Medium)
                                Text(
                                    Strings.f(
                                        "正确率 {0} · {1} 次练习 · 错 {2} 处",
                                        percent(weakest.correctRate),
                                        weakest.practiceCount,
                                        weakest.mistakeCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { state.navigate(Screen.Statistics(weakest.articleId)) }) {
                                    Text("看统计")
                                }
                                Button(onClick = { state.navigate(Screen.Practice(weakest.articleId)) }) {
                                    Text("去巩固")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (articleId == null) {
            item {
                SectionCard(
                    title = "成就",
                    subtitle = Strings.f(
                        "已解锁 {0} / {1} · 由练习记录实时派生",
                        achievements.count { it.unlocked },
                        achievements.size,
                    ),
                ) {
                    AchievementManager.Family.entries.forEach { family ->
                        val items = achievements.filter { it.family == family }
                        if (items.isEmpty()) return@forEach
                        Text(
                            familyLabel(family),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        items.forEach { item ->
                            AchievementRow(item, unlockedTimes[item.id])
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "错题本",
                subtitle = Strings.f(
                    "共 {0} 处错题，未攻克 {1} 处",
                    mistakes.size,
                    mistakes.count { !it.resolved },
                ),
                trailing = {
                    TextButton(onClick = { onlyUnresolved = !onlyUnresolved }) {
                        Text(if (onlyUnresolved) "显示全部" else "只看未攻克")
                    }
                },
            ) {
                if (shownMistakes.isEmpty()) {
                    EmptyState(
                        if (mistakes.isEmpty()) "还没有错题" else "没有未攻克的错题",
                        if (mistakes.isEmpty()) "答错的内容会自动收进这里，并按句子聚合。" else "继续保持。",
                    )
                } else {
                    shownMistakes.take(40).forEach { entry ->
                        MistakeRow(
                            entry = entry,
                            title = articles.firstOrNull { it.id == entry.articleId }?.title.orEmpty(),
                            onOpen = {
                                if (entry.articleId > 0) state.navigate(Screen.Reader(entry.articleId))
                            },
                            onDrill = {
                                if (entry.articleId > 0) state.navigate(Screen.Drill(entry.articleId))
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "错误类型分布", subtitle = "按判分结果分型（只统计有错因的记录）") {
                if (scoped.none { it.mistakes.isNotEmpty() }) {
                    Text("暂无错因数据。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val total = mistakeByType.values.sum().coerceAtLeast(1)
                    WeaknessRadar(mistakeByType)
                    Spacer(Modifier.height(14.dp))
                    ErrorType.entries.filter { it != ErrorType.OTHER }.forEach { type ->
                        val count = mistakeByType[type] ?: 0
                        ErrorBar(errorLabel(type.name), count, total)
                    }
                }
            }
        }

        item {
            SectionCard(title = "练习记录", subtitle = Strings.f("最近 {0} 条", minOf(scoped.size, 20))) {
                if (scoped.isEmpty()) {
                    EmptyState("还没有练习记录", "完成一次练习后，这里会出现记录与正确率。")
                } else {
                    scoped.sortedByDescending { it.timestamp }.take(20).forEach { record ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (record.articleId > 0) {
                                        state.navigate(Screen.Statistics(record.articleId))
                                    } else {
                                        crossDetail = record
                                    }
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    Strings.f(
                                        "{0} · {1}/{2}",
                                        modeLabel(record.mode),
                                        record.correctCount,
                                        record.totalBlanks,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    formatTime(record.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (record.totalBlanks == 0) "—"
                                else percent(record.correctCount.toFloat() / record.totalBlanks),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 32.dp)) }
    }

    crossDetail?.let { record ->
        val sources = record.sourceArticleIds.mapNotNull { id -> articles.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { crossDetail = null },
            title = { Text("跨文练习") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (sources.isEmpty()) {
                        Text("来源文章已删除。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        sources.forEach { article ->
                            TextButton(onClick = {
                                crossDetail = null
                                state.navigate(Screen.Reader(article.id))
                            }) { Text(article.title.ifBlank { Strings.t("未命名") }) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { crossDetail = null }) { Text("取消") } },
        )
    }
}

// ── 错题本 ──

@Composable
private fun MistakeRow(
    entry: MistakeNotebook.Entry,
    title: String,
    onOpen: () -> Unit,
    onDrill: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                Strings.f("应填「{0}」", entry.correctAnswer),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                Strings.f(
                    "{0} · 错 {1}/{2} 次",
                    errorLabel(entry.lastErrorType),
                    entry.wrongCount,
                    entry.attempts,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.lastUserAnswer.isNotBlank()) {
            Text(
                Strings.f("你写「{0}」", entry.lastUserAnswer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (entry.sentenceText.isNotBlank()) {
            Text(
                entry.sentenceText.trim().let { if (it.length > 90) it.take(90) + "…" else it },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    !entry.exact -> "旧记录 · 无法判断是否攻克"
                    entry.resolved -> "已攻克"
                    else -> "未攻克"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    !entry.exact -> MaterialTheme.colorScheme.onSurfaceVariant
                    entry.resolved -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
            )
            if (title.isNotBlank()) {
                Text(
                    Strings.f("《{0}》", title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.articleId > 0) {
                TextButton(onClick = onOpen) { Text("回到原篇") }
                TextButton(onClick = onDrill) { Text("集训") }
            }
        }
    }
}

// ── 热力图 ──

@Composable
private fun PracticeHeatmap(records: List<PracticeRecord>) {
    val today = LocalDate.now()
    val weeks = 12
    val counts = remember(records, today) {
        val byDay = records.groupingBy {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }.eachCount()
        List(weeks * 7) { index ->
            val date = today.minusDays((weeks * 7 - 1 - index).toLong())
            byDay[date] ?: 0
        }
    }
    val grid = remember(counts, today) { heatmapGrid(counts, today, DayOfWeek.MONDAY, weeks) }
    val max = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
    val active = counts.count { it > 0 }
    val base = MaterialTheme.colorScheme.primary

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width((weeks * 14).dp).height(98.dp)) {
            val cell = 12.dp.toPx()
            val gap = 2.dp.toPx()
            grid.forEachIndexed { col, rows ->
                rows.forEachIndexed { row, cellData ->
                    if (cellData == null) return@forEachIndexed
                    val alpha = if (cellData.count == 0) 0.08f
                    else 0.22f + 0.78f * (cellData.count.toFloat() / max)
                    drawRoundRect(
                        color = base.copy(alpha = alpha),
                        topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Strings.f("有练习 {0} 天", active), style = MaterialTheme.typography.bodyMedium)
            Text(
                "周一在上、越靠右越近",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                Strings.f("单日最多 {0} 次", counts.maxOrNull() ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeatDot(color: HeatColor) {
    val composeColor = if (color.argb < 0) Color.Transparent else Color(color.argb)
    Canvas(Modifier.width(12.dp).height(12.dp)) {
        drawRoundRect(
            color = if (color.argb < 0) Color.Gray.copy(alpha = 0.4f) else composeColor,
            cornerRadius = CornerRadius(3.dp.toPx()),
            size = Size(size.width, size.height),
            style = if (color.argb < 0) androidx.compose.ui.graphics.drawscope.Stroke(1.5f) else
                androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
}

@Composable
private fun LegendItem(color: HeatColor, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HeatDot(color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun bandLabel(band: String): String = Strings.t(
    when (band) {
        MemoryHeatmap.BAND_WEAK -> "快忘了"
        MemoryHeatmap.BAND_HARD -> "模糊"
        MemoryHeatmap.BAND_MID -> "接近目标"
        MemoryHeatmap.BAND_FAMILIAR -> "熟悉"
        MemoryHeatmap.BAND_FIRM -> "牢固"
        MemoryHeatmap.BAND_LOW_SAMPLE -> "样本不足"
        else -> "未练习"
    },
)

// ── 分布 ──

/**
 * 薄弱构成雷达图：六维 = 六类错因占全部错因的比例。
 * 与 Android 的 WeaknessProfileCard 同口径（分母是错因总数，不做贝叶斯收缩）。
 */
@Composable
private fun WeaknessRadar(counts: Map<ErrorType, Int>) {
    val types = listOf(
        ErrorType.TYPO,
        ErrorType.GLYPH_CONFUSED,
        ErrorType.MISSING,
        ErrorType.EXTRA,
        ErrorType.WRONG_ORDER,
        ErrorType.INCORRECT,
    )
    val total = types.sumOf { counts[it] ?: 0 }.coerceAtLeast(1)
    val values = types.map { (counts[it] ?: 0).toFloat() / total }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary

    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.8f
        val n = values.size
        fun at(index: Int, r: Float): Offset {
            val angle = -Math.PI / 2.0 + 2.0 * Math.PI * index / n
            return Offset(
                center.x + (r * cos(angle)).toFloat(),
                center.y + (r * sin(angle)).toFloat(),
            )
        }
        for (ring in 1..4) {
            val path = Path()
            for (i in 0 until n) {
                val p = at(i, radius * ring / 4f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(
                path,
                gridColor.copy(alpha = if (ring == 4) 0.75f else 0.35f),
                style = Stroke(if (ring == 4) 1.5.dp.toPx() else 1.dp.toPx()),
            )
        }
        for (i in 0 until n) {
            drawLine(gridColor.copy(alpha = 0.35f), center, at(i, radius), 1.dp.toPx())
        }
        val data = Path()
        values.forEachIndexed { i, value ->
            val p = at(i, radius * value.coerceIn(0f, 1f))
            if (i == 0) data.moveTo(p.x, p.y) else data.lineTo(p.x, p.y)
        }
        data.close()
        drawPath(data, accent.copy(alpha = 0.22f))
        drawPath(data, accent, style = Stroke(2.dp.toPx()))
        values.forEachIndexed { i, value ->
            val p = at(i, radius * value.coerceIn(0f, 1f))
            drawCircle(accent.copy(alpha = 0.18f), 7.dp.toPx(), p)
            drawCircle(accent, 3.dp.toPx(), p)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        types.forEachIndexed { i, type ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    errorLabel(type.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    percent(values[i]),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun ErrorBar(label: String, count: Int, total: Int) {
    val fraction = count.toFloat() / total
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.Box(
            Modifier.weight(1f).height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        ) {
            if (fraction > 0f) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth(fraction).height(8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                )
            }
        }
        Text("$count", Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun countErrorsByType(records: List<PracticeRecord>): Map<ErrorType, Int> {
    val out = mutableMapOf<ErrorType, Int>()
    for (record in records) {
        for (m in record.mistakes) {
            val type = m.errorType.toErrorType()
            out[type] = (out[type] ?: 0) + 1
        }
    }
    return out
}

private fun errorLabel(raw: String): String = Strings.t(
    when (raw.toErrorType()) {
        ErrorType.TYPO -> "错别字"
        ErrorType.GLYPH_CONFUSED -> "形近字"
        ErrorType.MISSING -> "漏字"
        ErrorType.EXTRA -> "多字"
        ErrorType.WRONG_ORDER -> "顺序错"
        ErrorType.INCORRECT -> "内容错"
        ErrorType.OTHER -> "其他"
    },
)

private fun modeLabel(mode: String): String = Strings.t(
    when (mode) {
        "SENTENCE" -> "句子挖空"
        "WORD" -> "字词挖空"
        "REVERSE" -> "反向默写"
        else -> mode
    },
)

private val csvTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** 导出练习记录 CSV。带 UTF-8 BOM，Excel 直接打开不乱码。 */
private fun exportCsv(target: File, records: List<PracticeRecord>, titles: Map<Long, String>) {
    val zone = ZoneId.systemDefault()
    val sb = StringBuilder()
    sb.append("时间,文章,模式,正确,总空数,正确率,用时秒,错题数\n")
    records.sortedBy { it.timestamp }.forEach { r ->
        val time = Instant.ofEpochMilli(r.timestamp).atZone(zone).format(csvTime)
        val title = titles[r.articleId].orEmpty().ifBlank { if (r.articleId > 0) "未命名" else "跨文练习" }
        val rate = if (r.totalBlanks > 0) "%.1f%%".format(r.correctCount * 100f / r.totalBlanks) else ""
        sb.append(csvEscape(time)).append(',')
            .append(csvEscape(title)).append(',')
            .append(csvEscape(modeLabel(r.mode))).append(',')
            .append(r.correctCount).append(',')
            .append(r.totalBlanks).append(',')
            .append(csvEscape(rate)).append(',')
            .append(r.duration / 1000).append(',')
            .append(r.mistakes.size).append('\n')
    }
    target.outputStream().use { out ->
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }
}

private fun csvEscape(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value

private fun evaluateAchievements(
    state: AppState,
    records: List<PracticeRecord>,
): List<AchievementManager.Achievement> {
    val days = records.map { DayBucket.localDate(it.timestamp) }.toSet()
    val longest = DayBucket.longestRun(days)
    val sentenceStates = state.fsrs.allSentenceStates()
    val learned = sentenceStates.values.filter { it.reviewCount > 0 }
    val avg = learned.takeIf { it.isNotEmpty() }
        ?.map { FsrsEngine.retentionRate(it).toFloat() }
        ?.average()
        ?.toFloat()
    val memory = AchievementManager.MemoryStats(
        learnedSentences = learned.size,
        avgRetention = avg,
        dueSentences = learned.count { FsrsEngine.isDue(it) },
    )
    return AchievementManager.evaluate(records, longest, memory)
}

@Composable
private fun AchievementRow(item: AchievementManager.Achievement, unlockedAt: Long?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.width(24.dp).height(24.dp)
                .background(
                    if (item.unlocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    androidx.compose.foundation.shape.CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (item.unlocked) "✓" else "·",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.unlocked) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(achievementName(item.id), style = MaterialTheme.typography.bodyMedium)
            Text(
                Strings.f(
                    "{0}/{1} {2}",
                    item.current.coerceAtMost(item.target),
                    item.target,
                    unitLabel(item.unit),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unlockedAt != null) {
            Text(
                Strings.f("解锁于 {0}", formatDay(unlockedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun familyLabel(family: AchievementManager.Family): String = Strings.t(
    when (family) {
        AchievementManager.Family.PRACTICE -> "练习"
        AchievementManager.Family.STREAK -> "坚持"
        AchievementManager.Family.ACCURACY -> "准确"
        AchievementManager.Family.BREADTH -> "广度"
        AchievementManager.Family.MEMORY -> "记忆"
    },
)

private fun unitLabel(unit: AchievementManager.Unit): String = Strings.t(
    when (unit) {
        AchievementManager.Unit.COUNT -> "次"
        AchievementManager.Unit.DAY -> "天"
        AchievementManager.Unit.KIND -> "种"
        AchievementManager.Unit.MINUTE -> "分钟"
        AchievementManager.Unit.PERCENT -> "%"
    },
)

private fun achievementName(id: String): String = Strings.t(
    when (id) {
        "first_practice" -> "初次练习"
        "practice_10" -> "勤学十次"
        "practice_100" -> "百炼成钢"
        "streak_7" -> "七日不辍"
        "streak_30" -> "一月坚持"
        "streak_100" -> "百日筑基"
        "perfect_one" -> "全对一次"
        "perfect_ten" -> "十全十美"
        "all_modes" -> "三法俱全"
        "focus_60" -> "专注一小时"
        "deep_10" -> "单篇十练"
        "memory_90" -> "记忆九成"
        "memory_95" -> "记忆九五"
        "due_cleared" -> "当日清空"
        else -> id
    },
)

// ── 遗忘曲线 ──

@Composable
private fun DecayCurve(curve: List<Float>, target: Float) {
    val lineColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val w = size.width
        val h = size.height
        fun yOf(value: Float) = h - value.coerceIn(0f, 1f) * h
        drawLine(
            color = targetColor.copy(alpha = 0.45f),
            start = Offset(0f, yOf(target)),
            end = Offset(w, yOf(target)),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
        if (curve.size >= 2) {
            val path = Path()
            curve.forEachIndexed { index, value ->
                val x = w * index / (curve.size - 1).toFloat()
                if (index == 0) path.moveTo(x, yOf(value)) else path.lineTo(x, yOf(value))
            }
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

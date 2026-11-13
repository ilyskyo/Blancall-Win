package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 学习任务流：把今日计划展开成「先做什么、大概多久」的顺序清单。
 * 判定与首页/提醒共用 [StudyCoach]，不另起一套规则。
 */
@Composable
fun StudyFlowScreen(state: AppState) {
    var tick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<StudyCoach.Snapshot?>(null) }
    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) { snapshot = state.studySnapshot() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }

    val plan = remember(snapshot) { snapshot?.let { StudyCoach.buildPlan(it.input) } }
    val input = snapshot?.input

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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text("学习任务流", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "按到期、未完成、薄弱与目标排序；一次最多 3 步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { tick++ }) { Text("重新计算") }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().widthIn(max = 1000.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowStat(
                    "预计用时",
                    plan?.let { Strings.f("{0} 分钟", it.estimatedMinutes) } ?: "—",
                    Modifier.weight(1f),
                )
                FlowStat("今日已练", Strings.f("{0} 次", input?.todayPracticeCount ?: 0), Modifier.weight(1f))
                FlowStat("每日目标", Strings.f("{0} 次", state.reminderPrefs.dailyPracticeGoal), Modifier.weight(1f))
                FlowStat(
                    "距考试",
                    plan?.examDaysLeft?.let { Strings.f("{0} 天", it) } ?: "未设置",
                    Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(
                title = "今日步骤",
                subtitle = when {
                    plan == null -> "正在计算…"
                    plan.items.isEmpty() -> "今天没有必须完成的内容。"
                    plan.examPace == StudyCoach.ExamPace.BEHIND ->
                        "按当前节奏可能排不完考纲，建议增加每日练习次数。"
                    else -> "按顺序完成即可；中途退出会保存断点。"
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
                    items.forEachIndexed { index, item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(labelOf(item), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text(
                                    detailOf(item),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = { start(state, item) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.padding(end = 4.dp))
                                Text("开始")
                            }
                        }
                    }
                }
            }
        }

        if (input != null) {
            item {
                SectionCard(title = "今日构成", subtitle = "与首页、提醒同一口径") {
                    KeyValueRow("到期文章", Strings.f("{0} 篇", snapshot?.dueArticleCount ?: 0))
                    KeyValueRow("到期句子", Strings.f("{0} 句", snapshot?.sentenceTotalDue ?: 0))
                    KeyValueRow("未完成练习", Strings.f("{0} 个", snapshot?.resumables?.size ?: 0))
                    KeyValueRow("未攻克错题", Strings.f("{0} 处", input.mistakeCount))
                    KeyValueRow("薄弱篇", input.weakestArticleTitle.ifBlank { "暂无" })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun start(state: AppState, item: StudyCoach.Item) {
    when (item.kind) {
        StudyCoach.Kind.MISTAKE_DRILL -> {
            if (item.articleId > 0) state.navigate(Screen.Drill(item.articleId))
            else state.navigate(Screen.Statistics())
        }
        StudyCoach.Kind.SENTENCE_CARDS -> state.navigate(Screen.Home)
        StudyCoach.Kind.DAILY_GOAL -> state.navigate(Screen.Articles)
        else -> if (item.articleId > 0) state.navigate(Screen.Practice(item.articleId))
        else state.navigate(Screen.Articles)
    }
}

private fun labelOf(item: StudyCoach.Item): String {
    val fallback = Strings.t("未命名")
    return when (item.kind) {
        StudyCoach.Kind.RESUME -> Strings.f("继续练习 · {0}", item.title.ifBlank { fallback })
        StudyCoach.Kind.REVIEW_ARTICLE -> Strings.f("复习 · {0}", item.title.ifBlank { fallback })
        StudyCoach.Kind.START_ARTICLE -> Strings.f("开始第一遍 · {0}", item.title.ifBlank { fallback })
        StudyCoach.Kind.WEAK_ARTICLE -> Strings.f("薄弱篇 · {0}", item.title.ifBlank { fallback })
        StudyCoach.Kind.MISTAKE_DRILL -> "错题集训"
        StudyCoach.Kind.SENTENCE_CARDS -> "句子卡"
        StudyCoach.Kind.DAILY_GOAL -> "补齐今日次数"
    }
}

private fun detailOf(item: StudyCoach.Item): String = when (item.kind) {
    StudyCoach.Kind.RESUME -> Strings.f(
        "已完成 {0}/{1} 空，还剩 {2} 空",
        item.answered,
        item.total,
        item.count,
    )
    StudyCoach.Kind.REVIEW_ARTICLE -> Strings.f("预计 {0} 分钟", item.estimatedMinutes)
    StudyCoach.Kind.START_ARTICLE -> "计划中还没练过"
    StudyCoach.Kind.WEAK_ARTICLE -> Strings.f("薄弱 {0} 处", item.count)
    StudyCoach.Kind.MISTAKE_DRILL -> Strings.f("全库 {0} 处错题", item.count)
    StudyCoach.Kind.SENTENCE_CARDS -> Strings.f("{0} 句到期", item.count)
    StudyCoach.Kind.DAILY_GOAL -> Strings.f("今天还差 {0} 次", item.count)
}

@Composable
private fun FlowStat(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

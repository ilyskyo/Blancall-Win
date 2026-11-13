package com.ilyskyo.blancall.desktop.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.algorithm.SentenceCloze
import com.ilyskyo.blancall.data.repository.DailySentenceCoordinator
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 句子卡（大卡片）：今日句置顶 → 全部到期句 → 全部未学新句。
 *
 * 交互对齐 Android 版：回忆面按「分句首字提示法」挖空，点「显示答案」揭晓整句；
 * 三键评级（忘记 / 不熟 / 记住了）写入句子级 FSRS；「全量复习」把已学未到期的句子也拉进队列。
 * 会话状态只保存在界面层，落盘由 [com.ilyskyo.blancall.data.repository.FsrsStateStore.saveSentence] 负责。
 */
@Composable
fun SentenceCardsScreen(
    state: AppState,
    tagId: Long? = null,
    articleId: Long? = null,
) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val tagData by state.tags.data.collectAsState()
    val scope = rememberCoroutineScope()

    val scopeTag = tagId?.let { id -> tagData.tags.firstOrNull { it.id == id } }
    val scopeArticles = remember(articles, tagId, articleId, tagData) {
        when {
            articleId != null -> articles.filter { it.id == articleId }
            tagId != null -> articles.filter { tagId in tagData.links[it.id].orEmpty() }
            else -> articles
        }
    }

    var queue by remember { mutableStateOf<List<DailySentenceCoordinator.QueueItem>?>(null) }
    var states by remember { mutableStateOf<Map<String, FsrsEngine.CardState>>(emptyMap()) }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var reviewAll by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val sessionRatings = remember { mutableStateMapOf<String, FsrsEngine.Rating>() }

    // 只在文章池 / 复习轮次变化时重建队列。不能监听 fsrs.revision：
    // 每评一张卡都会写句子状态并 bump revision，若作为 key 会把队列重置回第一张。
    LaunchedEffect(scopeArticles, reviewAll, round) {
        val paused = state.fsrs.pausedArticleIds()
        val pool = scopeArticles.filterNot { it.id in paused }
        if (pool.isEmpty()) {
            queue = emptyList()
            states = emptyMap()
            return@LaunchedEffect
        }
        val snapshot = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val sentenceStates = state.fsrs.allSentenceStates()
            val built = runCatching {
                DailySentenceCoordinator.buildQueue(
                    store = state.sentenceCards,
                    articles = pool,
                    sentenceStates = sentenceStates,
                    now = now,
                    includeAllLearned = reviewAll,
                    excludedArticleIds = paused,
                )
            }.getOrDefault(emptyList())
            built to sentenceStates
        }
        queue = snapshot.first
        states = snapshot.second
        index = 0
        revealed = false
        finished = false
        sessionRatings.clear()
    }

    val items = queue
    val current = items?.getOrNull(index)

    fun advance() {
        revealed = false
        if (index + 1 >= items.orEmpty().size) finished = true else index++
    }

    fun rate(rating: FsrsEngine.Rating) {
        val item = current ?: return
        val now = System.currentTimeMillis()
        val previous = states[item.key] ?: FsrsEngine.CardState()
        val next = FsrsEngine.review(previous, rating, now).also { it.lastRating = rating.name }
        states = states + (item.key to next)
        sessionRatings[item.key] = rating
        scope.launch(Dispatchers.IO) { runCatching { state.fsrs.saveSentence(item.key, next) } }
        advance()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { state.back() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("句子卡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        scopeTag != null -> Strings.f("范围：标签「{0}」", scopeTag.name)
                        articleId != null -> Strings.f("范围：{0}", current?.title ?: scopeArticles.firstOrNull()?.title.orEmpty())
                        else -> "今日一句 + 到期句 + 新句，评完自动下一张"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (current != null) {
                Text(
                    Strings.f("第 {0} / {1} 张", index + 1, items.orEmpty().size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                reviewAll = !reviewAll
                round++
            }) { Text(if (reviewAll) "结束复习" else "全量复习") }
            if (current != null && !finished) {
                TextButton(onClick = { state.navigate(Screen.Reader(current.articleId)) }) { Text("回到原篇") }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("遮挡提示", style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = state.prefs.sentenceClozeEnabled,
                onCheckedChange = { state.prefs.updateSentenceClozeEnabled(it) },
            )
            Text("首次也遮挡", style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = state.prefs.sentenceClozeNewEnabled,
                enabled = state.prefs.sentenceClozeEnabled,
                onCheckedChange = { state.prefs.updateSentenceClozeNewEnabled(it) },
            )
        }

        when {
            items == null -> BoxedMessage("正在抽取句子…")
            items.isEmpty() -> BoxedMessage(
                if (articleId != null || tagId != null) "这个范围内没有可练习的句子。"
                else "还没有可练习的句子。先导入文章并完成一次练习。",
            )
            finished -> FinishedCard(
                queueSize = items.size,
                again = sessionRatings.values.count { it == FsrsEngine.Rating.AGAIN },
                hard = sessionRatings.values.count { it == FsrsEngine.Rating.HARD },
                good = sessionRatings.values.count { it == FsrsEngine.Rating.GOOD },
                onAgain = {
                    reviewAll = true
                    round++
                },
                onHome = { state.navigate(Screen.Home) },
            )
            current != null -> SentenceCard(
                item = current,
                cardState = states[current.key],
                revealed = revealed,
                clozeEnabled = state.prefs.sentenceClozeEnabled,
                clozeNewEnabled = state.prefs.sentenceClozeNewEnabled,
                onReveal = { revealed = true },
                onRate = ::rate,
                onSkip = { advance() },
                onPrevious = {
                    if (index > 0) {
                        index--
                        revealed = false
                    }
                },
            )
        }
    }
}

@Composable
private fun BoxedMessage(message: String) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SentenceCard(
    item: DailySentenceCoordinator.QueueItem,
    cardState: FsrsEngine.CardState?,
    revealed: Boolean,
    clozeEnabled: Boolean,
    clozeNewEnabled: Boolean,
    onReveal: () -> Unit,
    onRate: (FsrsEngine.Rating) -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val hidesAnything = remember(item.text) { SentenceCloze.hidesAnything(item.text) }
    val recallFace = clozeEnabled && hidesAnything &&
        ((cardState?.reviewCount ?: 0) > 0 || clozeNewEnabled)
    val showFull = revealed || !recallFace
    val status = when {
        cardState == null || cardState.reviewCount == 0 -> "新句"
        FsrsEngine.isDue(cardState, now) -> Strings.f(
            "到期 · 复习 {0} 次 · 留存率 {1}",
            cardState.reviewCount,
            percent(FsrsEngine.retentionRate(cardState).toFloat()),
        )
        else -> Strings.f(
            "复习 {0} 次 · 留存率 {1}",
            cardState.reviewCount,
            percent(FsrsEngine.retentionRate(cardState).toFloat()),
        )
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp).clickable { if (!showFull) onReveal() },
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.title.isNotBlank()) Strings.f("来源：{0}", item.title) else "句子卡",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.isToday) {
                        Text(
                            "今日",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(220.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (showFull) item.text else SentenceCloze.cue(item.text),
                        style = MaterialTheme.typography.headlineSmall,
                        lineHeight = 36.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!showFull) {
            Button(onClick = onReveal) { Text("显示答案") }
        } else {
            val previous = cardState ?: FsrsEngine.CardState()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    FsrsEngine.Rating.AGAIN to "忘记",
                    FsrsEngine.Rating.HARD to "不熟",
                    FsrsEngine.Rating.GOOD to "记住了",
                ).forEach { (rating, label) ->
                    OutlinedButton(onClick = { onRate(rating) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontWeight = FontWeight.Medium)
                            Text(
                                Strings.f("{0} 天", FsrsEngine.previewIntervalDays(previous, rating, now)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPrevious) { Text("上一张") }
            TextButton(onClick = onSkip) { Text("跳过") }
        }
    }
}

@Composable
private fun FinishedCard(
    queueSize: Int,
    again: Int,
    hard: Int,
    good: Int,
    onAgain: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("本轮完成", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            Strings.f("共 {0} 张 · 忘记 {1} · 不熟 {2} · 记住了 {3}", queueSize, again, hard, good),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAgain) { Text("再来一轮") }
            OutlinedButton(onClick = onHome) { Text("回到首页") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

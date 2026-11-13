package com.ilyskyo.blancall.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.StudyCoach
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.data.repository.HomeLayoutStore
import com.ilyskyo.blancall.data.repository.SentenceCardStore
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.theme.Macaron
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import com.ilyskyo.blancall.desktop.ui.theme.isBlancallDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val HOME_PULL_MAX = 84.dp
private val ROW_UNIT = 92.dp
private val COLUMN_GAP = 12.dp

@Composable
fun HomeScreen(state: AppState) {
    val articles by state.articles.allArticles.collectAsState(initial = emptyList())
    val records by state.records.records.collectAsState(initial = emptyList())
    val revision by state.fsrs.revision.collectAsState()
    val activeRevision by state.fsrs.revision.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<StudyCoach.Snapshot?>(null) }
    var sentence by remember { mutableStateOf<SentenceCardStore.Snapshot?>(null) }
    var cards by remember { mutableStateOf(state.homeLayout.getCards()) }
    var editMode by remember { mutableStateOf(false) }
    var showAddCard by remember { mutableStateOf(false) }

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
    val dueArticles = snapshot?.input?.dueArticles.orEmpty()
    val resumables = snapshot?.input?.resumables.orEmpty()
    val recent = remember(articles, state.prefs.recentArticleIds) {
        val byId = articles.associateBy { it.id }
        val fromPrefs = state.prefs.recentArticleIds.mapNotNull { byId[it] }
        (fromPrefs + articles.sortedByDescending { it.updatedAt }).distinctBy { it.id }
    }
    val totalBlanks = records.sumOf { it.totalBlanks }
    val totalCorrect = records.sumOf { it.correctCount }
    val todayCount = snapshot?.input?.todayPracticeCount ?: 0
    val goal = state.reminderPrefs.dailyPracticeGoal

    fun persist(newCards: List<HomeLayoutStore.Card>) {
        cards = newCards
        state.scope.launch(Dispatchers.IO) { state.homeLayout.saveCards(newCards) }
    }

    // ── 下拉揭示品牌区（与 Android 同款：滚到顶继续下拉，内容跟手下移，松手回弹）──
    val scrollState = rememberScrollState()
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pullMaxPx = with(density) { HOME_PULL_MAX.toPx() }
    fun settlePull() {
        if (pull.value <= 0.5f) return
        scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f)) }
    }
    val nested = remember(scrollState, pullMaxPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy > 0f && scrollState.value <= 0) {
                    scope.launch {
                        pull.snapTo((pull.value + dy * 0.55f).coerceIn(0f, pullMaxPx))
                    }
                    return Offset(0f, dy)
                }
                if (dy != 0f && pull.value > 0f) {
                    scope.launch { pull.snapTo(0f) }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                settlePull()
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 品牌揭示层（内容层之下）
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(HOME_PULL_MAX)
                .graphicsLayer { alpha = (pull.value / pullMaxPx).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Blancall",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "填上空白，想起知识。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pull.value }
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(nested)
                .touchScrollable(
                    state = scrollState,
                    scope = scope,
                    onOverscroll = { dy ->
                        scope.launch {
                            pull.snapTo((pull.value + dy * 0.55f).coerceIn(0f, pullMaxPx))
                        }
                    },
                    onRelease = { settlePull() },
                )
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    placeholder = "搜索标题、正文或日期",
                    onClick = { state.navigate(Screen.Search) },
                    modifier = Modifier.weight(1f),
                    trailing = {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { state.navigate(Screen.Settings) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            )
            Spacer(Modifier.height(18.dp))

            TodayPlanCard(
                plan = plan,
                dueArticleCount = dueArticles.size,
                todayPracticeCount = todayCount,
                dailyPracticeGoal = goal,
                showDailyGoal = state.reminderPrefs.showDailyGoalOnCard,
                onOpenAll = { state.navigate(Screen.StudyFlow) },
                onStartItem = { item -> startPlanItem(state, item) },
            )
            Spacer(Modifier.height(12.dp))

            HomeCardCanvas(
                cards = if (editMode && cards.none { it.type == HomeLayoutStore.CardType.ADD_ARTICLE }) {
                    cards + HomeLayoutStore.Card(HomeLayoutStore.CARD_ID_ADD, HomeLayoutStore.CardType.ADD_ARTICLE)
                } else cards,
                editMode = editMode,
                articles = articles,
                dueArticles = dueArticles,
                resumables = resumables,
                recent = recent,
                sentence = sentence,
                stats = HomeStats(records.size, totalCorrect, totalBlanks, articles.size),
                firstCard = articles.firstOrNull(),
                onOpenArticle = { state.navigate(Screen.Reader(it)) },
                onPracticeArticle = { state.navigate(Screen.Practice(it)) },
                onResume = { state.navigate(Screen.Practice(it)) },
                onOpenSentence = { state.navigate(Screen.SentenceCards()) },
                onOpenStats = { state.navigate(Screen.Statistics()) },
                onOpenDue = { state.navigate(Screen.Articles) },
                onOpenRecentAll = { state.navigate(Screen.Articles) },
                onAddArticle = { state.navigate(Screen.Import) },
                onLongPress = { editMode = true },
                onDelete = { card -> persist(cards.filterNot { it.id == card.id }) },
                onPinToggle = { card ->
                    persist(
                        cards.map {
                            if (it.id != card.id) it
                            else if (it.pinned) it.copy(pinned = false, lockRow = -1, lockCol = -1)
                            else it.copy(pinned = true)
                        }
                    )
                },
            )
            Spacer(Modifier.height(24.dp))
        }

        if (editMode) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 108.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassButton(onClick = { showAddCard = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加卡片", style = MaterialTheme.typography.labelMedium)
                }
                PrimaryPill("完成", onClick = { editMode = false })
            }
        }

        if (showAddCard) {
            AddCardOverlay(
                existing = cards,
                onDismiss = { showAddCard = false },
                onAdd = { card ->
                    if (cards.none { it.id == card.id }) {
                        persist(cards + card)
                    }
                    showAddCard = false
                },
            )
        }
    }
}

private data class HomeStats(
    val practices: Int,
    val correct: Int,
    val blanks: Int,
    val articles: Int,
) {
    val rate: Float get() = if (blanks <= 0) 0f else correct.toFloat() / blanks
}

@Composable
private fun TodayPlanCard(
    plan: StudyCoach.Plan?,
    dueArticleCount: Int,
    todayPracticeCount: Int,
    dailyPracticeGoal: Int,
    showDailyGoal: Boolean,
    onOpenAll: () -> Unit,
    onStartItem: (StudyCoach.Item) -> Unit,
) {
    GlassCard(onClick = onOpenAll) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("今日计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        plan == null -> "正在计算今日计划…"
                        plan.items.isEmpty() -> "今天没有必须完成的内容，自由阅读也可以。"
                        plan.examDaysLeft != null -> Strings.f(
                            "距考试 {0} 天 · 今日约 {1} 分钟",
                            plan.examDaysLeft,
                            plan.estimatedMinutes,
                        )
                        else -> Strings.f("按到期、未完成与薄弱项排好，约 {0} 分钟", plan.estimatedMinutes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dueArticleCount > 0) {
                Text(
                    Strings.f("{0} 篇到期", dueArticleCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (showDailyGoal && dailyPracticeGoal > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                Strings.f("今日目标 {0}/{1}", todayPracticeCount, dailyPracticeGoal),
                style = MaterialTheme.typography.labelSmall,
                color = if (todayPracticeCount >= dailyPracticeGoal) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
        if (plan == null || plan.items.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "没有到期内容。新导入的文章会在完成第一次练习后进入记忆排期。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(6.dp))
            plan.items.forEach { item ->
                val src = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SmoothRoundedCornerShape(12.dp))
                        .clickable(interactionSource = src, indication = null) { onStartItem(item) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            itemLabel(item),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                }
            }
        }
    }
}

private fun itemLabel(item: StudyCoach.Item): String = when (item.kind) {
    StudyCoach.Kind.RESUME -> Strings.f("继续练习 · 还剩 {0} 空", item.count)
    StudyCoach.Kind.REVIEW_ARTICLE -> Strings.f("复习 · {0} 空", item.count)
    StudyCoach.Kind.START_ARTICLE -> Strings.f("开始学习 · {0} 空", item.count)
    StudyCoach.Kind.WEAK_ARTICLE -> Strings.f("薄弱篇 · {0} 处错", item.count)
    StudyCoach.Kind.SENTENCE_CARDS -> Strings.f("句子卡 · {0} 句", item.count)
    StudyCoach.Kind.MISTAKE_DRILL -> Strings.f("错题集训 · {0} 处", item.count)
    StudyCoach.Kind.DAILY_GOAL -> "补足今天的练习次数"
}

private fun startPlanItem(state: AppState, item: StudyCoach.Item) {
    when (item.kind) {
        StudyCoach.Kind.RESUME -> if (item.articleId > 0) state.navigate(Screen.Practice(item.articleId))
        StudyCoach.Kind.REVIEW_ARTICLE -> if (item.articleId > 0) state.navigate(Screen.Practice(item.articleId))
        StudyCoach.Kind.START_ARTICLE -> if (item.articleId > 0) state.navigate(Screen.Practice(item.articleId))
        StudyCoach.Kind.WEAK_ARTICLE -> if (item.articleId > 0) state.navigate(Screen.Practice(item.articleId))
        StudyCoach.Kind.SENTENCE_CARDS -> state.navigate(Screen.SentenceCards())
        StudyCoach.Kind.MISTAKE_DRILL -> state.navigate(Screen.Statistics())
        StudyCoach.Kind.DAILY_GOAL -> state.navigate(Screen.Articles)
    }
}

@Composable
private fun HomeCardCanvas(
    cards: List<HomeLayoutStore.Card>,
    editMode: Boolean,
    articles: List<Article>,
    dueArticles: List<StudyCoach.DueArticle>,
    resumables: List<StudyCoach.Resumable>,
    recent: List<Article>,
    sentence: SentenceCardStore.Snapshot?,
    stats: HomeStats,
    firstCard: Article?,
    onOpenArticle: (Long) -> Unit,
    onPracticeArticle: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onOpenSentence: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDue: () -> Unit,
    onOpenRecentAll: () -> Unit,
    onAddArticle: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: (HomeLayoutStore.Card) -> Unit,
    onPinToggle: (HomeLayoutStore.Card) -> Unit,
) {
    val rows = remember(cards) { arrangeRows(cards) }
    Column(verticalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
                row.forEach { card ->
                    val height = ROW_UNIT * card.rowSpan - 10.dp
                    Box(
                        Modifier
                            .weight(card.colSpan.toFloat())
                            .height(height)
                            .combinedClickableCard(
                                onClick = {
                                    if (editMode) return@combinedClickableCard
                                    when (card.type) {
                                        HomeLayoutStore.CardType.ADD_ARTICLE -> onAddArticle()
                                        HomeLayoutStore.CardType.DUE -> onOpenDue()
                                        HomeLayoutStore.CardType.CONTINUE -> resumables.firstOrNull()
                                            ?.let { onResume(it.articleId) }
                                        HomeLayoutStore.CardType.RECENT -> firstCard?.let { onOpenArticle(it.id) }
                                        HomeLayoutStore.CardType.SENTENCE -> onOpenSentence()
                                        HomeLayoutStore.CardType.STATS,
                                        HomeLayoutStore.CardType.GLOBAL_STATS -> onOpenStats()
                                        HomeLayoutStore.CardType.ARTICLE ->
                                            if (card.refId > 0) onOpenArticle(card.refId)
                                        HomeLayoutStore.CardType.CUSTOM_CLOZE ->
                                            if (card.articleId > 0) onPracticeArticle(card.articleId)
                                        HomeLayoutStore.CardType.CUSTOM_MASK ->
                                            if (card.articleId > 0) onOpenArticle(card.articleId)
                                    }
                                },
                                onLongClick = onLongPress,
                            ),
                    ) {
                        HomeCardBody(
                            card = card,
                            articles = articles,
                            dueArticles = dueArticles,
                            resumables = resumables,
                            recent = recent,
                            sentence = sentence,
                            stats = stats,
                            onOpenArticle = onOpenArticle,
                            onPracticeArticle = onPracticeArticle,
                            onResume = onResume,
                            onOpenSentence = onOpenSentence,
                            onOpenStats = onOpenStats,
                            onOpenDue = onOpenDue,
                            onOpenRecentAll = onOpenRecentAll,
                            onAddArticle = onAddArticle,
                        )
                        if (editMode) {
                            Row(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                EditCircle(
                                    icon = Icons.Outlined.PushPin,
                                    tint = if (card.pinned) MaterialTheme.colorScheme.primary else Color.Black,
                                    onClick = { onPinToggle(card) },
                                )
                                EditCircle(
                                    icon = Icons.Outlined.Close,
                                    tint = Color.Black,
                                    onClick = { onDelete(card) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.combinedClickableCard(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    return this
        .pressFeedback(src)
        .clip(SmoothRoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            SmoothRoundedCornerShape(20.dp),
        )
        .combinedClickable(
            interactionSource = src,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

@Composable
private fun EditCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun HomeCardBody(
    card: HomeLayoutStore.Card,
    articles: List<Article>,
    dueArticles: List<StudyCoach.DueArticle>,
    resumables: List<StudyCoach.Resumable>,
    recent: List<Article>,
    sentence: SentenceCardStore.Snapshot?,
    stats: HomeStats,
    onOpenArticle: (Long) -> Unit,
    onPracticeArticle: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onOpenSentence: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDue: () -> Unit,
    onOpenRecentAll: () -> Unit,
    onAddArticle: () -> Unit,
) {
    val dark = isBlancallDark()
    val short = card.rowSpan == 1
    val pad = if (short) 12.dp else 14.dp
    Column(Modifier.fillMaxSize().padding(pad)) {
        when (card.type) {
            HomeLayoutStore.CardType.DUE -> {
                CardHeader("待复习", Macaron.accent(3, dark))
                if (short) {
                    Text(
                        if (dueArticles.isEmpty()) "今天没有到期" else Strings.f("{0} 篇", dueArticles.size),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                } else {
                    Text(
                        dueArticles.size.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (dueArticles.isEmpty()) "今天没有到期文章"
                        else Strings.f("最早到期 · {0}", dueArticles.minByOrNull { it.due }?.title.orEmpty()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HomeLayoutStore.CardType.CONTINUE -> {
                CardHeader("继续练习", Macaron.accent(1, dark))
                val item = resumables.firstOrNull()
                if (item == null) {
                    Text("没有未完成的练习", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = if (short) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Strings.f("还剩 {0} 空", (item.total - item.answered).coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HomeLayoutStore.CardType.RECENT -> {
                CardHeader("最近文章", Macaron.accent(2, dark))
                if (recent.isEmpty()) {
                    CardEmpty("还没有文章", "导入第一篇开始背诵")
                } else {
                    val limit = if (short) 1 else 3
                    recent.take(limit).forEach { article ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(SmoothRoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onOpenArticle(article.id) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                article.title.ifBlank { "未命名" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatTime(article.updatedAt).take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "查看全部",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenRecentAll,
                        ),
                    )
                }
            }

            HomeLayoutStore.CardType.SENTENCE -> {
                CardHeader("今日一句", Macaron.accent(4, dark))
                if (sentence == null) {
                    Text("还没有可抽取的句子", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        sentence.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (short) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        sentence.title.ifBlank { "未命名文章" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HomeLayoutStore.CardType.STATS,
            HomeLayoutStore.CardType.GLOBAL_STATS -> {
                CardHeader("学习数据", Macaron.accent(0, dark))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatColumn("练习", stats.practices.toString(), Modifier.weight(1f))
                    StatColumn("正确率", if (stats.blanks <= 0) "—" else percent(stats.rate), Modifier.weight(1f))
                    StatColumn("字数", stats.blanks.toString(), Modifier.weight(1f))
                }
                if (short) {
                    Text(
                        Strings.f("{0} 篇文章", stats.articles),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HomeLayoutStore.CardType.ADD_ARTICLE -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("添加文章", style = MaterialTheme.typography.labelMedium)
                }
            }

            HomeLayoutStore.CardType.ARTICLE -> {
                val article = articles.firstOrNull { it.id == card.refId }
                if (article == null) {
                    CardEmpty("文章已删除", "长按卡片可移除")
                } else {
                    Text(
                        article.title.ifBlank { "未命名" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = if (short) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        article.content.replace('\n', ' ').take(if (short) 30 else 80),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (short) 1 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HomeLayoutStore.CardType.CUSTOM_CLOZE,
            HomeLayoutStore.CardType.CUSTOM_MASK -> {
                CardHeader(
                    if (card.type == HomeLayoutStore.CardType.CUSTOM_CLOZE) "自定义挖空" else "自定义遮挡",
                    Macaron.accent(5, dark),
                )
                Text(
                    card.title.ifBlank { "未命名配置" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (card.type == HomeLayoutStore.CardType.CUSTOM_CLOZE) "点击开始练习" else "点击进入阅读",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CardHeader(title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun CardEmpty(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(
        subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
    )
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddCardOverlay(
    existing: List<HomeLayoutStore.Card>,
    onDismiss: () -> Unit,
    onAdd: (HomeLayoutStore.Card) -> Unit,
) {
    val options = listOf(
        Triple(HomeLayoutStore.CARD_ID_DUE, HomeLayoutStore.CardType.DUE, "待复习"),
        Triple(HomeLayoutStore.CARD_ID_CONTINUE, HomeLayoutStore.CardType.CONTINUE, "继续练习"),
        Triple(HomeLayoutStore.CARD_ID_RECENT, HomeLayoutStore.CardType.RECENT, "最近文章"),
        Triple(HomeLayoutStore.CARD_ID_SENTENCE, HomeLayoutStore.CardType.SENTENCE, "今日一句"),
        Triple(HomeLayoutStore.CARD_ID_STATS, HomeLayoutStore.CardType.STATS, "学习数据"),
        Triple(HomeLayoutStore.CARD_ID_GLOBAL_STATS, HomeLayoutStore.CardType.GLOBAL_STATS, "全局数据"),
        Triple(HomeLayoutStore.CARD_ID_ADD, HomeLayoutStore.CardType.ADD_ARTICLE, "添加文章入口"),
    ).filter { (id, _, _) -> existing.none { it.id == id } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Text("添加卡片", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "长按首页卡片可进入编辑态，移动顺序稍后可用拖动完成。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            if (options.isEmpty()) {
                Text("所有卡片都已添加。", style = MaterialTheme.typography.bodyMedium)
            } else {
                options.forEach { (id, type, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onAdd(HomeLayoutStore.Card(id = id, type = type)) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun arrangeRows(cards: List<HomeLayoutStore.Card>): List<List<HomeLayoutStore.Card>> {
    val rows = mutableListOf<MutableList<HomeLayoutStore.Card>>()
    var used = 2
    cards.forEach { card ->
        val span = card.colSpan.coerceIn(1, 2)
        if (span >= 2 || used + span > 2) {
            rows += mutableListOf(card)
            used = span
        } else {
            rows.last().add(card)
            used += span
        }
    }
    return rows
}

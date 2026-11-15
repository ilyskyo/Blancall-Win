package com.ilyskyo.blancall.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlin.math.cos
import kotlin.math.sin

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
                    placeholder = "搜索",
                    onClick = { state.navigate(Screen.Search) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                // 与 Android 一致：设置是独立的 48dp 方钮，不再塞在搜索框内部
                DesktopTooltip("设置") {
                    GlassButton(
                        onClick = { state.navigate(Screen.Settings) },
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        SettingsGearIcon(
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(22.dp)
                                .semantics { contentDescription = "设置" },
                        )
                    }
                }
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
                onOpenAll = { state.navigate(Screen.TodayPlan) },
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
                onOpenDue = { state.navigate(Screen.DueList) },
                onOpenRecentAll = { state.navigate(Screen.Articles) },
                onAddArticle = { state.navigate(Screen.Import) },
                onLongPress = { editMode = true },
                onDelete = { card -> persist(cards.filterNot { it.id == card.id }) },
                onPinToggle = { card, row, col ->
                    persist(
                        cards.map {
                            if (it.id != card.id) it
                            else if (it.pinned) it.copy(pinned = false, lockRow = -1, lockCol = -1)
                            else it.copy(pinned = true, lockRow = row, lockCol = col)
                        }
                    )
                },
                onResize = { card, colSpan, rowSpan ->
                    persist(cards.map {
                        if (it.id == card.id) it.copy(colSpan = colSpan, rowSpan = rowSpan) else it
                    })
                },
                onMove = { id, delta ->
                    val from = cards.indexOfFirst { it.id == id }
                    val to = (from + delta).coerceIn(0, cards.lastIndex)
                    if (from >= 0 && to != from) {
                        val list = cards.toMutableList()
                        val item = list.removeAt(from)
                        list.add(to, item)
                        // 拖动就是要换位置：继续沿用旧锁位会让卡「拖了但没动」
                        persist(list.map {
                            if (it.id == id) it.copy(pinned = false, lockRow = -1, lockCol = -1) else it
                        })
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }

        DesktopScrollbar(
            scrollState,
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 12.dp, bottom = 100.dp, end = 4.dp),
        )

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
    onPinToggle: (HomeLayoutStore.Card, Int, Int) -> Unit,
    onResize: (HomeLayoutStore.Card, Int, Int) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 列数只看画布可用宽度：桌面窗口可以比平板更宽，也可以缩到手机宽度
        val columns = homeGridColumns(maxWidth.value)
        val slots = remember(cards, columns) { layoutHomeSlots(cards, columns) }
        val totalRows = remember(cards, slots) { homeSlotRows(cards, slots) }
        val colWidth = ((maxWidth - COLUMN_GAP * (columns - 1)) / columns).coerceAtLeast(0.dp)
        val gridHeight = if (totalRows <= 0) 0.dp
        else (ROW_UNIT + COLUMN_GAP) * totalRows - COLUMN_GAP

        Box(Modifier.fillMaxWidth().height(gridHeight)) {
            cards.forEach { card ->
                val slot = slots[card.id] ?: return@forEach
                val colSpan = card.colSpan.coerceIn(
                    HomeLayoutStore.MIN_COL_SPAN, HomeLayoutStore.MAX_COL_SPAN
                )
                val rowSpan = card.rowSpan.coerceIn(
                    HomeLayoutStore.MIN_ROW_SPAN, HomeLayoutStore.MAX_ROW_SPAN
                )
                val x = (colWidth + COLUMN_GAP) * slot[1]
                val y = (ROW_UNIT + COLUMN_GAP) * slot[0]
                val w = colWidth * colSpan + COLUMN_GAP * (colSpan - 1)
                val h = (ROW_UNIT + COLUMN_GAP) * rowSpan - COLUMN_GAP

                Box(
                    Modifier
                        .offset(x, y)
                        .size(w, h)
                        .graphicsLayer { translationY = if (dragId == card.id) dragDy else 0f }
                        .zIndex(if (dragId == card.id) 5f else 0f)
                        .combinedClickableCard(
                            color = cardFill(card),
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
                                onClick = { onPinToggle(card, slot[0], slot[1]) },
                            )
                            EditCircle(
                                icon = Icons.Outlined.Close,
                                tint = Color.Black,
                                onClick = { onDelete(card) },
                            )
                        }
                        Row(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            EditCircleText("⤢") {
                                val presets = listOf(2 to 1, 1 to 1, 1 to 2, 2 to 2)
                                val current = card.colSpan to card.rowSpan
                                val i = presets.indexOf(current).takeIf { it >= 0 } ?: -1
                                val next = presets[(i + 1).mod(presets.size)]
                                onResize(card, next.first, next.second)
                            }
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.94f))
                                    .pointerInput(card.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragId = card.id
                                                dragDy = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragDy += amount.y
                                            },
                                            onDragEnd = {
                                                if (abs(dragDy) > 56.dp.toPx()) {
                                                    onMove(card.id, if (dragDy > 0f) 1 else -1)
                                                }
                                                dragId = null
                                                dragDy = 0f
                                            },
                                            onDragCancel = {
                                                dragId = null
                                                dragDy = 0f
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("⠿", style = MaterialTheme.typography.labelMedium, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 卡片底色：与 Android 的 Macaron 映射一致。
 * 白卡（最近文章）保持 surface，靠 1dp 描边分层。
 */
@Composable
private fun cardFill(card: HomeLayoutStore.Card): Color {
    val dark = isBlancallDark()
    return when (card.type) {
        HomeLayoutStore.CardType.DUE -> Macaron.fill(0, dark)
        HomeLayoutStore.CardType.CONTINUE -> Macaron.fill(1, dark)
        HomeLayoutStore.CardType.ARTICLE -> Macaron.fill(3, dark)
        HomeLayoutStore.CardType.CUSTOM_CLOZE -> Macaron.fill(4, dark)
        HomeLayoutStore.CardType.CUSTOM_MASK -> Macaron.fill(2, dark)
        HomeLayoutStore.CardType.ADD_ARTICLE -> Macaron.fill(2, dark)
        HomeLayoutStore.CardType.STATS -> Macaron.fill(4, dark)
        HomeLayoutStore.CardType.GLOBAL_STATS -> Macaron.fill(5, dark)
        HomeLayoutStore.CardType.SENTENCE -> Macaron.fill(2, dark)
        HomeLayoutStore.CardType.RECENT -> MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun Modifier.combinedClickableCard(
    color: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val shape = SmoothRoundedCornerShape(24.dp)
    return this
        .pressFeedback(src)
        .clip(shape)
        .background(color)
        .border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            shape,
        )
        .combinedClickable(
            interactionSource = src,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

@Composable
private fun EditCircleText(text: String, onClick: () -> Unit) {
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
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.Black)
    }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(6.dp))
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

/**
 * 自绘设置齿轮（与 Android 同一条轮廓）：
 * 8 齿、齿顶/齿根 smoothstep 过渡、1.5dp 圆头描边；小尺寸下不会出现锯齿。
 */
@Composable
private fun SettingsGearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sc = size.minDimension / 24f
        val c = center
        val teeth = 8
        val period = 360f / teeth
        val tipRadius = 9.3f * sc
        val rootRadius = 7.3f * sc
        val tipHalf = 9.5f
        val rootHalf = 15.5f

        fun smoothstep(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }

        fun radiusAt(deg: Float): Float {
            val offset = ((deg + period / 2f) % period) - period / 2f
            val u = abs(offset)
            return when {
                u <= tipHalf -> tipRadius
                u >= rootHalf -> rootRadius
                else -> tipRadius + (rootRadius - tipRadius) *
                    smoothstep((u - tipHalf) / (rootHalf - tipHalf))
            }
        }

        val steps = 240
        val path = Path()
        for (i in 0..steps) {
            val deg = i * 360f / steps
            val rad = Math.toRadians((deg - 90f).toDouble())
            val r = radiusAt(deg)
            val x = c.x + (r * cos(rad)).toFloat()
            val y = c.y + (r * sin(rad)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val stroke = Stroke(
            width = 1.5f * sc,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawPath(path, color = color, style = stroke)
        drawCircle(color = color, radius = 3.0f * sc, center = c, style = stroke)
    }
}

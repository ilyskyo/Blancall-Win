package com.ilyskyo.blancall.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.theme.BlancallTheme
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape

@Composable
fun BlancallApp(state: AppState) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * state.prefs.uiScale),
    ) {
        BlancallTheme(
            themeMode = state.prefs.themeMode,
            systemDark = isSystemInDarkTheme(),
            backgroundStyle = state.prefs.backgroundStyle,
            accentIndex = state.prefs.accentIndex,
            uiFont = state.prefs.uiFont,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                AppSurface(state)
                StorageErrorBanner(state)
                ToastOverlay(state)
                if (state.showWelcome) WelcomeOverlay(state)
            }
        }
    }
}

@Composable
private fun AppSurface(state: AppState) {
    val screen = state.screen
    val rootTab = rootTabOf(screen)
    val immersive = screen is Screen.ReadingMode ||
        screen is Screen.Practice || screen is Screen.PracticeCustom ||
        screen is Screen.CrossPractice || screen is Screen.Drill

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 与 Android 同一断点：600dp 起用左侧 rail；窗口可以缩到手机宽度，所以只能看窗口宽度
        val widthClass = widthClassFor(maxWidth)
        val isLarge = widthClass != WidthClass.Compact
        val showNav = rootTab != null
        val showBottomBar = !isLarge && rootTab != null
        val contentMax = when {
            immersive -> PracticeMaxWidth
            // 列表/详情双栏与统计大屏需要比普通卡片网格更宽的内容区；
            // 其余页面仍保持 1100dp 的可读上限。
            isLarge && screen is Screen.Articles -> 1440.dp
            isLarge && screen is Screen.Statistics -> 1400.dp
            isLarge -> GridMaxWidth
            else -> 600.dp
        }
        val bottomInset = if (!isLarge && showNav) 96.dp else 0.dp
        val startInset = if (isLarge && showNav) NavRailWidth else 0.dp

        CompositionLocalProvider(
            LocalWindowWidthClass provides widthClass,
            LocalIsLargeScreen provides isLarge,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = startInset, bottom = bottomInset),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight()
                        .widthIn(max = contentMax)
                        .fillMaxWidth()
                        .swipeBack(
                            enabled = rootTab == null && !immersive && state.canGoBack(),
                            onBack = state::back,
                        ),
                ) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            val toRoot = rootTabOf(targetState) != null
                            val fromRoot = rootTabOf(initialState) != null
                            when {
                                fromRoot && toRoot ->
                                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                state.isBackNavigation ->
                                    (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(160)))
                                else ->
                                    (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(160)))
                            }
                        },
                        label = "page",
                    ) { target ->
                        ScreenContent(state, target)
                    }
                }
            }

            if (showNav) {
                if (isLarge) {
                    BlancallRail(
                        currentTab = rootTab!!.ordinal,
                        onSelect = { tab -> selectRootTab(state, RootTab.entries[tab]) },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                } else {
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(160)),
                        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(140)),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        BlancallBottomBar(
                            currentTab = rootTab!!.ordinal,
                            onSelect = { tab -> selectRootTab(state, RootTab.entries[tab]) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(state: AppState, screen: Screen) {
    when (screen) {
        Screen.Home -> HomeScreen(state)
        Screen.Articles -> ArticlesScreen(state)
        Screen.Import -> ImportScreen(state)
        is Screen.Reader -> ArticleDetailScreen(state, screen.articleId)
        is Screen.ReadingMode -> ReadingModeScreen(state, screen.articleId)
        is Screen.Practice -> PracticeScreen(state, screen.articleId)
        is Screen.PracticeCustom -> PracticeScreen(
            state,
            screen.articleId,
            initialCustomConfigId = screen.configId,
        )
        is Screen.CrossPractice -> CrossPracticeScreen(state, screen.articleIds)
        is Screen.Drill -> DrillScreen(state, screen.articleId)
        is Screen.Statistics -> StatisticsScreen(state, screen.articleId)
        Screen.StudyFlow -> StudyFlowScreen(state)
        Screen.Settings -> SettingsScreen(state)
        Screen.Tags -> TagsScreen(state)
        is Screen.TagHome -> TagHomeScreen(state, screen.tagId)
        is Screen.SentenceCards -> SentenceCardsScreen(state, screen.tagId, screen.articleId)
        Screen.ContentPacks -> ContentPacksScreen(state)
        Screen.Search -> ArticlesScreen(state, focusSearch = true)
        Screen.TodayPlan -> TodayPlanScreen(state)
        Screen.DueList -> DueListScreen(state)
        is Screen.Mistakes -> MistakesScreen(state, screen.articleId)
        is Screen.PracticeHistory -> PracticeHistoryScreen(state, screen.articleId)
        Screen.DataInterchange -> DataInterchangeScreen(state)
        is Screen.CustomClozeList -> CustomClozeListScreen(state, screen.articleId)
        is Screen.CustomClozeEdit -> CustomClozeEditScreen(state, screen.articleId, screen.configId)
        is Screen.MaskConfigList -> MaskConfigListScreen(state, screen.articleId)
        is Screen.MaskConfigEdit -> MaskConfigEditScreen(state, screen.articleId, screen.configId)
        Screen.Guide -> GuideScreen(state)
        Screen.Help -> HelpScreen(state)
        Screen.Privacy -> PrivacyScreen(state)
    }
}

/**
 * 页面归属哪个根 tab。
 *
 * 非沉浸页一律保留导航壳：子页（设置、导入、标签、句子卡、单篇统计）
 * 也必须能一键回首页。只有阅读/练习这类真正抢占注意力的页面才隐藏。
 * 这与 Android 的 `currentRoute` 归属口径一致，但把 Android 没有归 tab
 * 的桌面常用子页（设置/搜索/导入）显式挂到了父级 tab 上。
 */
internal fun rootTabOf(screen: Screen): RootTab? = when (screen) {
    Screen.Home, Screen.Settings, Screen.TodayPlan, Screen.DueList,
    Screen.Guide, Screen.Help, Screen.Privacy,
    is Screen.SentenceCards -> RootTab.HOME
    Screen.Articles, Screen.Search, Screen.Import, Screen.Tags, is Screen.TagHome,
    is Screen.Reader, is Screen.CustomClozeList, is Screen.CustomClozeEdit,
    is Screen.MaskConfigList, is Screen.MaskConfigEdit -> RootTab.ARTICLES
    is Screen.Statistics, Screen.StudyFlow, is Screen.Mistakes,
    is Screen.PracticeHistory, Screen.DataInterchange -> RootTab.STATS
    Screen.ContentPacks -> RootTab.LIBRARY
    else -> null
}

private fun selectRootTab(state: AppState, tab: RootTab) {
    when (tab) {
        RootTab.HOME -> state.goRoot(Screen.Home)
        RootTab.ARTICLES -> state.goRoot(Screen.Articles)
        RootTab.STATS -> state.goRoot(Screen.Statistics())
        RootTab.LIBRARY -> state.goRoot(Screen.ContentPacks)
    }
}

/**
 * 落盘失败横幅：常驻在顶部，直到用户主动关闭。
 *
 * Toast 会消失，用户很容易把「保存失败」当成一次误报；
 * 数据没写进磁盘必须持续可见，而且要把具体路径/原因露出来。
 */
@Composable
private fun StorageErrorBanner(state: AppState) {
    val message = state.storageError
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize().padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (message != null) {
                Row(
                    Modifier
                        .widthIn(max = 760.dp)
                        .padding(horizontal = 16.dp)
                        .clip(SmoothRoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.97f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            SmoothRoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "数据未能写入磁盘",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    GlassButton(onClick = state::dismissStorageError) {
                        Text("关闭", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 应用内提示：底部悬浮胶囊（与 Android 的 Toast 观感一致）。 */
@Composable
private fun ToastOverlay(state: AppState) {
    val message = state.toast
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().padding(bottom = 104.dp), contentAlignment = Alignment.BottomCenter) {
            if (message != null) {
                Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .clip(SmoothRoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

/**
 * 首启欢迎 + 五页引导（ADR-019）。
 *
 * 欢迎页只说明「离线、本机、无账号」；五页引导分别对应导入、阅读、练习、
 * 记忆与数据。任何一步都能跳过，隐私说明可直接打开，不要求点「同意」。
 */
@Composable
private fun WelcomeOverlay(state: AppState) {
    var step by remember { mutableIntStateOf(0) }
    val total = guidePages.size + 1
    fun finish() = state.dismissWelcome()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            Modifier
                .padding(24.dp)
                .widthIn(max = 560.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(26.dp),
        ) {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                if (step == 0) {
                    Text("欢迎使用 Blancall", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "填上空白，想起知识。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    listOf(
                        "把文章挖空，用 FSRS-6 间隔重复记住它。",
                        "所有数据保存在本机，无账号、无网络请求、无遥测。",
                        "关闭窗口可选择最小化到托盘，学习提醒会继续工作。",
                        "中文手写模型如随包分发，基于 CASIA-HWDB，仅限学术研究。",
                    ).forEach { line ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .height(5.dp)
                                    .width(5.dp)
                                    .clip(SmoothRoundedCornerShape(2.5.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    val page = guidePages[(step - 1).coerceIn(0, guidePages.lastIndex)]
                    Text(page.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    page.body.forEach { paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        Strings.f("{0}/{1}", step + 1, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (step == 0) {
                        GlassButton(onClick = {
                            finish()
                            state.navigate(Screen.Privacy)
                        }) {
                            Text("隐私说明", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    GlassButton(onClick = { finish() }) {
                        Text("跳过", style = MaterialTheme.typography.labelMedium)
                    }
                    if (step > 0) {
                        GlassButton(onClick = { step-- }) {
                            Text("上一步", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    PrimaryPill(
                        if (step >= total - 1) "开始使用" else "下一步",
                        onClick = {
                            if (step >= total - 1) finish() else step++
                        },
                    )
                }
            }
        }
    }
}

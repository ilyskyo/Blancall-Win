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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AppSurface(state)
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
    val immersive = screen is Screen.Reader || screen is Screen.Practice ||
        screen is Screen.CrossPractice || screen is Screen.Drill

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isLarge = maxWidth >= 840.dp
        val showNav = rootTab != null
        val contentMax = when {
            immersive -> 980.dp
            isLarge -> 1080.dp
            else -> 600.dp
        }
        val bottomInset = if (!isLarge && showNav) 96.dp else 0.dp
        val startInset = if (isLarge && showNav) 76.dp else 0.dp

        Box(
            Modifier
                .fillMaxSize()
                .padding(start = startInset, bottom = bottomInset),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = contentMax)
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
                BlancallBottomBar(
                    currentTab = rootTab!!.ordinal,
                    onSelect = { tab -> selectRootTab(state, RootTab.entries[tab]) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
        is Screen.Reader -> ReaderScreen(state, screen.articleId)
        is Screen.Practice -> PracticeScreen(state, screen.articleId)
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
    }
}

private fun rootTabOf(screen: Screen): RootTab? = when (screen) {
    Screen.Home -> RootTab.HOME
    Screen.Articles -> RootTab.ARTICLES
    is Screen.Statistics -> if (screen.articleId == null) RootTab.STATS else null
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

/** 首启欢迎层：模态遮罩 + 玻璃卡，而不是 Material 默认对话框。 */
@Composable
private fun WelcomeOverlay(state: AppState) {
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
                .widthIn(max = 420.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        ) {
            Text("欢迎使用 Blancall", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "填上空白，想起知识。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WelcomeLine("把文章挖空，用 FSRS-6 间隔重复记住它。")
                WelcomeLine("所有数据保存在本机，无账号、无网络请求、无遥测。")
                WelcomeLine("关闭窗口可选择最小化到托盘，学习提醒会继续工作。")
                WelcomeLine("手写模型如随包分发，基于 CASIA-HWDB，仅限学术研究。")
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryPill("开始使用", onClick = { state.dismissWelcome() })
            }
        }
    }
}

@Composable
private fun WelcomeLine(text: String) {
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
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
        )
    }
}

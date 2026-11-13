package com.ilyskyo.blancall.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ilyskyo.blancall.desktop.ui.theme.LocalAccent
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import com.ilyskyo.blancall.desktop.ui.theme.isBlancallDark
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 根 tab 定义（与 Android 一致：首页 / 文章 / 数据 / 素材库）。 */
enum class RootTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Outlined.Home),
    ARTICLES("文章", Icons.AutoMirrored.Outlined.Article),
    STATS("数据", Icons.Outlined.Insights),
    LIBRARY("素材库", Icons.Outlined.AutoStories),
}

val RootTab.hasLibraryTab: Boolean get() = this == RootTab.LIBRARY

private val BarHeight = 64.dp
private val BarHorizontalMargin = 14.dp
private val BarBottomMargin = 14.dp
private val IndicatorInset = 4.dp

/** Android 同款悬浮液态玻璃底栏（桌面用半透明玻璃兜底）。 */
@Composable
fun BlancallBottomBar(
    currentTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = RootTab.entries
    val count = tabs.size
    val accent = LocalAccent.current
    val dark = isBlancallDark()
    val shape = RoundedCornerShape(50)
    val barTint = if (dark) Color.Black.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.88f)
    val borderColor = if (dark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f)
    val indicatorTint = accent.copy(alpha = if (dark) 0.24f else 0.14f)
    val selectedIndex = currentTab.coerceIn(0, count - 1)

    val position = remember { Animatable(selectedIndex.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedIndex, count) {
        position.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.82f, stiffness = 520f))
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BarHorizontalMargin, vertical = BarBottomMargin)
            .height(BarHeight)
            .shadow(
                14.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.22f),
            )
            .clip(shape)
            .background(barTint)
            .border(1.dp, borderColor, shape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabW = maxWidth / count
            // 选中胶囊
            Box(
                Modifier
                    .offset { IntOffset((position.value * tabW.toPx()).roundToInt(), 0) }
                    .width(tabW)
                    .fillMaxHeight()
                    .padding(horizontal = IndicatorInset, vertical = 4.dp)
                    .zIndex(1f),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(indicatorTint)
                        .border(
                            1.5.dp,
                            if (dark) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.80f),
                            shape,
                        ),
                )
            }

            Row(Modifier.fillMaxSize().zIndex(2f)) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    Column(
                        Modifier
                            .width(tabW)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 手势层：轻点 / 水平拖动换 tab
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .pointerInput(count, tabW) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            down.consume()
                            val startTab = (down.position.x / tabW.toPx()).toInt().coerceIn(0, count - 1)
                            val base = position.value
                            val startX = down.position.x
                            var dragging = false
                            var lastX = startX
                            var committed = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val dx = change.position.x - startX
                                    if (!dragging && abs(dx) > viewConfiguration.touchSlop) {
                                        dragging = true
                                    }
                                    if (dragging) {
                                        change.consume()
                                        val t = (base + (change.position.x - lastX) / tabW.toPx())
                                            .coerceIn(0f, (count - 1).toFloat())
                                        scope.launch { position.snapTo(t) }
                                        lastX = change.position.x
                                    }
                                    if (!change.pressed) {
                                        val target = if (dragging) {
                                            position.value.roundToInt().coerceIn(0, count - 1)
                                        } else {
                                            startTab
                                        }
                                        committed = true
                                        scope.launch {
                                            position.animateTo(
                                                target.toFloat(),
                                                spring(dampingRatio = 0.82f, stiffness = 520f),
                                            )
                                        }
                                        if (target != currentTab) onSelect(target)
                                        change.consume()
                                        break
                                    }
                                }
                            } finally {
                                if (!committed) {
                                    scope.launch {
                                        position.animateTo(
                                            currentTab.toFloat(),
                                            spring(dampingRatio = 0.82f, stiffness = 520f),
                                        )
                                    }
                                }
                            }
                        }
                    },
            )
        }
    }
}

private val RailWidth = 76.dp
private val RailItemHeight = 64.dp
private val RailSideMargin = 6.dp
private val RailTopMargin = 12.dp
private val RailInsetV = 8.dp
private val RailCorner = 26.dp
private val RailIndicatorWidth = 48.dp
private val RailIndicatorHeight = 32.dp

/** Android 同款大屏侧栏坞：按内容高度收缩、贴内容区顶部，选中项只包图标。 */
@Composable
fun BlancallRail(
    currentTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = RootTab.entries
    val count = tabs.size
    val accent = LocalAccent.current
    val dark = isBlancallDark()
    val railShape = SmoothRoundedCornerShape(RailCorner)
    val indicatorShape = SmoothRoundedCornerShape(16.dp)
    val tint = if (dark) Color.Black.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.86f)
    val borderColor = if (dark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f)
    val indicatorTint = accent.copy(alpha = if (dark) 0.22f else 0.14f)
    val selectedIndex = currentTab.coerceIn(0, count - 1)

    val position = remember { Animatable(selectedIndex.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedIndex, count) {
        position.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.82f, stiffness = 520f))
    }

    val dockHeight = RailItemHeight * count + RailInsetV * 2
    Box(
        modifier
            .width(RailWidth)
            .fillMaxHeight()
            .padding(horizontal = RailSideMargin, vertical = RailTopMargin),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(dockHeight)
                .shadow(14.dp, railShape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(railShape)
                .background(tint)
                .border(1.dp, borderColor, railShape),
        ) {
            // 选中胶囊：只包图标
            Box(
                Modifier
                    .offset {
                        val top = RailInsetV.toPx() + RailItemHeight.toPx() * position.value +
                            (RailItemHeight - RailIndicatorHeight).toPx() / 2f
                        IntOffset(0, top.roundToInt())
                    }
                    .fillMaxWidth()
                    .padding(horizontal = (RailWidth - RailSideMargin * 2 - RailIndicatorWidth) / 2)
                    .height(RailIndicatorHeight)
                    .zIndex(1f)
                    .clip(indicatorShape)
                    .background(indicatorTint)
                    .border(
                        1.dp,
                        if (dark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.65f),
                        indicatorShape,
                    ),
            )
            Column(
                Modifier.fillMaxSize().padding(vertical = RailInsetV).zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    Column(
                        Modifier.height(RailItemHeight).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .pointerInput(count) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            down.consume()
                            val unit = RailItemHeight.toPx()
                            val startIndex = ((down.position.y - RailInsetV.toPx()) / unit)
                                .toInt().coerceIn(0, count - 1)
                            val base = position.value
                            val startY = down.position.y
                            var dragging = false
                            var lastY = startY
                            var committed = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val dy = change.position.y - startY
                                    if (!dragging && abs(dy) > viewConfiguration.touchSlop) dragging = true
                                    if (dragging) {
                                        change.consume()
                                        val t = (base + (change.position.y - lastY) / unit)
                                            .coerceIn(0f, (count - 1).toFloat())
                                        scope.launch { position.snapTo(t) }
                                        lastY = change.position.y
                                    }
                                    if (!change.pressed) {
                                        val target = if (dragging) {
                                            position.value.roundToInt().coerceIn(0, count - 1)
                                        } else startIndex
                                        committed = true
                                        if (target != currentTab) onSelect(target)
                                        change.consume()
                                        break
                                    }
                                }
                            } finally {
                                if (!committed) {
                                    scope.launch {
                                        position.animateTo(
                                            currentTab.toFloat(),
                                            spring(dampingRatio = 0.82f, stiffness = 520f),
                                        )
                                    }
                                }
                            }
                        }
                    },
            )
        }
    }
}

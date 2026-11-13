package com.ilyskyo.blancall.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.desktop.ui.theme.LocalAccent
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import com.ilyskyo.blancall.desktop.ui.theme.isBlancallDark
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── 通用排版与卡片 ──

/** 与 Android 同款的卡片容器：纯色 surface + 1dp 描边 + 超椭圆大圆角。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = SmoothRoundedCornerShape(20.dp),
    containerColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val clickable = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            interactionSource = src,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        onClick != null -> Modifier.clickable(
            interactionSource = src,
            indication = null,
            onClick = onClick,
        )
        else -> Modifier
    }
    val press = if (onClick != null || onLongClick != null) {
        Modifier.pressFeedback(src)
    } else Modifier
    val color = containerColor ?: MaterialTheme.colorScheme.surface
    Box(
        modifier
            .then(press)
            .then(clickable)
            .clip(shape)
            .background(color)
            .border(
                1.dp,
                (borderColor ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = 0.5f),
                shape,
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/** 按压下沉反馈：缩放 + 轻微变暗，取代 Material 涟漪（桌面/触屏都适用）。 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 900f),
        label = "pressScale",
    )
    val dim by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(90),
        label = "pressDim",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = dim
    }
}

/** 磨砂玻璃按钮（无渐变）：半透明 surface + 细描边 + 14dp 圆角。 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val shape = SmoothRoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .pressFeedback(src)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .clickable(
                interactionSource = src,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** iOS 风格开关（与 Android GlassSwitch 同尺寸：52×32，滑块 28，边距 2）。 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val dark = isBlancallDark()
    val track by animateColorAsState(
        targetValue = when {
            checked -> accent
            dark -> Color(0xFF636366)
            else -> Color(0xFFE3E3E8)
        },
        animationSpec = tween(160),
        label = "switchTrack",
    )
    val trackW = 52.dp
    val trackH = 32.dp
    val gap = 2.dp
    val thumbD = trackH - gap * 2
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackW - gap - thumbD else gap,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "switchThumb",
    )
    val src = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(trackW, trackH)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(
                interactionSource = src,
                indication = null,
                enabled = enabled && onCheckedChange != null,
            ) { onCheckedChange?.invoke(!checked) },
    ) {
        Box(Modifier.matchParentSize().background(track, CircleShape))
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = thumbX.toPx() }
                .size(thumbD)
                .shadow(1.dp, CircleShape, clip = false)
                .background(Color.White, CircleShape),
        )
    }
}

/** Android 版返回键：48dp 触控区 + 40dp 圆底。 */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isBlancallDark()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed) 1.dp else 4.dp, tween(120), label = "backLift")
    Box(
        modifier = modifier
            .size(48.dp)
            .pressFeedback(src, pressedScale = 0.92f)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .shadow(if (dark) 0.dp else lift, CircleShape, clip = false)
                .background(if (dark) Color.White.copy(alpha = 0.14f) else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = if (dark) Color.White else Color.Black,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 子页顶栏：返回键 + headlineMedium 标题 + 可选操作。全局统一 20dp 外边距。 */
@Composable
fun BlancallTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackButton(onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 根 tab 页标题（无返回键，与「我的文章 / 数据 / 设置」同字号同位置）。 */
@Composable
fun RootPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 首页搜索栏（常驻）：玻璃胶囊 + 搜索占位 + 右侧设置入口。 */
@Composable
fun SearchBar(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(SmoothRoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                SmoothRoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 可输入搜索框（文章列表用），风格与首页搜索栏一致。 */
@Composable
fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = SmoothRoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 标签胶囊。 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** 分段选择（浅色玻璃槽 + 选中胶囊）。 */
@Composable
fun <T> SegmentedPills(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(SmoothRoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(SmoothRoundedCornerShape(11.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(value) }
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 主要动作胶囊（Android 的强调色按钮）。 */
@Composable
fun PrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressFeedback(src)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── 触屏滚动 / 侧滑返回 ──

/**
 * Compose Desktop 默认只有鼠标滚轮能滚动，手指拖动不会带动列表。
 * 这个修饰符把 Touch/Stylus 的竖向拖动转换成滚动 + 惯性，触屏笔记本上才像平板。
 */
fun Modifier.touchScrollable(
    state: androidx.compose.foundation.gestures.ScrollableState,
    scope: kotlinx.coroutines.CoroutineScope,
    onOverscroll: ((Float) -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
): Modifier = pointerInput(state, scope, onOverscroll, onRelease) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
        var lastY = down.position.y
        var lastTime = down.uptimeMillis
        var velocityY = 0f
        var dragging = false
        val slop = viewConfiguration.touchSlop
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dyTotal = change.position.y - down.position.y
                if (!dragging) {
                    if (kotlin.math.abs(dyTotal) > slop) {
                        dragging = true
                        change.consume()
                    } else if (!change.pressed) {
                        break
                    } else {
                        continue
                    }
                }
                if (dragging) {
                    val now = change.uptimeMillis
                    val dt = (now - lastTime).coerceAtLeast(1L)
                    val dy = change.position.y - lastY
                    if (dt > 0) velocityY = (dy / dt) * 1000f
                    if (onOverscroll != null && !state.canScrollBackward && dy > 0f) {
                        onOverscroll(dy)
                    } else {
                        state.dispatchRawDelta(-dy)
                    }
                    lastY = change.position.y
                    lastTime = now
                    change.consume()
                }
                if (!change.pressed) break
            }
        } finally {
            onRelease?.invoke()
            if (dragging && kotlin.math.abs(velocityY) > 120f) {
                val delta = -velocityY * 0.18f
                scope.launchFling(state, delta)
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchFling(
    state: androidx.compose.foundation.gestures.ScrollableState,
    delta: Float,
) {
    this.launch {
        runCatching { state.animateScrollBy(delta, tween(420)) }
    }
}

/** 屏幕左缘向右滑动返回（触屏/笔）。鼠标拖动不触发，避免误触。 */
fun Modifier.swipeBack(
    enabled: Boolean = true,
    onBack: () -> Unit,
): Modifier = pointerInput(enabled, onBack) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
        if (down.position.x > 28.dp.toPx()) return@awaitEachGesture
        var fired = false
        var dx = 0f
        var dy = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            dx = change.position.x - down.position.x
            dy = change.position.y - down.position.y
            if (dx > viewConfiguration.touchSlop && dx > kotlin.math.abs(dy) * 1.2f) {
                change.consume()
            }
            if (!change.pressed) break
        }
        if (!fired && dx > size.width * 0.22f && dx > kotlin.math.abs(dy) * 1.5f) {
            fired = true
            onBack()
        }
    }
}

/** 页面底部留白：给悬浮底栏让位（与 Android 内容不被底栏压住的口径一致）。 */
@Composable
fun Modifier.navBarInset(bottomBar: Boolean): Modifier =
    if (bottomBar) this.padding(bottom = 96.dp) else this

/** 页面通用纵向滚动列（带触屏拖动）。 */
@Composable
fun ScrollPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        modifier
            .fillMaxSize()
            .touchScrollable(state, scope)
            .verticalScroll(state),
        content = content,
    )
}

// ── 原有通用零件（保留 API，视觉升级为玻璃卡片）──

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier) {
        if (title != null || trailing != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (title != null) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatTime(epochMillis: Long): String =
    if (epochMillis <= 0L) "—"
    else Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormatter)

fun formatDay(epochMillis: Long): String =
    if (epochMillis <= 0L) "—"
    else Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

fun percent(value: Float): String = "${(value * 100).toInt()}%"

/** 强调色圆点（主题色指示）。 */
@Composable
fun AccentDot(size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(Modifier.size(size).background(LocalAccent.current, CircleShape))
}

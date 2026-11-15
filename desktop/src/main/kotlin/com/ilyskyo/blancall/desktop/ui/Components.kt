package com.ilyskyo.blancall.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.theme.LocalAccent
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import com.ilyskyo.blancall.desktop.ui.theme.isBlancallDark
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

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
            .desktopHover(src, shape, enabled = onClick != null || onLongClick != null)
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

/**
 * 桌面悬停层：鼠标进入时叠一层很淡的 onSurface，并变成手型光标。
 *
 * Compose Desktop 的 clickable 默认既不显示 hover，也不换光标；
 * 移动端不需要，桌面没有就会像一块「死的图」。
 */
@Composable
fun Modifier.desktopHover(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f),
): Modifier {
    if (!enabled) return this
    val hovered by interactionSource.collectIsHoveredAsState()
    return this
        .hoverable(interactionSource)
        .pointerHoverIcon(PointerIcon.Hand)
        .background(if (hovered) color else Color.Transparent, shape)
}

/** 图标按钮的桌面 tooltip；延迟出现，避免扫过一排按钮时满屏气泡。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .clip(SmoothRoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        delayMillis = 550,
        content = content,
    )
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
    val focused by src.collectIsFocusedAsState()
    val shape = SmoothRoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .pressFeedback(src)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .desktopHover(src, shape, enabled = enabled)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
                shape,
            )
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
    val focused by src.collectIsFocusedAsState()
    val lift by animateDpAsState(if (pressed) 1.dp else 4.dp, tween(120), label = "backLift")
    DesktopTooltip("返回") {
        Box(
            modifier = modifier
                .size(48.dp)
                .pressFeedback(src, pressedScale = 0.92f)
                .desktopHover(src, CircleShape)
                .then(
                    if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                )
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
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            )
        }
    }
}

/**
 * 桌面玻璃输入框：与 SearchInput 同一套边框/圆角，但带标签并支持多行。
 * 新建/编辑类页面不再使用 Material 的 OutlinedTextField，避免主题字族与 hover 不一致。
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    enabled: Boolean = true,
) {
    val shape = SmoothRoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    shape,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = src,
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
    compact: Boolean = false,
) {
    val shape = CircleShape
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = if (compact) 22.dp else 36.dp)
            .clip(shape)
            .background(bg)
            .desktopHover(src, shape, enabled = onClick != null)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = src,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 3.dp else 8.dp,
            ),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/**
 * 设置行里的下拉菜单：触发区是一行「当前值 + 向下箭头」，选项在浮层里。
 *
 * 桌面设置不该把 6 个语言/字族选项摊成一排按钮：那既占宽度，
 * 又让「当前选了什么」不突出。菜单保留选中勾，触发区始终显示当前值。
 */
@Composable
fun <T> GlassDropdown(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    triggerMinWidth: androidx.compose.ui.unit.Dp = 160.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    val shape = SmoothRoundedCornerShape(12.dp)
    val src = remember { MutableInteractionSource() }
    Box(modifier) {
        Row(
            Modifier
                .widthIn(min = triggerMinWidth)
                .heightIn(min = 40.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .desktopHover(src, shape, enabled = enabled)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
                .clickable(
                    interactionSource = src,
                    indication = null,
                    enabled = enabled,
                ) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), SmoothRoundedCornerShape(12.dp)),
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    trailingIcon = if (value == selected) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/**
 * 桌面右键：卡片操作菜单。
 * 移动端用长按/「更多」按钮；桌面用户更习惯右键，且卡面能保持干净。
 */
fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = pointerInput(action) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                action()
            }
        }
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
            val src = remember(value) { MutableInteractionSource() }
            val shape = SmoothRoundedCornerShape(11.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .desktopHover(src, shape)
                    .clickable(
                        interactionSource = src,
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
    val focused by src.collectIsFocusedAsState()
    Box(
        modifier
            .pressFeedback(src)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .desktopHover(src, CircleShape, enabled = enabled, color = Color.White.copy(alpha = 0.12f))
            .then(
                if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.78f), CircleShape) else Modifier
            )
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

/** 设置行：标题 + 可选说明 + 右侧控件（Android 设置页的 standard row）。 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val rowShape = SmoothRoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(rowShape)
            .desktopHover(src, rowShape, enabled = onClick != null)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = src, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** 设置分区标题（卡片外的小标题）。 */
@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** 主题色圆点选择器。 */
@Composable
fun AccentPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        com.ilyskyo.blancall.desktop.ui.theme.AccentPresets.forEachIndexed { index, preset ->
            val active = index == selected
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(
                        if (active) 2.dp else 0.dp,
                        if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (active) 22.dp else 26.dp)
                        .clip(CircleShape)
                        .background(preset.primaryLight),
                )
            }
        }
    }
}

/**
 * 设置滑杆：桌面设置里的连续值（字号/行距/界面缩放）优先用滑杆。
 *
 * 加减按钮只适合 3–5 档离散值；连续值用按钮需要点很多次，
 * 也看不出「当前值在区间里的位置」。滑杆保留数值标签，拖动时实时显示。
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueLabel: (Float) -> String = { "%.2f".format(it) },
    steps: Int = 0,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            valueLabel(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

/** 加减步进行（字号 / 行距用）。 */
@Composable
fun StepValue(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepCircle("−", onMinus)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 34.dp),
            textAlign = TextAlign.Center,
        )
        StepCircle("+", onPlus)
    }
}

@Composable
private fun StepCircle(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 桌面滚动条：Compose Desktop 默认不画滚动条，桌面用户会不知道页面能不能滚、滚到哪。
 * 调用方把它叠在滚动容器右侧，内容区自己留出 end padding。
 */
@Composable
fun DesktopScrollbar(
    state: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
    )
}

@Composable
fun DesktopScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
    )
}

/** 时间字段：时/分两个下拉，替代手机式的四个加减按钮。 */
@Composable
fun GlassTimeField(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GlassDropdown(
            options = (0..23).map { it to "%02d".format(it) },
            selected = hour,
            onSelect = { onTimeChange(it, minute) },
            modifier = Modifier.width(84.dp),
            triggerMinWidth = 64.dp,
        )
        Text(":", style = MaterialTheme.typography.bodyMedium)
        GlassDropdown(
            // 旧数据里的分钟可能不是 5 的倍数；把当前值也放进选项，避免下拉显示空白。
            options = ((0..11).map { it * 5 } + minute).distinct().sorted()
                .map { it to "%02d".format(it) },
            selected = minute,
            onSelect = { onTimeChange(hour, it) },
            modifier = Modifier.width(84.dp),
            triggerMinWidth = 64.dp,
        )
    }
}

/**
 * 日期字段：自有月历浮层（不依赖系统 DatePicker）。
 *
 * 项目约定日期选择用自有月历，保证六套语言与桌面主题一致；
 * 返回 epochDay，0 表示清除。
 */
@Composable
fun GlassDateField(
    epochDay: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDate = remember(epochDay) { epochDay.takeIf { it > 0 }?.let(LocalDate::ofEpochDay) }
    var month by remember(expanded, epochDay) {
        mutableStateOf(selectedDate?.let(YearMonth::from) ?: YearMonth.now())
    }
    val locale = Locale.forLanguageTag(Strings.locale)
    val shape = SmoothRoundedCornerShape(12.dp)
    val src = remember { MutableInteractionSource() }
    Box(modifier) {
        Row(
            Modifier
                // 触发器内部有 weight 文本；只给 min 不给 max 时 Row 会吃掉整行剩余宽度，
                // 把「考试日期」这类设置行的标题挤成一列竖排。这里必须同时封顶。
                .widthIn(min = 170.dp, max = 200.dp)
                .heightIn(min = 40.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .desktopHover(src, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
                .clickable(
                    interactionSource = src,
                    indication = null,
                ) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedDate?.toString() ?: "未设置",
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedDate == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), SmoothRoundedCornerShape(14.dp)),
        ) {
            Column(Modifier.width(304.dp).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        month.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月", modifier = Modifier.size(18.dp))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    (1..7).forEach { dayIndex ->
                        val day = DayOfWeek.of(dayIndex)
                        Text(
                            day.getDisplayName(JavaTextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val first = month.atDay(1)
                val lead = first.dayOfWeek.value - 1
                val days = month.lengthOfMonth()
                val rows = ((lead + days + 6) / 7)
                repeat(rows) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val dayNumber = row * 7 + col - lead + 1
                            Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                                if (dayNumber in 1..days) {
                                    val date = month.atDay(dayNumber)
                                    val isSelected = date == selectedDate
                                    val isToday = date == LocalDate.now()
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            )
                                            .then(
                                                if (!isSelected && isToday) {
                                                    Modifier.border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                        CircleShape,
                                                    )
                                                } else Modifier
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                expanded = false
                                                onSelect(date.toEpochDay())
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            dayNumber.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        expanded = false
                        onSelect(LocalDate.now().toEpochDay())
                    }) { Text("今天", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = {
                        expanded = false
                        onSelect(0L)
                    }) { Text("清除", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

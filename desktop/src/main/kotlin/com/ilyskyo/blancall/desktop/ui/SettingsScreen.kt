package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.data.backup.BackupManager
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.AppPaths
import com.ilyskyo.blancall.desktop.platform.Autostart
import com.ilyskyo.blancall.desktop.platform.DiagnosticPackage
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import com.ilyskyo.blancall.desktop.platform.ImportedFonts
import com.ilyskyo.blancall.desktop.ui.theme.SmoothRoundedCornerShape
import com.ilyskyo.blancall.notification.ReminderFrequency
import com.ilyskyo.blancall.notification.ReminderSchedule
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val reviewTemplates = listOf(
    Triple("sprint", "冲刺备考", "目标留存率 95%，间隔更短、复习更勤"),
    Triple("standard", "标准记忆", "90%，均衡（默认）"),
    Triple("deep", "深度长期", "85%，间隔更长"),
)

private enum class SettingsCategory(val label: String, val icon: ImageVector) {
    APPEARANCE("外观", Icons.Outlined.Palette),
    STUDY("学习与提醒", Icons.Outlined.NotificationsActive),
    READING("阅读", Icons.AutoMirrored.Outlined.MenuBook),
    DATA("内容与数据", Icons.Outlined.FolderOpen),
    ABOUT("关于", Icons.Outlined.Info),
}

@Composable
fun SettingsScreen(state: AppState, initialCategory: String = SettingsCategory.APPEARANCE.name) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var categoryName by rememberSaveable { mutableStateOf(initialCategory) }
    var query by rememberSaveable { mutableStateOf("") }
    val category = remember(categoryName) {
        SettingsCategory.entries.firstOrNull { it.name == categoryName } ?: SettingsCategory.APPEARANCE
    }

    Column(Modifier.fillMaxSize()) {
        BlancallTopBar(
            title = "设置",
            subtitle = "Blancall Windows · 无 AI 本地版",
            onBack = { state.back() },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索设置项…",
                modifier = Modifier.widthIn(max = 520.dp).weight(1f),
            )
            if (query.isNotBlank()) {
                GlassButton(onClick = { query = "" }) {
                    Text("清除", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = maxWidth >= 900.dp
            val content: @Composable () -> Unit = {
                SettingsContent(
                    state = state,
                    category = category,
                    query = query,
                    onJump = { target ->
                        categoryName = target.name
                        query = ""
                    },
                    scope = scope,
                    busy = busy,
                    setBusy = { busy = it },
                )
            }
            if (wide) {
                Row(
                    Modifier
                        .widthIn(max = 1120.dp)
                        .fillMaxSize(),
                ) {
                    CategoryRail(
                        selected = category,
                        onSelect = { categoryName = it.name },
                        modifier = Modifier.width(216.dp).fillMaxHeight(),
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    )
                    val contentScroll = rememberScrollState()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(contentScroll)
                                .padding(horizontal = 28.dp, vertical = 8.dp),
                        ) { content() }
                        DesktopScrollbar(
                            contentScroll,
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(top = 8.dp, bottom = 8.dp, end = 4.dp),
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "设置分类",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(68.dp),
                        )
                        GlassDropdown(
                            options = SettingsCategory.entries.map { it.name to it.label },
                            selected = category.name,
                            onSelect = { categoryName = it },
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                    }
                    val contentScroll = rememberScrollState()
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(contentScroll)
                                .padding(horizontal = 20.dp),
                        ) { content() }
                        DesktopScrollbar(
                            contentScroll,
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(top = 4.dp, bottom = 4.dp, end = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRail(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "设置分类",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
        )
        SettingsCategory.entries.forEach { item ->
            val active = item == selected
            val src = remember(item) { MutableInteractionSource() }
            val shape = SmoothRoundedCornerShape(12.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .desktopHover(src, shape)
                    .clickable(
                        interactionSource = src,
                        indication = null,
                    ) { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: AppState,
    category: SettingsCategory,
    query: String,
    onJump: (SettingsCategory) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    busy: Boolean,
    setBusy: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (query.isNotBlank()) {
            SettingsSearchResults(query, onJump)
        } else {
            when (category) {
                SettingsCategory.APPEARANCE -> AppearanceSettings(state)
                SettingsCategory.STUDY -> StudySettings(state)
                SettingsCategory.READING -> ReadingSettings(state)
                SettingsCategory.DATA -> DataSettings(state, scope, busy, setBusy)
                SettingsCategory.ABOUT -> AboutSettings(state)
            }
        }
    }
}

// ── 设置搜索 ──

private data class SettingEntry(
    val title: String,
    val subtitle: String,
    val category: SettingsCategory,
    val keywords: String = "",
)

private val settingsIndex = listOf(
    SettingEntry("主题", "跟随系统 / 浅色 / 深色", SettingsCategory.APPEARANCE, "theme dark light"),
    SettingEntry("背景", "浅色模式下的米黄 / 纯白", SettingsCategory.APPEARANCE, "background"),
    SettingEntry("主题色", "强调色预设", SettingsCategory.APPEARANCE, "accent color"),
    SettingEntry("界面字号", "影响全部界面文字", SettingsCategory.APPEARANCE, "font scale"),
    SettingEntry("界面字体", "系统衬线 / 系统黑体 / 霞鹜文楷", SettingsCategory.APPEARANCE, "font family"),
    SettingEntry("界面语言", "简中 / 繁中 / 英 / 日 / 韩 / 俄", SettingsCategory.APPEARANCE, "language locale"),
    SettingEntry("关闭按钮", "每次询问 / 托盘 / 退出", SettingsCategory.APPEARANCE, "close tray exit"),
    SettingEntry("开机自启", "写入当前用户启动项", SettingsCategory.APPEARANCE, "autostart startup"),
    SettingEntry("复习模板", "冲刺 / 标准 / 深度长期", SettingsCategory.STUDY, "fsrs retention"),
    SettingEntry("每日学习时长", "今日计划分钟预算", SettingsCategory.STUDY, "goal minutes"),
    SettingEntry("每日练习目标", "今日目标进度与提醒口径", SettingsCategory.STUDY, "daily goal"),
    SettingEntry("考试日期", "今日计划倒排", SettingsCategory.STUDY, "exam date"),
    SettingEntry("每日学习提醒", "托盘提醒开关", SettingsCategory.STUDY, "reminder"),
    SettingEntry("提醒时间", "时 / 分", SettingsCategory.STUDY, "reminder time"),
    SettingEntry("提醒频率", "每天 / 工作日 / 一三五 / 自定义", SettingsCategory.STUDY, "reminder frequency"),
    SettingEntry("免打扰", "免打扰时段", SettingsCategory.STUDY, "quiet hours"),
    SettingEntry("阅读字号", "所有文章的默认字号", SettingsCategory.READING, "reader font"),
    SettingEntry("阅读行距", "所有文章的默认行距", SettingsCategory.READING, "line height"),
    SettingEntry("段落首行缩进", "仅对允许自动缩进的文章生效", SettingsCategory.READING, "indent"),
    SettingEntry("练习提示", "10 秒无操作淡显下一字", SettingsCategory.READING, "hint"),
    SettingEntry("导入阅读字体", "TTF / OTF", SettingsCategory.READING, "font import"),
    SettingEntry("数据目录", "打开 %LOCALAPPDATA%\\Blancall\\data", SettingsCategory.DATA, "folder"),
    SettingEntry("导出备份", "ZIP v1，与 Android 双向兼容", SettingsCategory.DATA, "backup export"),
    SettingEntry("导入备份", "导入前自动预备份", SettingsCategory.DATA, "backup import"),
    SettingEntry("文章标签", "标签管理与文章绑定", SettingsCategory.DATA, "tags"),
    SettingEntry("内容包", "素材库 ZIP 导入", SettingsCategory.DATA, "content pack library"),
    SettingEntry("数据互换", "JSON 导出 / CSV 回灌", SettingsCategory.DATA, "json csv exchange"),
    SettingEntry("导出诊断包", "环境与日志，不含正文", SettingsCategory.DATA, "diagnostics logs"),
    SettingEntry("使用引导", "五页上手说明", SettingsCategory.ABOUT, "guide onboarding"),
    SettingEntry("帮助", "快捷键与数据安全", SettingsCategory.ABOUT, "help shortcuts"),
    SettingEntry("隐私说明", "本机存储与无网络", SettingsCategory.ABOUT, "privacy"),
)

@Composable
private fun SettingsSearchResults(query: String, onJump: (SettingsCategory) -> Unit) {
    val needle = query.trim().lowercase()
    val results = remember(needle) {
        settingsIndex.filter {
            it.title.lowercase().contains(needle) ||
                it.subtitle.lowercase().contains(needle) ||
                it.keywords.lowercase().contains(needle)
        }
    }
    SettingsSectionLabel(Strings.f("搜索“{0}”", query))
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        if (results.isEmpty()) {
            EmptyState("没有匹配的设置项", "换一个关键词，或清空搜索回到分类。")
        } else {
            results.forEach { entry ->
                SettingsRow(
                    title = entry.title,
                    subtitle = Strings.f("{0} · {1}", entry.category.label, entry.subtitle),
                    onClick = { onJump(entry.category) },
                ) {
                    Text("打开", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(state: AppState) {
    var autostartEnabled by remember { mutableStateOf(Autostart.isEnabled()) }
    SettingsSectionLabel("外观")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        SettingsRow("主题") {
            SegmentedPills(
                options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                selected = state.prefs.themeMode,
                onSelect = { state.prefs.updateThemeMode(it) },
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        SettingsRow("背景", "浅色模式下生效") {
            SegmentedPills(
                options = listOf("sepia" to "米黄", "plain" to "纯白"),
                selected = if (state.prefs.backgroundStyle == "plain") "plain" else "sepia",
                onSelect = { state.prefs.updateBackgroundStyle(it) },
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
        SettingsRow("主题色") {
            AccentPicker(
                selected = state.prefs.accentIndex,
                onSelect = { state.prefs.updateAccentIndex(it) },
            )
        }
        SettingsRow("界面字号", "影响全部界面文字") {
            Column(Modifier.width(240.dp)) {
                GlassSlider(
                    value = state.prefs.uiScale,
                    onValueChange = { state.prefs.updateUiScale(it) },
                    valueRange = 0.85f..1.30f,
                    valueLabel = { "${(it * 100).toInt()}%" },
                )
            }
        }
        SettingsRow("界面字体", "标题与正文的字族；阅读页有独立排版设置") {
            GlassDropdown(
                options = listOf(
                    "serif" to "系统衬线",
                    "sans" to "系统黑体",
                    "xwwk" to "霞鹜文楷",
                ),
                selected = state.prefs.uiFont,
                onSelect = { state.prefs.updateUiFont(it) },
                modifier = Modifier.widthIn(max = 240.dp),
            )
        }
        SettingsRow("界面语言") {
            GlassDropdown(
                options = Strings.languages.map { it.tag to it.label },
                selected = state.prefs.language,
                onSelect = {
                    state.prefs.updateLanguage(it)
                    Strings.use(it)
                },
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
        SettingsRow("关闭按钮") {
            SegmentedPills(
                options = listOf("ask" to "每次询问", "tray" to "托盘", "exit" to "退出"),
                selected = state.prefs.closeAction,
                onSelect = { state.prefs.updateCloseAction(it) },
                modifier = Modifier.widthIn(max = 270.dp),
            )
        }
        SettingsRow("窗口材质", "Windows 11 标题栏云母；重启应用后生效") {
            SegmentedPills(
                options = listOf("glass" to "静态玻璃", "mica" to "云母"),
                selected = state.prefs.materialMode,
                onSelect = { state.prefs.updateMaterialMode(it) },
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
        SettingsRow("开机自启", "写入当前用户启动项，不需要管理员权限") {
            GlassSwitch(
                checked = autostartEnabled,
                onCheckedChange = { enabled ->
                    if (Autostart.setEnabled(enabled)) {
                        autostartEnabled = enabled
                        state.showToast(if (enabled) "已开启开机自启" else "已关闭开机自启")
                    } else {
                        state.showToast("无法修改启动项，请检查安全软件")
                    }
                },
            )
        }
    }
}

@Composable
private fun StudySettings(state: AppState) {
    SettingsSectionLabel("记忆计划")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        SettingsRow("复习模板") {
            SegmentedPills(
                options = reviewTemplates.map { it.first to it.second },
                selected = state.prefs.reviewTemplateId,
                onSelect = { state.updateReviewTemplate(it) },
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        val currentTemplate = reviewTemplates.firstOrNull { it.first == state.prefs.reviewTemplateId } ?: reviewTemplates[1]
        Text(
            Strings.f(
                "{0}（当前 {1}%）",
                Strings.t(currentTemplate.third),
                (FsrsEngine.retentionForTemplate(currentTemplate.first) * 100).toInt(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SettingsRow("每日学习时长", "用于今日计划的分钟预算") {
            Column(Modifier.width(220.dp)) {
                GlassSlider(
                    value = state.reminderPrefs.goalMinutes.toFloat(),
                    onValueChange = { state.reminderPrefs.updateGoalMinutes(it.toInt()) },
                    valueRange = 5f..120f,
                    valueLabel = { Strings.f("{0} 分钟", it.toInt()) },
                )
            }
        }
        SettingsRow("每日练习目标", "用于今日目标进度与提醒口径") {
            GlassDropdown(
                // 旧版可能存了 7 之类的自定义值；把它留在选项里，避免下拉空白。
                options = (listOf(1, 3, 5, 10, 20) + state.reminderPrefs.dailyPracticeGoal)
                    .distinct().sorted()
                    .map { it to Strings.f("{0} 次", it) },
                selected = state.reminderPrefs.dailyPracticeGoal,
                onSelect = { state.reminderPrefs.updateDailyPracticeGoal(it) },
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
        SettingsRow("考试日期", "用于今日计划倒排；留空则按普通计划") {
            GlassDateField(
                epochDay = state.reminderPrefs.examEpochDay,
                onSelect = { state.reminderPrefs.updateExamEpochDay(it) },
            )
        }
    }

    SettingsSectionLabel("学习提醒")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        ReminderSettingsContent(state)
    }
}

@Composable
private fun ReadingSettings(state: AppState) {
    var fonts by remember { mutableStateOf(ImportedFonts.list()) }
    SettingsSectionLabel("阅读排版")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        SettingsRow("阅读字号") {
            Column(Modifier.width(240.dp)) {
                GlassSlider(
                    value = state.prefs.readerFontPx,
                    onValueChange = { state.prefs.updateReaderFontPx(it) },
                    valueRange = 14f..36f,
                    valueLabel = { Strings.f("{0} px", it.toInt()) },
                )
            }
        }
        SettingsRow("阅读行距") {
            Column(Modifier.width(240.dp)) {
                GlassSlider(
                    value = state.prefs.readerLineHeight,
                    onValueChange = { state.prefs.updateReaderLineHeight(it) },
                    valueRange = 1.2f..2.6f,
                    valueLabel = { "%.1f".format(it) },
                )
            }
        }
        SettingsRow("段落首行缩进", "仅对允许自动缩进的文章生效") {
            GlassSwitch(
                checked = state.prefs.paragraphIndent,
                onCheckedChange = { state.prefs.updateParagraphIndent(it) },
            )
        }
        SettingsRow("练习提示", "10 秒无操作淡显下一字") {
            GlassSwitch(
                checked = state.prefs.hintsEnabled,
                onCheckedChange = { state.prefs.updateHintsEnabled(it) },
            )
        }
    }
    SettingsSectionLabel("导入字体")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (fonts.isEmpty()) "还没有导入字体" else Strings.f("已导入 {0} 个字体", fonts.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            GlassButton(onClick = {
                val file = FileDialogs.open("导入字体", listOf(".ttf", ".otf")) ?: return@GlassButton
                val entry = ImportedFonts.import(file)
                if (entry == null) {
                    state.showToast("字体无法读取")
                } else {
                    fonts = ImportedFonts.list()
                    state.showToast(Strings.f("已导入字体「{0}」", entry.name))
                }
            }) { Text("导入 TTF / OTF", style = MaterialTheme.typography.labelMedium) }
        }
        fonts.forEach { font ->
            SettingsRow(font.name, "阅读设置里可按文章选择") {
                GlassButton(onClick = {
                    if (ImportedFonts.delete(font.id)) {
                        fonts = ImportedFonts.list()
                        state.showToast("字体已删除；使用它的文章会回落到系统字体")
                    }
                }) { Text("删除", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "字体文件复制到数据目录 fonts/，不进备份；换机后需要在阅读设置里重新导入。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSectionLabel("说明")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            "阅读模式里可以按文章单独调整字体、字号、行距、遮挡与划线；这里改的是所有文章的默认值。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataSettings(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    busy: Boolean,
    setBusy: (Boolean) -> Unit,
) {
    SettingsSectionLabel("数据")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            state.dataDir.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(onClick = { openFolder(AppPaths.dataDir) }) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("数据目录", style = MaterialTheme.typography.labelMedium)
            }
            GlassButton(onClick = { openFolder(AppPaths.backupsDir) }) {
                Text("备份目录", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryPill(
                if (busy) "处理中…" else "导出备份",
                enabled = !busy,
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
                    val target = FileDialogs.save("导出备份", "blancall_backup_$stamp.zip")
                        ?: return@PrimaryPill
                    scope.launch {
                        setBusy(true)
                        try {
                            withContext(Dispatchers.IO) {
                                target.outputStream().use { out ->
                                    BackupManager.exportZip(
                                        state.dataDir,
                                        out,
                                        BackupManager.Group.defaultSelection(),
                                    )
                                }
                            }
                            state.showToast(Strings.f("备份已导出：{0}", target.name))
                        } catch (e: Exception) {
                            state.reportStorageError("导出备份", e)
                        } finally {
                            setBusy(false)
                        }
                    }
                },
            )
            GlassButton(
                enabled = !busy,
                onClick = {
                    val source = FileDialogs.open("导入备份", listOf(".zip")) ?: return@GlassButton
                    scope.launch {
                        setBusy(true)
                        try {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                                    val pre = File(AppPaths.backupsDir, "pre-import_$stamp.zip")
                                    pre.outputStream().use { out ->
                                        BackupManager.exportZip(
                                            state.dataDir,
                                            out,
                                            BackupManager.Group.defaultSelection(),
                                        )
                                    }
                                }
                                BackupManager.restoreZip(source, state.dataDir)
                                File(state.dataDir, "ai_history").deleteRecursively()
                            }
                            state.reloadAll()
                            state.showToast("备份已导入，数据已重新加载")
                        } catch (e: Exception) {
                            state.reportStorageError("导入备份", e)
                        } finally {
                            setBusy(false)
                        }
                    }
                },
            ) { Text("导入备份", style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "备份包含文章、练习记录、FSRS 状态、标签、自定义挖空、遮挡、阅读偏好、墨迹等 7 个类目；不含 AI 数据。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSectionLabel("内容管理")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(onClick = { state.navigate(Screen.Tags) }) {
                Text("文章标签", style = MaterialTheme.typography.labelMedium)
            }
            GlassButton(onClick = { state.navigate(Screen.ContentPacks) }) {
                Text("内容包", style = MaterialTheme.typography.labelMedium)
            }
            GlassButton(onClick = { state.navigate(Screen.DataInterchange) }) {
                Text("数据互换", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "标签用于筛选文章与组织复习；与 Android 的 tags.json 完全一致。数据互换提供 JSON 导出与 12 列 CSV 回灌。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSectionLabel("诊断")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(onClick = {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
                val target = FileDialogs.save("导出诊断包", "blancall_diagnostics_$stamp.zip")
                    ?: return@GlassButton
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) {
                            DiagnosticPackage.export(target, state.dataDir, "7.6.1-win.2")
                        }
                        state.showToast(Strings.f("诊断包已导出（{0} 项，不含正文）", count))
                    } catch (e: Exception) {
                        state.showToast(Strings.f("导出失败：{0}", e.message))
                    }
                }
            }) { Text("导出诊断包", style = MaterialTheme.typography.labelMedium) }
            GlassButton(onClick = { openFolder(AppPaths.logsDir) }) {
                Text("日志目录", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AboutSettings(state: AppState) {
    SettingsSectionLabel("关于")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text("Blancall 7.6.1-win.2 · MIT", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "纯本地、无账号、无网络、无遥测。数据目录固定为 %LOCALAPPDATA%\\Blancall。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "手写识别模型基于 CASIA-HWDB 训练，仅限学术研究使用，禁止商业用途。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSectionLabel("帮助")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text("1. 首页或文章页导入文章（粘贴、TXT、PDF、DOCX、拖拽均可）。", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Text("2. 阅读模式里调排版与遮挡；详情页进入练习，四种模式共用一套判分。", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Text("3. 数据页查看错题本、练习历史、热力图与成就；备份在设置 → 数据。", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(onClick = { state.navigate(Screen.Guide) }) {
                Text("使用引导", style = MaterialTheme.typography.labelMedium)
            }
            GlassButton(onClick = { state.navigate(Screen.Help) }) {
                Text("帮助", style = MaterialTheme.typography.labelMedium)
            }
            GlassButton(onClick = { state.navigate(Screen.Privacy) }) {
                Text("隐私说明", style = MaterialTheme.typography.labelMedium)
            }
            GlassButton(onClick = { state.replayWelcome() }) {
                Text("重播欢迎页", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    SettingsSectionLabel("赞赏区·本软件完全免费")
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            "如果 Blancall 帮你记住了想记住的东西，把它推荐给需要的人，就是最好的支持。应用没有账号、没有广告，也不会向你索要任何费用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderSettingsContent(state: AppState) {
    val rp = state.reminderPrefs

    SettingsRow(
        title = "每日学习提醒",
        subtitle = if (!rp.enabled) {
            "已关闭"
        } else {
            val next = ReminderSchedule.nextAt(LocalDateTime.now(), rp.hour, rp.minute, rp.reminderDays)
            val locale = Locale.forLanguageTag(Strings.locale)
            val day = next.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            val stamp = next.format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale),
            )
            Strings.f("下次：{0} {1}", day, stamp)
        },
    ) {
        GlassSwitch(
            checked = rp.enabled,
            onCheckedChange = {
                rp.updateEnabled(it)
                state.reminders.restart()
            },
        )
    }

    if (!rp.enabled) {
        Text(
            "开启后，到点会在托盘弹出复习提醒。关闭窗口时选择「最小化到托盘」可保持提醒。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }

    SettingsRow("提醒时间", "5 分钟步进") {
        GlassTimeField(
            hour = rp.hour,
            minute = rp.minute,
            onTimeChange = { h, m ->
                rp.updateTime(h, m)
                state.reminders.restart()
            },
        )
    }

    SettingsRow("提醒频率") {
        SegmentedPills(
            options = listOf(
                ReminderFrequency.DAILY to "每天",
                ReminderFrequency.WEEKLY_FIVE to "工作日",
                ReminderFrequency.WEEKLY_THREE to "一三五",
                ReminderFrequency.CUSTOM to "自定义",
            ),
            selected = rp.frequency,
            onSelect = {
                rp.updateFrequency(it)
                state.reminders.restart()
            },
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("提醒日", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
        val names = java.time.DayOfWeek.entries.map {
            it.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag(Strings.locale))
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            java.time.DayOfWeek.entries.forEachIndexed { index, day ->
                TagChip(
                    text = names[index],
                    selected = day in rp.reminderDays,
                    onClick = {
                        if (day in rp.reminderDays) {
                            if (rp.reminderDays.size > 1) {
                                rp.updateReminderDays(rp.reminderDays - day)
                                state.reminders.restart()
                            } else {
                                state.showToast("至少保留一天")
                            }
                        } else {
                            rp.updateReminderDays(rp.reminderDays + day)
                            state.reminders.restart()
                        }
                    },
                )
            }
        }
    }

    SettingsRow("免打扰") {
        GlassSwitch(
            checked = rp.quietHoursEnabled,
            onCheckedChange = {
                rp.updateQuietHoursEnabled(it)
                state.reminders.restart()
            },
        )
    }
    if (rp.quietHoursEnabled) {
        SettingsRow("免打扰时段") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassTimeField(
                    hour = rp.quietStartMinutes / 60,
                    minute = rp.quietStartMinutes % 60,
                    onTimeChange = { h, m ->
                        rp.updateQuietStart(h * 60 + m)
                        state.reminders.restart()
                    },
                )
                Text("至", style = MaterialTheme.typography.labelSmall)
                GlassTimeField(
                    hour = rp.quietEndMinutes / 60,
                    minute = rp.quietEndMinutes % 60,
                    onTimeChange = { h, m ->
                        rp.updateQuietEnd(h * 60 + m)
                        state.reminders.restart()
                    },
                )
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Strings.f("连续学习 {0} 天 · 最长 {1} 天", rp.studyStreakDays, rp.longestStreakDays),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        GlassButton(onClick = { state.reminders.sendTest() }) {
            Text("测试提醒", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun openFolder(dir: File) {
    runCatching {
        dir.mkdirs()
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir)
    }
}

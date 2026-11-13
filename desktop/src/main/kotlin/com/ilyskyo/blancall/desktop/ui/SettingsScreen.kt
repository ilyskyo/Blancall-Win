package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.data.backup.BackupManager
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.AppPaths
import com.ilyskyo.blancall.desktop.platform.DiagnosticPackage
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import com.ilyskyo.blancall.notification.ReminderFrequency
import com.ilyskyo.blancall.notification.ReminderSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().widthIn(max = 1000.dp)) {
            Column {
                Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Blancall Windows · 无 AI 本地版",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "外观",
            subtitle = "主题与阅读排版",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                    if (state.prefs.themeMode == value) {
                        Button(onClick = { state.prefs.updateThemeMode(value) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { state.prefs.updateThemeMode(value) }) { Text(label) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("背景", style = MaterialTheme.typography.bodyMedium)
                listOf("sepia" to "米黄", "plain" to "白", "gray" to "灰").forEach { (value, label) ->
                    if (state.prefs.backgroundStyle == value) {
                        Button(onClick = { state.prefs.updateBackgroundStyle(value) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { state.prefs.updateBackgroundStyle(value) }) { Text(label) }
                    }
                }
                Text("仅浅色模式生效", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("界面字号", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    state.prefs.updateUiScale(state.prefs.uiScale - 0.05f)
                }) { Text("A-") }
                Text("${(state.prefs.uiScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    state.prefs.updateUiScale(state.prefs.uiScale + 0.05f)
                }) { Text("A+") }
                Text("85%–130%，影响全部界面文字", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("界面语言", style = MaterialTheme.typography.bodyMedium)
                com.ilyskyo.blancall.desktop.app.Strings.languages.forEach { language ->
                    if (state.prefs.language == language.tag) {
                        Button(onClick = {
                            state.prefs.updateLanguage(language.tag)
                            com.ilyskyo.blancall.desktop.app.Strings.use(language.tag)
                        }) { Text(language.label) }
                    } else {
                        OutlinedButton(onClick = {
                            state.prefs.updateLanguage(language.tag)
                            com.ilyskyo.blancall.desktop.app.Strings.use(language.tag)
                        }) { Text(language.label) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关闭按钮", style = MaterialTheme.typography.bodyMedium)
                listOf("ask" to "每次询问", "tray" to "最小化到托盘", "exit" to "退出").forEach { (value, label) ->
                    if (state.prefs.closeAction == value) {
                        Button(onClick = { state.prefs.updateCloseAction(value) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { state.prefs.updateCloseAction(value) }) { Text(label) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("练习提示", style = MaterialTheme.typography.bodyMedium)
                Checkbox(
                    checked = state.prefs.hintsEnabled,
                    onCheckedChange = { state.prefs.updateHintsEnabled(it) },
                )
                Text(
                    if (state.prefs.hintsEnabled) "显示字数弱提示与首字强提示" else "关闭后不显示任何提示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("段落首行缩进", style = MaterialTheme.typography.bodyMedium)
                Checkbox(
                    checked = state.prefs.paragraphIndent,
                    onCheckedChange = { state.prefs.updateParagraphIndent(it) },
                )
                Text(
                    "仅对允许自动缩进的文章生效（PDF/Word 保持原文排版）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("阅读字号", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { state.prefs.updateReaderFontPx(state.prefs.readerFontPx - 1f) }) { Text("A-") }
                Text("${state.prefs.readerFontPx.toInt()}", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { state.prefs.updateReaderFontPx(state.prefs.readerFontPx + 1f) }) { Text("A+") }
                Spacer(Modifier.weight(1f))
                Text("行距", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { state.prefs.updateReaderLineHeight(state.prefs.readerLineHeight - 0.1f) }) { Text("-") }
                Text("%.1f".format(state.prefs.readerLineHeight), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { state.prefs.updateReaderLineHeight(state.prefs.readerLineHeight + 0.1f) }) { Text("+") }
            }
        }

        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "复习与提醒",
            subtitle = "FSRS 复习强度、每日提醒与免打扰",
        ) {
            ReminderSettingsContent(state)
        }

        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "数据",
            subtitle = "所有数据保存在本机；可导出与 Android 完全同格式的备份 ZIP",
        ) {
            Text(
                state.dataDir.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openFolder(AppPaths.dataDir) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("打开数据目录")
                }
                OutlinedButton(onClick = { openFolder(AppPaths.backupsDir) }) { Text("打开备份目录") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
                        val target = FileDialogs.save("导出备份", "blancall_backup_$stamp.zip") ?: return@Button
                        scope.launch {
                            busy = true
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
                                state.showToast(Strings.f("导出失败：{0}", e.message))
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) "处理中…" else "导出备份") }

                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val source = FileDialogs.open("导入备份", listOf(".zip")) ?: return@OutlinedButton
                        scope.launch {
                            busy = true
                            try {
                                withContext(Dispatchers.IO) {
                                    // 导入前预备份（Windows 增强）
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
                                    // Windows 无 AI：不保留 Pro 备份里的 AI 历史
                                    File(state.dataDir, "ai_history").deleteRecursively()
                                }
                                state.reloadAll()
                                state.showToast("备份已导入，数据已重新加载")
                            } catch (e: Exception) {
                                state.showToast(Strings.f("导入失败：{0}", e.message))
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("导入备份") }
            }
            Text(
                "备份包含文章、练习记录、FSRS 状态、标签、自定义挖空、遮挡、阅读偏好、墨迹等 7 个类目；不含 AI 数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "内容管理",
            subtitle = "标签与内容包",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { state.navigate(Screen.Tags) }) { Text("文章标签") }
                OutlinedButton(onClick = { state.navigate(Screen.ContentPacks) }) { Text("内容包") }
            }
            Text(
                "标签与 Android 的 tags.json 同格式；创建标签后可在文章列表给文章打标签。内容包支持 zip（txt/md）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "帮助与引导",
            subtitle = "三步开始；随时可以重播欢迎页",
        ) {
            Text("1. 首页或文章页导入文章（粘贴、TXT、PDF、DOCX、拖拽均可）。", style = MaterialTheme.typography.bodyMedium)
            Text("2. 打开文章阅读，点击「开始练习」；三种模式与自定义挖空都在练习页切换。", style = MaterialTheme.typography.bodyMedium)
            Text("3. 数据页查看错题本、热力图、成就与遗忘曲线；备份在设置 → 数据。", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { state.replayWelcome() }) { Text("重播欢迎页") }
                OutlinedButton(onClick = { openFolder(AppPaths.dataDir) }) { Text("打开数据目录") }
            }
        }

        SectionCard(
            modifier = Modifier.widthIn(max = 1000.dp),
            title = "关于",
            subtitle = "Blancall 7.6.1-win.2 · MIT",
        ) {
            Text(
                "纯本地、无账号、无网络、无遥测。数据目录固定为 %LOCALAPPDATA%\\Blancall。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "手写识别模型基于 CASIA-HWDB 训练，仅限学术研究使用，禁止商业用途；ncnn 等第三方组件与许可全文见安装目录 THIRD-PARTY-LICENSES.md。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Windows 版尚未代码签名，首次运行可能出现 SmartScreen 提示；不提供自动更新与 MSI 安装包，升级请替换程序目录，学习数据保持不变。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
                    val target = FileDialogs.save("导出诊断包", "blancall_diagnostics_$stamp.zip")
                        ?: return@TextButton
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
                }) { Text("导出诊断包") }
                TextButton(onClick = { openFolder(AppPaths.logsDir) }) { Text("打开日志目录") }
            }
            Text(
                "诊断包只含环境信息、日志、偏好摘要与数据文件清单，不含文章正文与练习内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(bottom = 32.dp))
    }
}

private val reviewTemplates = listOf(
    Triple("sprint", "冲刺备考", "目标留存率 95%，间隔更短、复习更勤"),
    Triple("standard", "标准记忆", "90%，均衡（默认）"),
    Triple("deep", "深度长期", "85%，间隔更长"),
)

@Composable
private fun ReminderSettingsContent(state: AppState) {
    val rp = state.reminderPrefs

    Text("复习模板", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        reviewTemplates.forEach { (id, label, _) ->
            if (state.prefs.reviewTemplateId == id) {
                Button(onClick = { state.updateReviewTemplate(id) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { state.updateReviewTemplate(id) }) { Text(label) }
            }
        }
    }
    val current = reviewTemplates.firstOrNull { it.first == state.prefs.reviewTemplateId }
        ?: reviewTemplates[1]
    Text(
        Strings.f(
            "{0}（当前 {1}%）",
            current.third,
            (FsrsEngine.retentionForTemplate(current.first) * 100).toInt(),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(
            checked = rp.enabled,
            onCheckedChange = {
                rp.updateEnabled(it)
                state.reminders.restart()
            },
        )
        Text("每日学习提醒", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            if (!rp.enabled) "已关闭"
            else {
                val next = ReminderSchedule.nextAt(LocalDateTime.now(), rp.hour, rp.minute, rp.reminderDays)
                val locale = Locale.forLanguageTag(Strings.locale)
                val day = next.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                val stamp = next.format(
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        .withLocale(locale),
                )
                Strings.f("下次：{0} {1}", day, stamp)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!rp.enabled) {
        Text(
            "开启后，到点会在托盘弹出复习提醒。关闭窗口时选择「最小化到托盘」可保持提醒；完全退出应用则不会提醒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("提醒时间", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        StepButton("−") {
            rp.updateTime((rp.hour + 23) % 24, rp.minute)
            state.reminders.restart()
        }
        Text(
            "%02d:%02d".format(rp.hour, rp.minute),
            style = MaterialTheme.typography.titleMedium,
        )
        StepButton("+") {
            rp.updateTime((rp.hour + 1) % 24, rp.minute)
            state.reminders.restart()
        }
        StepButton("−") {
            rp.updateTime(rp.hour, (rp.minute + 55) % 60)
            state.reminders.restart()
        }
        StepButton("+") {
            rp.updateTime(rp.hour, (rp.minute + 5) % 60)
            state.reminders.restart()
        }
        Text("小时 / 分钟（5 分钟步进）", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("提醒频率", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        listOf(
            ReminderFrequency.DAILY to "每天",
            ReminderFrequency.WEEKLY_FIVE to "工作日",
            ReminderFrequency.WEEKLY_THREE to "一三五",
            ReminderFrequency.CUSTOM to "自定义",
        ).forEach { (freq, label) ->
            if (rp.frequency == freq) {
                Button(onClick = {
                    rp.updateFrequency(freq)
                    state.reminders.restart()
                }) { Text(label) }
            } else {
                OutlinedButton(onClick = {
                    rp.updateFrequency(freq)
                    state.reminders.restart()
                }) { Text(label) }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("提醒日", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        val names = DayOfWeek.entries.map {
            it.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag(Strings.locale))
        }
        DayOfWeek.entries.forEachIndexed { index, day ->
            val selected = day in rp.reminderDays
            if (selected) {
                Button(onClick = {
                    if (rp.reminderDays.size > 1) {
                        rp.updateReminderDays(rp.reminderDays - day)
                        state.reminders.restart()
                    } else {
                        state.showToast("至少保留一天")
                    }
                }) { Text(names[index]) }
            } else {
                OutlinedButton(onClick = {
                    rp.updateReminderDays(rp.reminderDays + day)
                    state.reminders.restart()
                }) { Text(names[index]) }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("每日目标", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        listOf(1, 3, 5, 10).forEach { goal ->
            if (rp.dailyPracticeGoal == goal) {
                Button(onClick = { rp.updateDailyPracticeGoal(goal) }) { Text(Strings.f("{0} 次", goal)) }
            } else {
                OutlinedButton(onClick = { rp.updateDailyPracticeGoal(goal) }) { Text(Strings.f("{0} 次", goal)) }
            }
        }
        Text("用于今日目标进度与提醒口径", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(
            checked = rp.quietHoursEnabled,
            onCheckedChange = { rp.updateQuietHoursEnabled(it); state.reminders.restart() },
        )
        Text("免打扰", style = MaterialTheme.typography.bodyMedium)
        if (rp.quietHoursEnabled) {
            StepButton("−") {
                rp.updateQuietStart(rp.quietStartMinutes - 30)
                state.reminders.restart()
            }
            Text(formatMinuteOfDay(rp.quietStartMinutes), style = MaterialTheme.typography.bodyMedium)
            StepButton("+") {
                rp.updateQuietStart(rp.quietStartMinutes + 30)
                state.reminders.restart()
            }
            Text("至")
            StepButton("−") {
                rp.updateQuietEnd(rp.quietEndMinutes - 30)
                state.reminders.restart()
            }
            Text(formatMinuteOfDay(rp.quietEndMinutes), style = MaterialTheme.typography.bodyMedium)
            StepButton("+") {
                rp.updateQuietEnd(rp.quietEndMinutes + 30)
                state.reminders.restart()
            }
            Text(
                "落在免打扰窗口内的那次提醒会直接跳过，不顺延",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            Strings.f("连续学习 {0} 天 · 最长 {1} 天", rp.studyStreakDays, rp.longestStreakDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { state.reminders.sendTest() }) { Text("发送测试提醒") }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}

private fun formatMinuteOfDay(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private fun openFolder(dir: File) {
    runCatching {
        dir.mkdirs()
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir)
    }
}

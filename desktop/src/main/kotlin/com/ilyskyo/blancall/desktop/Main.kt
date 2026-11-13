package com.ilyskyo.blancall.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.platform.AppPaths
import com.ilyskyo.blancall.desktop.platform.SingleInstance
import com.ilyskyo.blancall.desktop.platform.TextImporter
import com.ilyskyo.blancall.desktop.platform.TrayController
import com.ilyskyo.blancall.desktop.platform.WindowGeometry
import com.ilyskyo.blancall.desktop.ui.BlancallApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.JOptionPane
import kotlin.math.min

fun main() {
    if (!SingleInstance.acquire()) {
        JOptionPane.showMessageDialog(
            null,
            "Blancall 已经在运行。请从任务栏或托盘打开已有窗口。",
            "Blancall",
            JOptionPane.INFORMATION_MESSAGE,
        )
        return
    }
    AppPaths.ensureDirectories()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching {
            val file = java.io.File(AppPaths.logsDir, "crash-${System.currentTimeMillis()}.log")
            file.writeText(
                buildString {
                    appendLine("time=${java.util.Date()}")
                    appendLine("thread=${thread.name}")
                    appendLine(error.stackTraceToString())
                }
            )
        }
        error.printStackTrace()
    }
    application {
        val state = remember {
            AppState(AppPaths.dataDir).also {
                it.applyReviewTemplate()
                com.ilyskyo.blancall.desktop.app.Strings.use(it.prefs.language)
            }
        }
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val targetW = min(1280, (bounds.width - 80).coerceAtLeast(960))
        val targetH = min(820, (bounds.height - 80).coerceAtLeast(640))
        val savedGeometry = remember { WindowGeometry.load() }
        var showCloseDialog by remember { mutableStateOf(false) }
        var rememberChoice by remember { mutableStateOf(false) }
        var awtWindow by remember { mutableStateOf<java.awt.Window?>(null) }
        val windowState = rememberWindowState(
            size = DpSize(800.dp, 600.dp),
            position = WindowPosition(Alignment.Center),
        )

        Window(
            onCloseRequest = {
                awtWindow?.let { WindowGeometry.save(it) }
                when (state.prefs.closeAction) {
                    "tray" -> awtWindow?.isVisible = false
                    "exit" -> exitApplication()
                    else -> showCloseDialog = true
                }
            },
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else when {
                    event.isCtrlPressed && event.key == Key.N -> {
                        state.navigate(Screen.Import); true
                    }
                    event.isCtrlPressed && event.key == Key.O -> {
                        state.navigate(Screen.Import); true
                    }
                    event.isCtrlPressed && event.key == Key.F -> {
                        state.navigate(Screen.Search); true
                    }
                    event.isCtrlPressed && event.key == Key.Comma -> {
                        state.navigate(Screen.Settings); true
                    }
                    event.key == Key.Escape && state.canGoBack() -> {
                        state.back(); true
                    }
                    event.key == Key.F11 -> {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                            else WindowPlacement.Fullscreen
                        true
                    }
                    else -> false
                }
            },
            title = "Blancall",
            state = windowState,
        ) {
            awtWindow = window
            MenuBar {
                Menu("文件") {
                    Item("导入文章", shortcut = KeyShortcut(Key.N, ctrl = true)) {
                        state.navigate(Screen.Import)
                    }
                    Item("打开文件导入", shortcut = KeyShortcut(Key.O, ctrl = true)) {
                        state.navigate(Screen.Import)
                    }
                    Item("搜索", shortcut = KeyShortcut(Key.F, ctrl = true)) {
                        state.navigate(Screen.Search)
                    }
                    Separator()
                    Item("设置", shortcut = KeyShortcut(Key.Comma, ctrl = true)) {
                        state.navigate(Screen.Settings)
                    }
                    Separator()
                    Item("退出") {
                        WindowGeometry.save(window)
                        exitApplication()
                    }
                }
                Menu("查看") {
                    Item("首页") { state.navigate(Screen.Home) }
                    Item("文章") { state.navigate(Screen.Articles) }
                    Item("数据") { state.navigate(Screen.Statistics()) }
                    Separator()
                    Item("全屏/退出全屏", shortcut = KeyShortcut(Key.F11)) {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                            else WindowPlacement.Fullscreen
                    }
                }
                Menu("帮助") {
                    Item("打开数据目录") { openFolder(AppPaths.dataDir) }
                    Item("打开备份目录") { openFolder(AppPaths.backupsDir) }
                    Item("打开日志目录") { openFolder(AppPaths.logsDir) }
                    Separator()
                    Item("关于 Blancall") {
                        state.pushNotification(
                            "Blancall 7.6.1-win.2",
                            "纯本地、无账号、无网络、无遥测。数据目录 %LOCALAPPDATA%\\Blancall。",
                        )
                    }
                }
            }
            val tray = remember {
                TrayController(window) {
                    WindowGeometry.save(window)
                    exitApplication()
                }
            }
            LaunchedEffect(Unit) {
                state.notifier = { title, body, _ ->
                    val shown = tray.displayMessage(title, body)
                    if (!shown) state.showToast("$title · $body")
                    tray.updateTooltip("Blancall · $title")
                }
                state.reminders.start()
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.ilyskyo.blancall.desktop.platform.AutoBackup.runIfDue(
                        AppPaths.dataDir,
                        AppPaths.backupsDir,
                    )
                }
            }
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(900, 600)
                installDropTarget(window, state)
                if (savedGeometry != null &&
                    savedGeometry.width >= 900 && savedGeometry.height >= 600 &&
                    savedGeometry.x > -savedGeometry.width && savedGeometry.x < bounds.width &&
                    savedGeometry.y >= 0 && savedGeometry.y < bounds.height
                ) {
                    window.setBounds(
                        savedGeometry.x,
                        savedGeometry.y,
                        savedGeometry.width,
                        savedGeometry.height,
                    )
                } else {
                    window.setSize(targetW, targetH)
                    window.setLocationRelativeTo(null)
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    WindowGeometry.save(window)
                    state.reminders.stop()
                    tray.dispose()
                }
            }

            if (showCloseDialog) {
                AlertDialog(
                    onDismissRequest = { showCloseDialog = false },
                    title = { Text("关闭 Blancall") },
                    text = {
                        Column {
                            Text("选择关闭行为。最小化到托盘后，学习提醒仍可工作。")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = rememberChoice,
                                    onCheckedChange = { rememberChoice = it },
                                )
                                Text("记住我的选择（可在设置中改回）")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (rememberChoice) state.prefs.updateCloseAction("tray")
                            showCloseDialog = false
                            tray.hideWindow()
                        }) { Text("最小化到托盘") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                showCloseDialog = false
                                tray.showWindow()
                            }) { Text("取消") }
                            TextButton(onClick = {
                                if (rememberChoice) state.prefs.updateCloseAction("exit")
                                exitApplication()
                            }) { Text("退出") }
                        }
                    },
                )
            }

            BlancallApp(state)
        }
    }
}

private val DROP_EXTENSIONS = setOf("txt", "md", "pdf", "docx", "epub", "html", "htm", "rtf")

private fun openFolder(dir: File) {
    runCatching {
        dir.mkdirs()
        if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir)
    }
}

/** 拖拽文件到窗口即导入：与「从文件导入」同一读取器，失败的文件单独计数。 */
private fun installDropTarget(window: java.awt.Window, state: AppState) {
    val target = DropTarget()
    target.addDropTargetListener(object : DropTargetAdapter() {
        override fun drop(event: DropTargetDropEvent) {
            try {
                event.acceptDrop(DnDConstants.ACTION_COPY)
                @Suppress("UNCHECKED_CAST")
                val files = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                    ?: emptyList()
                val supported = files.filter { it.isFile && it.extension.lowercase() in DROP_EXTENSIONS }
                if (supported.isEmpty()) {
                    state.showToast("只支持 TXT / Markdown / PDF / DOCX / HTML / RTF 文件")
                    return
                }
                state.scope.launch {
                    var count = 0
                    var failed = 0
                    var lastId = -1L
                    for (file in supported) {
                        val text = withContext(Dispatchers.IO) {
                            runCatching { TextImporter.read(file) }.getOrNull()
                        }
                        if (text.isNullOrBlank()) {
                            failed++
                            continue
                        }
                        val id = state.articles.insert(
                            com.ilyskyo.blancall.data.model.Article(
                                title = file.nameWithoutExtension,
                                content = text.trim(),
                                autoIndent = file.extension.lowercase() in setOf("txt", "md"),
                            )
                        )
                        lastId = id
                        count++
                    }
                    if (count == 0) {
                        state.showToast("没有可导入的文字")
                    } else {
                        state.showToast(
                            if (failed > 0) {
                                Strings.f("已导入 {0} 篇，{1} 个文件读取失败", count, failed)
                            } else {
                                Strings.f("已导入 {0} 篇", count)
                            },
                        )
                        state.navigate(Screen.Reader(lastId))
                    }
                }
            } catch (e: Exception) {
                state.showToast(Strings.f("拖拽导入失败：{0}", e.message))
            } finally {
                event.dropComplete(true)
            }
        }
    })
    runCatching { window.dropTarget = target }
}

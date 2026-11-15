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
import androidx.compose.ui.res.painterResource
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
import com.ilyskyo.blancall.desktop.platform.DeepLink
import com.ilyskyo.blancall.desktop.platform.SingleInstance
import com.ilyskyo.blancall.desktop.platform.TextImporter
import com.ilyskyo.blancall.desktop.platform.TrayController
import com.ilyskyo.blancall.desktop.platform.WindowGeometry
import com.ilyskyo.blancall.desktop.platform.WindowsBackdrop
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

fun main(args: Array<String>) {
    if (!SingleInstance.acquire(args)) {
        if (!SingleInstance.lastForwardSucceeded) {
            JOptionPane.showMessageDialog(
                null,
                "Blancall 已经在运行。请从任务栏或托盘打开已有窗口。",
                "Blancall",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }
        return
    }
    val dirsReady = AppPaths.ensureDirectories()
    com.ilyskyo.blancall.desktop.platform.DesktopLogging.install()
    com.ilyskyo.blancall.platform.Log.i("Blancall", "startup: data=${AppPaths.dataDir.absolutePath}")
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
        com.ilyskyo.blancall.desktop.platform.DesktopLogging.logCrash(thread, error)
        error.printStackTrace()
    }
    application {
        val state = remember {
            AppState(AppPaths.dataDir).also {
                it.applyReviewTemplate()
                com.ilyskyo.blancall.desktop.app.Strings.use(it.prefs.language)
                if (!dirsReady) {
                    it.reportStorageError(
                        Strings.f(
                            "数据目录不可写：{0}。请检查目录权限或安全软件拦截；在恢复前，新增内容不会落盘。",
                            AppPaths.rootDir.absolutePath,
                        ),
                    )
                }
            }
        }
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        // 1200×800 复现平板横屏比例；小屏按可用区域收窄，但保留手机布局可用的最小宽度
        val targetW = min(1280, (bounds.width - 80).coerceAtLeast(960))
        val targetH = min(800, (bounds.height - 80).coerceAtLeast(640))
        val savedGeometry = remember { WindowGeometry.load() }
        var showCloseDialog by remember { mutableStateOf(false) }
        var rememberChoice by remember { mutableStateOf(false) }
        var awtWindow by remember { mutableStateOf<java.awt.Window?>(null) }
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
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
                    event.isCtrlPressed && event.key == Key.S -> {
                        if (!state.handleSave()) {
                            state.showToast("当前页面没有可保存的编辑")
                        }
                        true
                    }
                    // 面板/弹层优先：AppState 里的 Esc 栈先消费，没人消费才回退导航。
                    event.key == Key.Escape -> state.handleEscape()
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
            icon = painterResource("icon/blancall.png"),
            state = windowState,
        ) {
            awtWindow = window
            val tray = remember {
                TrayController(
                    window,
                    onToday = {
                        window.isVisible = true
                        window.toFront()
                        state.navigate(Screen.TodayPlan)
                    },
                    onSettings = {
                        window.isVisible = true
                        window.toFront()
                        state.navigate(Screen.Settings)
                    },
                    onExit = {
                        WindowGeometry.save(window)
                        exitApplication()
                    },
                )
            }
            LaunchedEffect(Unit) {
                state.notifier = { title, body, _ ->
                    val shown = tray.displayMessage(title, body)
                    if (!shown) state.showToast("$title · $body")
                    tray.updateTooltip("Blancall · $title")
                }
                SingleInstance.setActivationHandler { forwarded ->
                    tray.showWindow()
                    DeepLink.parse(forwarded)?.let { target ->
                        state.navigate(target)
                    }
                }
                DeepLink.parse(args)?.let { state.navigate(it) }
                state.reminders.start()
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.ilyskyo.blancall.desktop.platform.AutoBackup.runIfDue(
                        AppPaths.dataDir,
                        AppPaths.backupsDir,
                    )
                }
            }
            LaunchedEffect(Unit) {
                // 与 ARCHITECTURE §8.2 一致：960×640 以下双栏与详情排版都失去意义。
                window.minimumSize = Dimension(960, 640)
                installDropTarget(window, state)
                if (savedGeometry != null &&
                    savedGeometry.width >= 960 && savedGeometry.height >= 640 &&
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
                WindowsBackdrop.apply(
                    window,
                    enabled = state.prefs.materialMode == "mica",
                    darkTitleBar = state.prefs.themeMode == "dark",
                )
            }
            DisposableEffect(Unit) {
                onDispose {
                    WindowGeometry.save(window)
                    state.reminders.stop()
                    tray.dispose()
                    SingleInstance.release()
                }
            }

            // 原生菜单栏：桌面上「快捷键要有可见入口」，也让文件/视图/帮助可发现。
            MenuBar {
                Menu(Strings.t("文件"), mnemonic = 'F') {
                    Item(Strings.t("导入文章"), shortcut = KeyShortcut(Key.N, ctrl = true)) {
                        state.navigate(Screen.Import)
                    }
                    Item(Strings.t("从文件导入"), shortcut = KeyShortcut(Key.O, ctrl = true)) {
                        state.navigate(Screen.Import)
                    }
                    Item(Strings.t("搜索"), shortcut = KeyShortcut(Key.F, ctrl = true)) {
                        state.navigate(Screen.Search)
                    }
                    Item(Strings.t("今日计划")) { state.navigate(Screen.TodayPlan) }
                    Item(Strings.t("待复习清单")) { state.navigate(Screen.DueList) }
                    Item(Strings.t("练习历史")) { state.navigate(Screen.PracticeHistory()) }
                    Item(Strings.t("错题本")) { state.navigate(Screen.Mistakes()) }
                    Item(Strings.t("数据互换")) { state.navigate(Screen.DataInterchange) }
                    Item(Strings.t("设置"), shortcut = KeyShortcut(Key.Comma, ctrl = true)) {
                        state.navigate(Screen.Settings)
                    }
                    Separator()
                    Item(Strings.t("退出"), shortcut = KeyShortcut(Key.Q, ctrl = true)) {
                        exitApplication()
                    }
                }
                Menu(Strings.t("视图"), mnemonic = 'V') {
                    Item(Strings.t("全屏"), shortcut = KeyShortcut(Key.F11)) {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Fullscreen
                            }
                    }
                    Item(Strings.t("返回"), shortcut = KeyShortcut(Key.Escape)) {
                        state.handleEscape()
                    }
                }
                Menu(Strings.t("帮助"), mnemonic = 'H') {
                    Item(Strings.t("使用引导")) { state.navigate(Screen.Guide) }
                    Item(Strings.t("帮助")) { state.navigate(Screen.Help) }
                    Item(Strings.t("隐私说明")) { state.navigate(Screen.Privacy) }
                    Item(Strings.t("欢迎页")) { state.replayWelcome() }
                    Item(Strings.t("设置")) { state.navigate(Screen.Settings) }
                    Item(Strings.t("打开数据目录")) { openFolder(AppPaths.dataDir) }
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

private val DROP_TEXT_EXTENSIONS = setOf(
    "txt", "md", "pdf", "docx", "doc", "epub", "html", "htm", "rtf",
)
private const val DROP_ZIP_EXTENSION = "zip"

private fun openFolder(dir: File) {
    runCatching {
        dir.mkdirs()
        if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir)
    }
}

/**
 * 拖拽文件到窗口即导入：
 * - 文本类文件走与「从文件导入」相同的读取器；
 * - `.zip` 若是内容包则导入为素材库，若是备份包则提示到设置页导入（不猜测用户意图）。
 * 失败的文件单独计数，绝不假装成功。
 */
private fun installDropTarget(window: java.awt.Window, state: AppState) {
    val target = DropTarget()
    target.addDropTargetListener(object : DropTargetAdapter() {
        override fun drop(event: DropTargetDropEvent) {
            try {
                event.acceptDrop(DnDConstants.ACTION_COPY)
                @Suppress("UNCHECKED_CAST")
                val files = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                    ?: emptyList()
                val supported = files.filter {
                    it.isFile && (it.extension.lowercase() in DROP_TEXT_EXTENSIONS ||
                        it.extension.lowercase() == DROP_ZIP_EXTENSION)
                }
                if (supported.isEmpty()) {
                    state.showToast(
                        "只支持 TXT / Markdown / PDF / DOCX / DOC / EPUB / HTML / RTF / 内容包 ZIP 文件",
                    )
                    return
                }
                state.scope.launch {
                    var count = 0
                    var failed = 0
                    var packCount = 0
                    var backupSeen = false
                    var lastId = -1L
                    for (file in supported) {
                        if (file.extension.lowercase() == DROP_ZIP_EXTENSION) {
                            if (withContext(Dispatchers.IO) { isBackupZip(file) }) {
                                backupSeen = true
                                continue
                            }
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    state.contentPacks.import(file.inputStream(), file.nameWithoutExtension)
                                }.getOrNull()
                            }
                            if (result is com.ilyskyo.blancall.data.contentpack.ContentPackStore.ImportResult.Success) {
                                packCount++
                            } else {
                                failed++
                            }
                            continue
                        }
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
                    if (backupSeen) {
                        state.showToast("这是备份包，请在 设置 → 内容与数据 中导入备份")
                    }
                    when {
                        count > 0 -> {
                            state.showToast(
                                if (failed > 0) {
                                    Strings.f("已导入 {0} 篇，{1} 个文件读取失败", count, failed)
                                } else {
                                    Strings.f("已导入 {0} 篇", count)
                                },
                            )
                            state.navigate(Screen.Reader(lastId))
                        }
                        packCount > 0 -> {
                            state.showToast(Strings.f("已导入 {0} 个内容包", packCount))
                            state.navigate(Screen.ContentPacks)
                        }
                        !backupSeen && failed > 0 -> state.showToast("没有可导入的文字")
                    }
                }
            } catch (e: Exception) {
                state.reportStorageError("拖拽导入", e)
            } finally {
                event.dropComplete(true)
            }
        }
    })
    runCatching { window.dropTarget = target }
}

/** 备份 ZIP 与内容包 ZIP 的判别：备份固定有 `manifest.json` + `files/` 前缀。 */
private fun isBackupZip(file: File): Boolean = runCatching {
    java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zip ->
        var hasManifest = false
        var hasFiles = false
        var seen = 0
        var entry = zip.nextEntry
        while (entry != null && seen < 500 && !(hasManifest && hasFiles)) {
            val name = entry.name.replace('\\', '/')
            if (name == "manifest.json") hasManifest = true
            if (name.startsWith("files/")) hasFiles = true
            seen++
            entry = zip.nextEntry
        }
        hasManifest && hasFiles
    }
}.getOrDefault(false)

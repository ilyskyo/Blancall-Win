package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import java.io.File

internal data class GuidePage(
    val title: String,
    val body: List<String>,
    val actionLabel: String? = null,
    val action: ((AppState) -> Unit)? = null,
)

internal val guidePages = listOf(
    GuidePage(
        title = "把文章变成挖空",
        body = listOf(
            "从首页「导入」进入：粘贴文本、选择 TXT / Markdown / PDF / DOCX / DOC / EPUB / HTML / RTF，或直接把文件拖进窗口。",
            "导入后先生成文章，再在详情页点「开始练习」。没有固定素材要求：任何文章都可以挖空。",
        ),
        actionLabel = "去导入",
        action = { it.navigate(Screen.Import) },
    ),
    GuidePage(
        title = "阅读与遮挡",
        body = listOf(
            "详情页点「阅读模式」进入沉浸阅读；Aa 打开排版设置，宽窗在右侧、窄窗在底部。",
            "背诵遮挡支持短遮挡、长遮挡、混合与自定义配置；点击挡片露出原文，再点遮回。",
            "拖动鼠标可划线、写批注，选区还能直接保存成自定义挖空。",
        ),
        actionLabel = "打开文章列表",
        action = { it.navigate(Screen.Articles) },
    ),
    GuidePage(
        title = "练习与判分",
        body = listOf(
            "句子挖空、字词挖空、反向默写、自定义挖空四种模式共用同一套判分：标点、全半角、大小写与数字小数点都有容错。",
            "两级提示：弱提示给字数，强提示给首字；10 秒无输入会自动淡显下一个字。",
            "中文手写模型随 Release 单独提供，解压到应用根目录后出现手写入口；识别全程本机完成。",
        ),
        actionLabel = "开始练习",
        action = { it.navigate(Screen.Articles) },
    ),
    GuidePage(
        title = "记忆与今日计划",
        body = listOf(
            "每次练习都会更新文章级与句子级 FSRS-6 排期；今天该复习什么由「今日计划」统一给出。",
            "到期清单按逾期/今天分组；句子卡提供句级回忆与三键评级；错题本按「同句同答案」聚合。",
            "考试日期与每日学习时长会参与今日计划预算，可在设置 → 学习与提醒里调整。",
        ),
        actionLabel = "查看今日计划",
        action = { it.navigate(Screen.TodayPlan) },
    ),
    GuidePage(
        title = "数据只在本机",
        body = listOf(
            "文章、练习记录、FSRS 状态、标签、墨迹、偏好都写入 %LOCALAPPDATA%\\Blancall；没有账号、没有网络请求、没有遥测。",
            "设置 → 内容与数据可导出/导入备份 ZIP（与 Android 双向兼容），并在导入前自动做一份预备份。",
            "换机时先导出备份；卸载程序不会删除学习数据，删除数据目录前请确认已有备份。",
        ),
        actionLabel = "打开数据设置",
        action = { it.navigate(Screen.Settings) },
    ),
)

@Composable
fun GuideScreen(state: AppState) {
    var page by remember { mutableIntStateOf(0) }
    val current = guidePages[page.coerceIn(0, guidePages.lastIndex)]
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 860.dp).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BlancallTopBar(
                title = "使用引导",
                subtitle = Strings.f("第 {0} / {1} 页", page + 1, guidePages.size),
                onBack = { state.back() },
                actions = {
                    GlassButton(onClick = { state.navigate(Screen.Help) }) {
                        Text("帮助", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                current.body.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                current.actionLabel?.let { label ->
                    Spacer(Modifier.height(4.dp))
                    PrimaryPill(label, onClick = { current.action?.invoke(state) })
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassButton(enabled = page > 0, onClick = { page-- }) {
                    Text("上一页", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                guidePages.indices.forEach { index ->
                    TagChip(
                        text = (index + 1).toString(),
                        selected = index == page,
                        onClick = { page = index },
                        compact = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                PrimaryPill(
                    if (page >= guidePages.lastIndex) "完成" else "下一页",
                    onClick = {
                        if (page >= guidePages.lastIndex) state.back() else page++
                    },
                )
            }
        }
    }
}

@Composable
fun HelpScreen(state: AppState) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val shortcuts = listOf(
        "Ctrl+N" to "导入文章",
        "Ctrl+O" to "从文件导入",
        "Ctrl+F" to "搜索",
        "Ctrl+," to "设置",
        "Ctrl+Z / Ctrl+Y" to "练习答案撤销/重做",
        "Ctrl+S" to "保存当前编辑",
        "Ctrl+滚轮" to "阅读字号缩放",
        "Space / ← →" to "句子卡翻面 / 阅读翻章",
        "Esc" to "关闭面板或返回上一层",
        "F11" to "全屏",
    )
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().touchScrollable(listState, scope),
            state = listState,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
                    BlancallTopBar(
                        title = "帮助",
                        subtitle = "功能入口、快捷键与数据安全",
                        onBack = { state.back() },
                        actions = {
                            GlassButton(onClick = { state.navigate(Screen.Guide) }) {
                                Text("使用引导", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    )
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    title = "常用入口",
                    subtitle = "所有快捷键都有菜单或按钮入口",
                ) {
                    shortcuts.forEach { (key, label) ->
                        KeyValueRow(key, Strings.t(label))
                    }
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    title = "数据与恢复",
                    subtitle = "误删程序不会删除学习数据",
                ) {
                    Text(
                        "学习数据固定写入 %LOCALAPPDATA%\\Blancall。导入 Android 备份前会自动生成 pre-import 预备份；每日首次启动还会做一次自动备份（保留最近 7 份）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(onClick = { state.navigate(Screen.DataInterchange) }) {
                            Text("数据互换", style = MaterialTheme.typography.labelMedium)
                        }
                        GlassButton(onClick = { state.navigate(Screen.Privacy) }) {
                            Text("隐私说明", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                SectionCard(
                    Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    title = "恢复与支持",
                    subtitle = "没有账号系统与云端",
                ) {
                    Text(
                        "程序崩溃后可在设置 → 内容与数据 → 导出诊断包；诊断包只含环境信息与日志，不含文章正文。备份与诊断包都要由你手动保存，应用不会上传任何内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        DesktopScrollbar(
            listState,
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(12.dp, 12.dp, 4.dp, 12.dp),
        )
    }
}

@Composable
fun PrivacyScreen(state: AppState) {
    val text = remember { loadPrivacyText() }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 860.dp).fillMaxSize().padding(24.dp)) {
            BlancallTopBar(
                title = "隐私说明",
                subtitle = "本机存储 · 无账号 · 无网络请求",
                onBack = { state.back() },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(end = 16.dp, bottom = 24.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Default),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 随包 PRIVACY.md 优先：安装版与便携版都由打包脚本复制到程序根目录；
 * 开发时从工作目录读取。都没有时显示摘要，绝不联网取。
 */
private fun loadPrivacyText(): String {
    val candidates = buildList {
        val appPath = System.getProperty("jpackage.app-path")
        if (!appPath.isNullOrBlank()) add(File(File(appPath).parentFile, "PRIVACY.md"))
        add(File(System.getProperty("user.dir"), "PRIVACY.md"))
        add(File(System.getProperty("user.home"), "PRIVACY.md"))
    }
    candidates.firstOrNull { it.isFile }?.let { file ->
        runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return """
        Blancall 是纯本地应用：不收集账号、遥测、广告与崩溃上报，不引入 HTTP 客户端，不发起网络请求。
        文章、练习记录、FSRS 状态、标签、墨迹与偏好只写入 %LOCALAPPDATA%\\Blancall。
        删除该目录即清除全部本地数据；卸载程序默认不删除该目录。
        诊断包仅在设置页手动导出，含环境信息、日志与数据文件清单，不含文章正文。
    """.trimIndent()
}

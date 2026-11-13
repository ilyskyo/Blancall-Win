package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.platform.FileDialogs
import com.ilyskyo.blancall.desktop.platform.PdfPreview
import com.ilyskyo.blancall.desktop.platform.TextImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImportScreen(state: AppState) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pdfPreview by remember { mutableStateOf<PdfPreview.Result?>(null) }
    var pdfName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun suggestedTitle(): String {
        if (title.isNotBlank()) return title.trim()
        return content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(40)
            ?: "未命名"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 1000.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { state.back() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text("导入文章", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val text = runCatching {
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            .getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                    }.getOrNull()
                    if (text.isNullOrBlank()) {
                        state.showToast("剪贴板里没有可导入的文字")
                    } else {
                        content = text
                        sourceName = "剪贴板"
                        if (title.isBlank()) {
                            title = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                                ?.take(40).orEmpty()
                        }
                        state.showToast(Strings.f("已从剪贴板读取 {0} 字", text.length))
                    }
                },
            ) { Text("从剪贴板导入") }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val file = FileDialogs.open(
                        "选择文件",
                        listOf(".txt", ".md", ".pdf", ".docx", ".doc", ".epub", ".html", ".htm", ".rtf"),
                    )
                    if (file != null) {
                        if (file.extension.lowercase() in IMAGE_EXTENSIONS) {
                            state.showToast("图片不能直接导入；请先用 OCR/文字版整理成文本")
                            return@OutlinedButton
                        }
                        if (file.extension.equals("pdf", ignoreCase = true)) {
                            scope.launch {
                                busy = true
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { PdfPreview.load(file) }.getOrNull()
                                    }
                                    if (result == null || result.text.isBlank()) {
                                        state.showToast("PDF 里没有可导入的文字")
                                    } else {
                                        pdfPreview = result
                                        pdfName = file.name
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                            return@OutlinedButton
                        }
                        scope.launch {
                            busy = true
                            try {
                                val text = withContext(Dispatchers.IO) { TextImporter.read(file) }
                                if (text.isBlank()) {
                                    state.showToast("文件里没有可导入的文字")
                                } else {
                                    content = text
                                    sourceName = file.name
                                    if (title.isBlank()) title = file.nameWithoutExtension
                                    state.showToast(Strings.f("已读取 {0}", file.name))
                                }
                            } catch (e: Exception) {
                                state.showToast(Strings.f("读取失败：{0}", e.message))
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("从文件导入")
            }
        }

        Column(
            Modifier.fillMaxWidth().widthIn(max = 1000.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("标题（留空自动取首行）") },
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().height(420.dp),
                label = { Text("正文（粘贴文本，或从 TXT 读取）") },
                placeholder = { Text("把要背诵的文章粘贴到这里…") },
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOfNotNull(
                        sourceName?.let { Strings.f("来源：{0}", it) },
                        Strings.f("{0} 字", content.length),
                        Strings.f("{0} 段", content.lineSequence().count { it.isNotBlank() }),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { state.back() }) { Text("取消") }
                    Button(
                        enabled = content.isNotBlank() && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val article = Article(
                                        title = suggestedTitle(),
                                        content = content.trim(),
                                        autoIndent = true,
                                    )
                                    val id = state.articles.insert(article)
                                    state.prefs.updateLastArticleId(id)
                                    state.showToast(Strings.f("已导入《{0}》", article.title))
                                    state.navigate(Screen.Reader(id))
                                } catch (e: Exception) {
                                    state.showToast(Strings.f("保存失败：{0}", e.message))
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("保存并阅读") }
                }
            }
            Text(
                "支持粘贴文本，以及 TXT / Markdown / PDF / DOCX / DOC / EPUB / HTML / RTF 文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val preview = pdfPreview
    if (preview != null) {
        Dialog(onDismissRequest = { pdfPreview = null }) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(Strings.f("PDF 预览 · {0}", pdfName), style = MaterialTheme.typography.titleMedium)
                    Text(
                        Strings.f(
                            "显示前 {0} 页 / 共 {1} 页 · 抽取 {2} 字",
                            preview.pages.size,
                            preview.totalPages,
                            preview.text.length,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(preview.pages) { page ->
                            Image(
                                bitmap = page.toComposeImageBitmap(),
                                contentDescription = "PDF 页面",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { pdfPreview = null }) { Text("取消") }
                        Button(onClick = {
                            content = preview.text
                            sourceName = pdfName
                            if (title.isBlank()) title = pdfName.substringBeforeLast('.')
                            pdfPreview = null
                            state.showToast(Strings.f("已读取 {0}", pdfName))
                        }) { Text("使用这份文本") }
                    }
                }
            }
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "tif", "tiff")

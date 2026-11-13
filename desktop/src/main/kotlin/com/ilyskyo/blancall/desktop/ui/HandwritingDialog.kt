package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.handwriting.HandwritingScript
import com.ilyskyo.blancall.handwriting.HandwritingSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * 手写输入面板：白底画布 → 与 Android 相同的灰度化 → 离线 NCNN 识别 → 候选点选。
 *
 * 模型不可用时调用方不显示入口；识别失败只提示「没认出」，绝不自动填一个猜测值。
 */
@Composable
fun HandwritingDialog(
    state: AppState,
    answer: String,
    onPick: (Char) -> Unit,
    onDismiss: () -> Unit,
    onPickText: ((String) -> Unit)? = null,
) {
    val script = remember(answer) { HandwritingScript.forAnswer(answer) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var candidates by remember { mutableStateOf<List<com.ilyskyo.blancall.desktop.handwriting.HandwritingRecognizer.Candidate>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("手写输入", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (script == HandwritingScript.Chinese) "中文模型（HWDB 7356 类）" else "英文/数字模型（EMNIST 47 类）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Canvas(
                    Modifier.size(360.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> strokes.add(listOf(offset)) },
                                onDrag = { change, _ ->
                                    val last = strokes.lastOrNull() ?: return@detectDragGestures
                                    strokes[strokes.lastIndex] = last + change.position
                                },
                            )
                        },
                ) {
                    strokes.forEach { stroke ->
                        if (stroke.size == 1) {
                            drawCircle(Color(0xFF1B1B1B), radius = 6.dp.toPx(), center = stroke.first())
                        } else if (stroke.size > 1) {
                            val path = Path().apply {
                                moveTo(stroke.first().x, stroke.first().y)
                                stroke.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, Color(0xFF1B1B1B), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                }
                if (candidates.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        candidates.forEach { candidate ->
                            Button(onClick = { onPick(candidate.char) }) {
                                Text(
                                    Strings.f(
                                        "{0}  {1}%",
                                        candidate.char,
                                        (candidate.confidence * 100).toInt(),
                                    ),
                                )
                            }
                        }
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            strokes.clear()
                            candidates = emptyList()
                            message = null
                        }) { Text("清除") }
                        if (onPickText != null && script == HandwritingScript.Chinese) {
                            TextButton(
                                enabled = strokes.isNotEmpty() && !busy,
                                onClick = {
                                    busy = true
                                    message = null
                                    val snapshot = strokes.map { it.toList() }
                                    scope.launch {
                                        val text = withContext(Dispatchers.Default) {
                                            recognizeLine(state, snapshot, script)
                                        }
                                        if (text.isNullOrBlank()) {
                                            message = "没有可识别的内容（写大一点再试）"
                                        } else {
                                            onPickText(text)
                                        }
                                        busy = false
                                    }
                                },
                            ) { Text("连写识别") }
                        }
                        TextButton(onClick = onDismiss) { Text("取消") }
                    }
                    Button(
                        enabled = strokes.isNotEmpty() && !busy,
                        onClick = {
                            busy = true
                            message = null
                            val snapshot = strokes.map { it.toList() }
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    val gray = renderStrokesToGray(snapshot, 360)
                                    state.handwriting.recognize(gray, 360, 360, script)
                                }
                                candidates = result
                                if (result.isEmpty()) message = "没有可识别的内容（写大一点再试）"
                                busy = false
                            }
                        },
                    ) { Text(if (busy) "识别中…" else "识别") }
                }
            }
        }
    }
}

/**
 * 连写识别：渲染整行 → 列投影拆字 → 逐段 top-1 拼接。
 * 任一段完全认不出就跳过该段（不猜），全部失败返回 null。
 */
private fun recognizeLine(
    state: AppState,
    strokes: List<List<Offset>>,
    script: HandwritingScript,
): String? {
    val size = 360
    val gray = renderStrokesToGray(strokes, size)
    val segments = HandwritingSegmenter.split(gray, size, size)
    if (segments.isEmpty()) return null
    val out = StringBuilder()
    for (segment in segments) {
        val pad = 8
        val left = (segment.left - pad).coerceAtLeast(0)
        val right = (segment.right + pad).coerceAtMost(size)
        val width = right - left
        if (width <= 0) continue
        val cropped = ByteArray(width * size)
        for (y in 0 until size) {
            System.arraycopy(gray, y * size + left, cropped, y * width, width)
        }
        val candidate = state.handwriting.recognize(cropped, width, size, script).firstOrNull()
        if (candidate != null) out.append(candidate.char)
    }
    return out.toString().ifBlank { null }
}

/** 把笔画画到白底位图并转灰度（与 Android `toGrayscaleBytes` 同口径：白底黑字）。 */
private fun renderStrokesToGray(strokes: List<List<Offset>>, size: Int): ByteArray {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = AwtColor.WHITE
        g.fillRect(0, 0, size, size)
        g.color = AwtColor.BLACK
        g.stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        strokes.forEach { stroke ->
            if (stroke.size == 1) {
                val p = stroke.first()
                g.fillOval((p.x - 6).toInt(), (p.y - 6).toInt(), 12, 12)
            } else if (stroke.size > 1) {
                for (i in 1 until stroke.size) {
                    val a = stroke[i - 1]
                    val b = stroke[i]
                    g.drawLine(a.x.toInt(), a.y.toInt(), b.x.toInt(), b.y.toInt())
                }
            }
        }
    } finally {
        g.dispose()
    }
    val gray = ByteArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val rgb = img.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val gg = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            gray[y * size + x] = ((r * 299 + gg * 587 + b * 114) / 1000).toByte()
        }
    }
    return gray
}

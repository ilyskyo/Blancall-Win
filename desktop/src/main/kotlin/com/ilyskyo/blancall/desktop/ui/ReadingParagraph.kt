package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ilyskyo.blancall.data.repository.ReadingMark

/**
 * 阅读段落：支持鼠标拖选划线/批注，并把已有标注画在正文上。
 *
 * 坐标口径与 Android 一致：段落索引 + 段内 `[a, e)`（相对 `ReaderOcclusion.splitParagraphs` 的 trim 文本）。
 */
@Composable
fun ReadingParagraph(
    text: String,
    fontPx: Float,
    lineHeight: Float,
    indent: Boolean,
    marks: List<ReadingMark>,
    onAddMark: (start: Int, end: Int, note: String) -> Unit,
    onDeleteMark: (markId: Long) -> Unit,
    onEditNote: (markId: Long, note: String) -> Unit,
    onSaveCloze: ((start: Int, end: Int) -> Unit)? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selection by remember(text) { mutableStateOf<IntRange?>(null) }
    var anchor by remember(text) { mutableStateOf(-1) }
    var noteTarget by remember(text) { mutableStateOf<IntRange?>(null) }
    var noteText by remember(text) { mutableStateOf("") }
    var editingMarkId by remember(text) { mutableStateOf<Long?>(null) }

    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val markColor = Color(0xFFF9A825)

    Text(
        text = text,
        fontSize = fontPx.sp,
        lineHeight = (fontPx * lineHeight).sp,
        style = MaterialTheme.typography.bodyLarge.copy(
            textIndent = if (indent) TextIndent(firstLine = 2.em) else TextIndent(),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                marks.forEach { mark ->
                    rangeRects(l, mark.a, mark.e).forEach { r ->
                        drawRect(
                            color = markColor.copy(alpha = 0.14f),
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                        )
                        drawRect(
                            color = markColor,
                            topLeft = Offset(r.left, r.bottom - 2.dp.toPx()),
                            size = Size(r.width, 2.dp.toPx()),
                        )
                    }
                }
                selection?.let { sel ->
                    rangeRects(l, sel.first, sel.last + 1).forEach { r ->
                        drawRect(
                            color = highlight,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                        )
                    }
                }
            }
            .pointerInput(text) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val l = layout ?: return@detectDragGestures
                        val offset = l.getOffsetForPosition(pos).coerceIn(0, (text.length - 1).coerceAtLeast(0))
                        anchor = offset
                        selection = offset..offset
                    },
                    onDrag = { change, _ ->
                        val l = layout ?: return@detectDragGestures
                        if (anchor < 0) return@detectDragGestures
                        val offset = l.getOffsetForPosition(change.position)
                            .coerceIn(0, (text.length - 1).coerceAtLeast(0))
                        selection = minOf(anchor, offset)..maxOf(anchor, offset)
                    },
                )
            },
        onTextLayout = { layout = it },
    )

    selection?.let { sel ->
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = {
                onAddMark(sel.first, sel.last + 1, "")
                selection = null
            }) { Text("划线") }
            TextButton(onClick = {
                noteTarget = sel
                noteText = ""
                editingMarkId = null
            }) { Text("批注") }
            if (onSaveCloze != null) {
                TextButton(onClick = {
                    onSaveCloze(sel.first, sel.last + 1)
                    selection = null
                }) { Text("存为挖空") }
            }
            TextButton(onClick = { selection = null }) { Text("取消") }
        }
    }

    marks.filter { it.hasNote }.forEach { mark ->
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "批注：${mark.note}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                noteTarget = mark.a until mark.e
                noteText = mark.note
                editingMarkId = mark.id
            }) { Text("改") }
            TextButton(onClick = { onDeleteMark(mark.id) }) { Text("删") }
        }
    }

    val target = noteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text(if (editingMarkId == null) "添加批注" else "修改批注") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("批注内容") },
                    minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val note = noteText.trim()
                    val markId = editingMarkId
                    if (markId != null) onEditNote(markId, note)
                    else onAddMark(target.first, target.last + 1, note)
                    noteTarget = null
                    selection = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { noteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** [start, end) 在文本布局里占用的逐行矩形（与 Android `rangeRects` 同算法）。 */
private fun rangeRects(
    layout: TextLayoutResult,
    start: Int,
    endExclusive: Int,
): List<androidx.compose.ui.geometry.Rect> {
    val len = layout.layoutInput.text.length
    val end = endExclusive.coerceAtMost(len)
    if (start >= end || start < 0) return emptyList()
    var line = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end - 1)
    val res = mutableListOf<androidx.compose.ui.geometry.Rect>()
    while (true) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line, visibleEnd = true)
        val cs = maxOf(start, lineStart)
        val ce = minOf(end - 1, lineEnd - 1)
        if (ce >= cs) {
            val left = layout.getBoundingBox(cs).left
            val right = layout.getBoundingBox(ce).right
            var top = layout.getBoundingBox(cs).top
            var bottom = layout.getBoundingBox(cs).bottom
            for (i in cs + 1..ce) {
                val bbox = layout.getBoundingBox(i)
                if (bbox.top < top) top = bbox.top
                if (bbox.bottom > bottom) bottom = bbox.bottom
            }
            res.add(androidx.compose.ui.geometry.Rect(left, top, right, bottom))
        }
        line++
        if (line > lastLine) break
    }
    return res
}

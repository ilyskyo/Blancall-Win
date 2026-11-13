package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TagPickerDialog(
    state: AppState,
    article: Article,
    onDismiss: () -> Unit,
) {
    val data by state.tags.data.collectAsState()
    var selected by remember(article.id, data.tags) {
        mutableStateOf(data.links[article.id].orEmpty())
    }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文章标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (data.tags.isEmpty()) {
                    Text("还没有标签。先到「设置 → 文章标签」创建。")
                    TextButton(onClick = {
                        onDismiss()
                        state.navigate(Screen.Tags)
                    }) { Text("去创建标签") }
                } else {
                    data.tags.forEach { tag ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = tag.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + tag.id else selected - tag.id
                                },
                            )
                            Box(Modifier.size(12.dp).background(Color(0xFF000000L or tag.color.toLong()), CircleShape))
                            Text(tag.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = data.tags.isNotEmpty(),
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { state.tags.setArticleTags(article.id, selected) }
                        state.showToast("标签已更新")
                        onDismiss()
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

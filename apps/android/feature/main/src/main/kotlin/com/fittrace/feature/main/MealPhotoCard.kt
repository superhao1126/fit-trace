package com.fittrace.feature.main

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MealPhotoCard(
    state: EditorState,
    model: RecordEditorViewModel,
    editing: Boolean,
    onReading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    fun upload(selected: Uri) {
        reading = true
        onReading(true)
        scope.launch {
            val result =
                withContext(Dispatchers.IO) { runCatching { readSelectedImage(context, selected) } }
            reading = false
            onReading(false)
            result.fold(
                { model.upload(it.first, it.second) },
                { model.error(it.message ?: "无法读取餐食照片") },
            )
        }
    }
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    MealPhotoSourcePicker(
        visible = choosingSource,
        onDismiss = { choosingSource = false },
        onSelected = { selected ->
            uri = selected.toString()
            model.edit { it.copy(imageUrl = null) }
            upload(selected)
        },
        onError = model::error,
    )
    LaunchedEffect(uri, state.draft.imageUrl, reading) {
        onReading(reading || (uri != null && state.draft.imageUrl == null))
    }
    val image = uri ?: state.draft.imageUrl
    if (editing || image != null)
        InsightsCard {
            Text("餐食照片", fontSize = 14.sp)
            image?.let { PhotoImage(it, Modifier.fillMaxWidth().height(200.dp), fit = true) }
            if (editing) {
                val busy = state.saving || reading
                OutlinedButton(
                    onClick = { choosingSource = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (image == null) "添加餐食照片" else "重新选择餐食照片")
                }
                if (image != null)
                    TextButton(
                        onClick = {
                            uri = null
                            model.edit { it.copy(imageUrl = null) }
                        },
                        enabled = !busy,
                    ) {
                        Text("移除餐食照片")
                    }
                if (uri != null && state.draft.imageUrl == null && !busy)
                    TextButton(onClick = { upload(uri!!.toUri()) }) { Text("重试上传餐食照片") }
                if (state.uploading || reading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    if (state.uploading || reading) "正在上传餐食照片…"
                    else if (state.draft.imageUrl != null) "已上传，保存这餐后生效"
                    else "照片选填，支持 JPG / PNG / WebP，最大 6 MB",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
}

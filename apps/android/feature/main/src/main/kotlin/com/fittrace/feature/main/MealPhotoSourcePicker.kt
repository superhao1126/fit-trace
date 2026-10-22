package com.fittrace.feature.main

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
internal fun MealPhotoSourcePicker(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelected: (Uri) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingCameraPath?.let(::File)
            pendingCameraPath = null
            if (success && file != null) {
                onSelected(
                    FileProvider.getUriForFile(context, "${context.packageName}.meal-photo", file)
                )
            } else {
                file?.delete()
            }
        }
    val album =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                onSelected(it)
            }
        }
    if (visible)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("添加餐食照片") },
            text = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onDismiss()
                            runCatching {
                                    val directory =
                                        File(context.cacheDir, "meal-camera").apply { mkdirs() }
                                    val file = File.createTempFile("meal-", ".jpg", directory)
                                    pendingCameraPath = file.absolutePath
                                    camera.launch(
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.meal-photo",
                                            file,
                                        )
                                    )
                                }
                                .onFailure {
                                    pendingCameraPath?.let(::File)?.delete()
                                    pendingCameraPath = null
                                    onError("无法打开相机，请从相册选择照片")
                                }
                        },
                    ) {
                        Text("拍照")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onDismiss()
                            runCatching {
                                    album.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                                }
                                .onFailure { onError("无法打开相册，请稍后重试") }
                        },
                    ) {
                        Text("从相册选择")
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
}

package com.fittrace.feature.main

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fittrace.core.designsystem.FitDialog
import com.fittrace.core.designsystem.FitDialogButton

/** 原始动作照片随安装包提供；没有匹配素材时不使用其他动作的照片。 */
@Composable
internal fun ExercisePhoto(
    exercise: CatalogExercise,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    val path = exercise.images.getOrNull(index)
    var status by remember(path) { mutableStateOf("动作图加载中") }
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (path == null)
            Text("图文教程", color = Color(0xFF476747), style = MaterialTheme.typography.labelMedium)
        else {
            AsyncImage(
                model = path,
                contentDescription = "${exercise.name}示范图${index + 1}",
                modifier = Modifier.fillMaxSize().semantics { stateDescription = status },
                contentScale = ContentScale.Fit,
                onSuccess = { status = "动作图已加载" },
                onError = { status = "动作图加载失败" },
            )
            if (status == "动作图加载失败") Text("图片暂不可用", color = Color.DarkGray)
        }
    }
}

@Composable
internal fun ExerciseTutorial(
    exercise: CatalogExercise,
    onDismiss: () -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    var linkFailed by remember(exercise.id) { mutableStateOf(false) }
    FitDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${exercise.bodyPart} · ${exercise.equipment}",
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (exercise.images.isNotEmpty()) {
                    var pose by remember(exercise.id) { mutableIntStateOf(0) }
                    ExercisePhoto(exercise, Modifier.fillMaxWidth().aspectRatio(1.5f), pose)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        exercise.images.forEachIndexed { index, _ ->
                            FilterChip(
                                selected = pose == index,
                                onClick = { pose = index },
                                label = { Text("示范图 ${index + 1}") },
                            )
                        }
                    }
                }
                Text("动作步骤", style = MaterialTheme.typography.titleSmall)
                exercise.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                Text("动作要点", style = MaterialTheme.typography.titleSmall)
                Text(exercise.tips)
                if (exercise.mediaSourceUrl.isNotBlank()) {
                    Text(
                        "示范照片：Free Exercise DB · 公共领域",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                val link =
                    Uri.Builder()
                        .scheme("https")
                        .authority("search.bilibili.com")
                        .path("/all")
                        .appendQueryParameter("keyword", "${exercise.name} 动作教程")
                        .build()
                        .toString()
                TextButton(
                    onClick = { linkFailed = runCatching { uriHandler.openUri(link) }.isFailure },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("在哔哩哔哩搜索教程 ↗")
                }
                if (linkFailed) Text("暂时无法打开链接，请稍后重试", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { FitDialogButton(onClick = onDismiss) { Text("看完了") } },
        dismissButton =
            onAdd?.let { add ->
                { FitDialogButton(primary = false, onClick = add) { Text("加入训练") } }
            },
    )
}

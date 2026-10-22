package com.fittrace.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** 页面提示与字段提示共用语义色、间距和读屏通知，不依赖平台 Toast。 */
@Composable
fun FitNotice(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    onRetry: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val foreground =
        if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color =
            if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        contentColor = foreground,
    ) {
        Column(
            Modifier.padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text,
                style =
                    if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyMedium,
            )
            onRetry?.let {
                TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("重试", color = foreground)
                }
            }
        }
    }
}

@Composable
fun FitInlineMessage(text: String, error: Boolean = true) =
    FitNotice(text, error = error, compact = true)

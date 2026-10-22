package com.fittrace.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 提示、确认与弹窗表单共用框架；正文可滚动，操作区始终可达。 */
@Composable
fun FitDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    Dialog(onDismissRequest, properties) {
        Surface(
            modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 20.dp).imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                title?.let {
                    Box(Modifier.semantics { heading() }) {
                        ProvideTextStyle(
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        ) {
                            it()
                        }
                    }
                }
                text?.let {
                    Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun FitDialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val container =
        if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    if (primary)
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor =
                        if (destructive) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary,
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            content = content,
        )
    else
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            content = content,
        )
}

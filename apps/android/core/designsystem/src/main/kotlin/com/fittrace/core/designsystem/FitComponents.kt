package com.fittrace.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FitCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun FitPage(
    title: String,
    onBack: (() -> Unit)? = null,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .imePadding()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background,
                    ),
                    endY = 650f,
                )
            )
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) TextButton(onClick = onBack) { Text("返回") }
            else Spacer(Modifier.width(12.dp))
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            action()
        }
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun FitMessage(text: String, onRetry: (() -> Unit)? = null, error: Boolean = false) {
    FitNotice(text, error = error, onRetry = onRetry)
}

@Composable
fun FitSaveButton(saving: Boolean, onClick: () -> Unit, label: String = "保存记录") {
    Button(
        onClick = onClick,
        enabled = !saving,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(label, fontWeight = FontWeight.Bold)
    }
}

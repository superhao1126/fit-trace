package com.fittrace.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun InsightsPage(
    title: String,
    subtitle: String,
    onRecord: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier.offset(y = with(LocalDensity.current) { -scroll.value.toDp() })
                .fillMaxWidth()
                .height(205.dp)
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .background(
                    Brush.linearGradient(
                        if (dark) listOf(Color(0xFF29482A), Color(0xFF164F36), Color(0xFF1D2D23))
                        else listOf(Color(0xFFDDF8B4), Color(0xFFADF4CA), Color(0xFFEDF6EB))
                    )
                )
        )
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null)
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.width(32.dp).semantics { contentDescription = "返回" },
                    ) {
                        Text("‹", fontSize = 26.sp)
                    }
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        Modifier.padding(top = 10.dp),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRecord, contentPadding = PaddingValues(0.dp)) {
                    Text("＋ 记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            content()
        }
    }
}

@Composable
internal fun InsightsSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(38.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp))
            .padding(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (index == selected) MaterialTheme.colorScheme.surface
                        else Color.Transparent
                    )
                    .semantics { this.selected = index == selected }
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                    color =
                        if (index == selected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun InsightsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun InsightsEmpty(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 290.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error)
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "!",
                    Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        else Image(painterResource(R.drawable.design_sprout), null, Modifier.size(72.dp))
        Text(title, Modifier.padding(top = 20.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            Modifier.padding(top = 10.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error)
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 20.dp).widthIn(min = 200.dp),
            ) {
                Text(action)
            }
        else
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 20.dp).widthIn(min = 240.dp),
            ) {
                Text(action, color = MaterialTheme.colorScheme.onPrimary)
            }
    }
}

@Composable
internal fun InsightsSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)))
}

@Composable
internal fun InsightsRetry(message: String, onRetry: () -> Unit, detail: String? = null) {
    InsightsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(message, fontSize = 12.sp)
                detail?.let {
                    Text(
                        it,
                        Modifier.padding(top = 6.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onRetry) { Text("重试 ›", fontSize = 12.sp) }
        }
    }
}

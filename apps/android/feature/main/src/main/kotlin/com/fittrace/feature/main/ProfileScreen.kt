package com.fittrace.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onGoal: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.loading && state.profile != null,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = profilePadding())
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "记录日常，看见改变",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(onClick = onSettings) { Text("设置") }
            }
            if (state.loading && state.profile == null)
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics { contentDescription = "正在加载个人资料" }
                )
            state.error?.let { ProfileError(it, onRefresh) }
            state.profile?.let { user ->
                ProfileCard(onClick = onProfile) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ProfileAvatar(user.avatarUrl)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                user.displayName,
                                fontSize = 19.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                user.email,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${user.createdAt.profileDate("yyyy年M月d日")}加入",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                ProfileNavigationRow("记录档案", "按日期回看记录", R.drawable.profile_archive, onArchive)
                state.bodyError?.let { ProfileError(it, onRefresh) }
                Text("我的目标", fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                val goal = user.goal
                if (goal == null)
                    ProfileNavigationRow(
                        "设置健身目标",
                        "减脂、增肌或保持，找到适合自己的节奏",
                        R.drawable.profile_avatar,
                        onGoal,
                    )
                else
                    ProfileCard(onClick = onGoal) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${goal.type.goalLabel()}计划",
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "调整 ›",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileWeight(
                                "当前体重",
                                state.body?.weight,
                                Modifier.weight(1f),
                                if (state.loading) "加载中"
                                else if (state.bodyError != null) "—" else "未记录",
                            )
                            Text(
                                "→",
                                Modifier.padding(horizontal = 14.dp),
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ProfileWeight("目标体重", goal.targetWeight, Modifier.weight(1f))
                        }
                        val progress =
                            if (state.bodyError == null) goal.progress(state.body?.weight) else null
                        progress?.let {
                            Text(
                                "已完成 $it%",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                            )
                            LinearProgressIndicator(
                                progress = { it / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                        }
                        Text(
                            "起始 ${goal.startWeight.weightText()} kg" +
                                (goal.targetDate?.let { " · 目标日期 ${it.profileDate("M月d日")}" }
                                    ?: ""),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        state.body?.let {
                            Text(
                                "体重更新于 ${it.recordedAt.profileDate("M月d日")}",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
            }
        }
    }
}

@Composable
internal fun ProfileCard(
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun ProfileNavigationRow(
    title: String,
    subtitle: String?,
    icon: Int,
    onClick: () -> Unit,
) {
    ProfileCard(onClick) {
        Row(
            Modifier.heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painterResource(icon),
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 15.sp, lineHeight = 20.sp)
                subtitle?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painterResource(R.drawable.profile_right),
                null,
                Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ProfileAvatar(url: String?, size: Dp = 64.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFFE3F1EC)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.profile_avatar), null, Modifier.size(size * .45f))
        if (!url.isNullOrBlank())
            AsyncImage(
                model = url,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
    }
}

@Composable
private fun ProfileWeight(
    label: String,
    weight: Double?,
    modifier: Modifier,
    empty: String = "未记录",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                weight?.weightText() ?: empty,
                fontSize = if (weight == null) 17.sp else 30.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (weight != null)
                Text(
                    " kg",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
        }
    }
}

@Composable
internal fun ProfileError(error: String, retry: (() -> Unit)? = null) {
    ProfileCard {
        Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        retry?.let { TextButton(onClick = it) { Text("重试") } }
    }
}

@Composable
internal fun profilePadding(): Dp =
    if (with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } < 360.dp)
        16.dp
    else 24.dp

internal fun String.profileDate(pattern: String): String =
    runCatching {
            OffsetDateTime.parse(this)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(pattern))
        }
        .getOrDefault("")

internal fun GoalType.goalLabel() =
    when (this) {
        GoalType.LOSE_FAT -> "减脂"
        GoalType.GAIN_MUSCLE -> "增肌"
        GoalType.MAINTAIN -> "保持"
    }

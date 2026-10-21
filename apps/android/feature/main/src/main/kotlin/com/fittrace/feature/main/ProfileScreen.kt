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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    onRecords: (RecordKind) -> Unit = {},
    onSecurity: () -> Unit = {},
    onPolicy: (Boolean) -> Unit = {},
    onAbout: () -> Unit = {},
    themeMode: String = "SYSTEM",
    onThemeMode: (String) -> Unit = {},
) {
    var appearance by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val header =
        if (dark)
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
        else listOf(Color(0xFFD3F8C4), Color(0xFFA8F1C9))
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        PullToRefreshBox(
            isRefreshing = state.loading && state.profile != null,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(header),
                            RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        )
                        .padding(horizontal = profilePadding())
                        .padding(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "我的",
                            Modifier.weight(1f),
                            fontSize = 28.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
                        ) {
                            TextButton(
                                onClick = onArchive,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    "记录档案 ›",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (state.profile == null) {
                        if (state.loading) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(72.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = .5f),
                                            CircleShape,
                                        )
                                )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(.6f)
                                            .height(18.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = .5f),
                                                RoundedCornerShape(8.dp),
                                            )
                                    )
                                    Box(
                                        Modifier.fillMaxWidth(.85f)
                                            .height(12.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = .5f),
                                                RoundedCornerShape(6.dp),
                                            )
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().semantics {
                                    contentDescription = "正在加载个人资料"
                                }
                            )
                        }
                    } else {
                        val user = state.profile
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 88.dp)
                                .clickable(onClick = onProfile),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ProfileAvatar(user.avatarUrl, 72.dp)
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    user.displayName,
                                    fontSize = 20.sp,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    user.email,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                "›",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val goal = user.goal
                        val current = if (state.bodyError == null) state.body?.weight else null
                        val progress = goal?.progress(current)
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        if (dark)
                                            listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.surface,
                                            )
                                        else listOf(Color(0xFF70D59C), Color(0xFFC2EC9B))
                                    )
                                )
                                .clickable(onClick = onGoal)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Image(
                                painterResource(R.drawable.design_sprout),
                                null,
                                Modifier.width(62.dp).height(76.dp),
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    if (goal == null) "设置健身目标" else "我的${goal.type.goalLabel()}目标",
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (goal == null) "找到适合自己的节奏"
                                    else
                                        "${current?.weightText() ?: "—"} → ${goal.targetWeight.weightText()} kg",
                                    fontSize = if (goal == null) 14.sp else 20.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (goal == null) "减脂、增肌或保持"
                                    else if (state.bodyError != null) "体重暂时无法读取"
                                    else if (progress != null) "已完成 $progress% · 慢慢靠近目标"
                                    else if (goal.type == GoalType.MAINTAIN) "保持节奏，记录每一点变化"
                                    else "记录体重后查看变化",
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                            Text(
                                if (goal == null) "设置 ›" else "调整 ›",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Column(
                    Modifier.padding(horizontal = profilePadding())
                        .padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    state.error?.let { ProfileError(it, onRefresh) }
                    state.bodyError?.let { ProfileError(it, onRefresh) }
                    ProfileMenuGroup("记录与工具") {
                        ProfileMenuRow("身体记录", R.drawable.home_body) { onRecords(RecordKind.BODY) }
                        ProfileMenuRow("饮食记录", R.drawable.home_meal) { onRecords(RecordKind.MEAL) }
                        ProfileMenuRow("运动记录", R.drawable.home_workout) {
                            onRecords(RecordKind.WORKOUT)
                        }
                        ProfileMenuRow("身材照片", R.drawable.home_photo) {
                            onRecords(RecordKind.PHOTO)
                        }
                    }
                    ProfileMenuGroup("账号设置") {
                        ProfileMenuRow(
                            "编辑资料",
                            R.drawable.home_profile,
                            enabled = state.profile != null,
                            onClick = onProfile,
                        )
                        ProfileMenuRow(
                            "我的目标",
                            R.drawable.profile_avatar,
                            state.profile?.goal?.type?.goalLabel(),
                            state.profile != null,
                            onGoal,
                        )
                        ProfileMenuRow("登录安全", R.drawable.profile_archive, onClick = onSecurity)
                    }
                    ProfileMenuGroup("通用") {
                        ProfileMenuRow("显示模式", R.drawable.home_photo, themeMode.modeLabel()) {
                            appearance = true
                        }
                        ProfileMenuRow("显示与设置", R.drawable.home_profile, onClick = onSettings)
                    }
                    ProfileMenuGroup("关于循形") {
                        ProfileMenuRow("用户协议", R.drawable.profile_archive) { onPolicy(false) }
                        ProfileMenuRow("隐私政策", R.drawable.profile_archive) { onPolicy(true) }
                        ProfileMenuRow("版本与关于", R.drawable.home_home, onClick = onAbout)
                    }
                }
            }
        }
        if (appearance) ProfileAppearanceSheet(themeMode, onThemeMode) { appearance = false }
    }
}

@Composable
internal fun ProfileMenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun ProfileMenuRow(
    title: String,
    icon: Int,
    detail: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painterResource(icon),
            null,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .85f else .38f),
        )
        Text(
            title,
            Modifier.weight(1f),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f),
        )
        detail?.let {
            Text(
                it,
                Modifier.widthIn(max = 110.dp),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painterResource(R.drawable.profile_right),
            null,
            Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun String.modeLabel() =
    when (this) {
        "LIGHT" -> "浅色"
        "DARK" -> "深色"
        else -> "跟随系统"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileAppearanceSheet(mode: String, onMode: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("显示模式", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("取消") }
            }
            listOf("SYSTEM", "LIGHT", "DARK").forEach { option ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (mode == option) MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable {
                            onMode(option)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.modeLabel(), Modifier.weight(1f), fontSize = 16.sp)
                    RadioButton(
                        selected = mode == option,
                        onClick = {
                            onMode(option)
                            onDismiss()
                        },
                    )
                }
            }
            Text(
                "选择后立即生效，并保存在本机。",
                Modifier.padding(top = 12.dp),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Modifier.size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
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

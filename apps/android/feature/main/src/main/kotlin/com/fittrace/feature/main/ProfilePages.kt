package com.fittrace.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun ProfilePage(
    title: String,
    onBack: () -> Unit,
    enabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = profilePadding())
            .padding(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.profile_back),
                    stringResource(R.string.back),
                    Modifier.size(18.dp),
                )
            }
            Text(
                title,
                Modifier.weight(1f),
                fontSize = 25.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
internal fun ProfileInfoScreen(user: UserProfile, onBack: () -> Unit, onEdit: () -> Unit) {
    ProfilePage("个人资料", onBack, action = { TextButton(onClick = onEdit) { Text("编辑") } }) {
        Spacer(Modifier.height(8.dp))
        ProfileCard {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("头像", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProfileAvatar(user.avatarUrl, 52.dp)
            }
            ProfileDetailRow("昵称", user.displayName)
            ProfileDetailRow("性别", user.gender.genderLabel())
            ProfileDetailRow("邮箱", user.email)
            ProfileDetailRow("加入时间", user.createdAt.profileDate("yyyy年M月d日"))
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            Modifier.weight(1f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
internal fun ProfileEditScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onNickname: (String) -> Unit,
    onGender: (UserGender) -> Unit,
    onSave: () -> Unit,
    onPassword: () -> Unit,
) {
    ProfilePage("账号设置", onBack, !state.saving) {
        Text("个人资料", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProfileCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ProfileAvatar(state.profile?.avatarUrl)
                Text("头像")
            }
        }
        ProfileField("昵称", state.nickname, onNickname, !state.saving)
        Text("性别 · 选填", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProfileChoices(
            UserGender.entries,
            state.gender,
            UserGender::genderLabel,
            onGender,
            !state.saving,
        )
        Text("性别不影响记录和使用", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.formError?.let { ProfileError(it) }
        Button(
            onClick = onSave,
            enabled = !state.saving && !state.loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (state.saving) "保存中…" else "保存资料")
        }
        Text("登录安全", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProfileCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("登录邮箱", fontSize = 13.sp)
                Text(state.profile?.email.orEmpty(), Modifier.weight(1f), fontSize = 14.sp)
            }
        }
        ProfileNavigationRow("修改密码", null, R.drawable.home_profile, onPassword)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalEditScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onType: (GoalType) -> Unit,
    onTarget: (String) -> Unit,
    onCurrent: (String) -> Unit,
    onDate: (String?) -> Unit,
    onEditWeight: () -> Unit,
    onSave: () -> Unit,
) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    ProfilePage("我的目标", onBack, !state.saving) {
        Text("找到适合自己的节奏", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("你现在更关注什么？", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        ProfileChoices(GoalType.entries, state.goalType, GoalType::goalLabel, onType, !state.saving)
        Text(
            when (state.goalType) {
                GoalType.LOSE_FAT -> "让体重和围度稳步下降"
                GoalType.GAIN_MUSCLE -> "关注体重、训练与身体变化"
                GoalType.MAINTAIN -> "维持当前状态和记录习惯"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProfileCard {
            if (state.editingWeight) {
                ProfileField("当前体重（kg）", state.currentWeight, onCurrent, !state.saving, true)
                Text(
                    "保存目标时会同步记录当前体重",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text("当前体重", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        state.currentWeight,
                        fontSize = 34.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " kg",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                TextButton(
                    onClick = onEditWeight,
                    enabled = !state.saving,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("记录新的体重 ›")
                }
            }
        }
        state.bodyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ProfileField("目标体重（kg）", state.targetWeight, onTarget, !state.saving, true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("目标日期 · 选填", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProfileCard(onClick = if (state.saving) null else ({ calendar = true })) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        state.targetDate?.let {
                            LocalDate.parse(it).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                        } ?: "选择目标日期",
                        fontSize = 15.sp,
                    )
                    Text("›")
                }
            }
            if (state.targetDate != null)
                TextButton(onClick = { onDate(null) }, enabled = !state.saving) { Text("清除日期") }
        }
        Text(
            "日期只是节奏参考，不影响日常记录",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.formError?.let { ProfileError(it) }
        Button(
            onClick = onSave,
            enabled = !state.saving && !state.loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (state.saving) "保存中…" else "保存目标")
        }
    }
    if (calendar) {
        val today = LocalDate.now()
        val date = state.targetDate?.let(LocalDate::parse) ?: today
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            Instant.ofEpochMilli(utcTimeMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate() >= today

                        override fun isSelectableYear(year: Int): Boolean = year >= today.year
                    },
            )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let {
                            onDate(
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toString()
                            )
                        }
                        calendar = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("取消") } },
        ) {
            DatePicker(picker, title = { Text("选择目标日期", Modifier.padding(24.dp)) })
        }
    }
}

@Composable
internal fun ProfileSettingsScreen(
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onPolicy: (Boolean) -> Unit,
    onAbout: () -> Unit,
    mode: String,
    onMode: (String) -> Unit,
    busy: Boolean,
    onLogout: () -> Unit,
) {
    var logout by remember { mutableStateOf(false) }
    ProfilePage("设置", onBack) {
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileNavigationRow("账号设置", "个人资料与登录安全", R.drawable.home_profile, onAccount)
            ProfileNavigationRow("用户协议", "了解使用条款", R.drawable.profile_archive) { onPolicy(false) }
            ProfileNavigationRow("隐私政策", "了解信息使用方式", R.drawable.profile_archive) { onPolicy(true) }
            ProfileNavigationRow(
                stringResource(R.string.about_title),
                "应用介绍与版本信息",
                R.drawable.home_home,
                onAbout,
            )
        }
        Text("显示模式", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProfileChoices(
            listOf("SYSTEM", "LIGHT", "DARK"),
            mode,
            {
                when (it) {
                    "LIGHT" -> "浅色"
                    "DARK" -> "深色"
                    else -> "跟随系统"
                }
            },
            onMode,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { logout = true },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (busy) "退出中…" else "退出登录")
        }
    }
    if (logout)
        AlertDialog(
            onDismissRequest = { logout = false },
            title = { Text("退出登录？") },
            text = { Text("退出后将清除本机登录状态，账号中的记录会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        logout = false
                        onLogout()
                    },
                    enabled = !busy,
                ) {
                    Text("退出登录")
                }
            },
            dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } },
        )
}

@Composable
internal fun ProfileAboutScreen(versionName: String, onBack: () -> Unit, onPolicy: () -> Unit) {
    ProfilePage(stringResource(R.string.about_title), onBack) {
        Column(
            Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painterResource(R.drawable.profile_brand),
                null,
                Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)),
            )
            Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("FitTrace", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.brand_slogan),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProfileCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("当前版本", fontSize = 13.sp)
                Text(stringResource(R.string.version, versionName), fontSize = 14.sp)
            }
        }
        ProfileNavigationRow("用户协议", null, R.drawable.profile_archive, onPolicy)
        Text(
            "记录身体、饮食、训练与身材变化\n用清晰的记录和趋势，找到自己的节奏",
            fontSize = 13.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
        )
    }
}

@Composable
internal fun <T> ProfileChoices(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Button(
                onClick = { onSelect(option) },
                enabled = enabled,
                modifier =
                    Modifier.weight(1f).heightIn(min = 44.dp).semantics {
                        this.selected = option == selected
                    },
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (option == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                        contentColor =
                            if (option == selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Text(label(option), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    number: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value,
            onChange,
            Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled,
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text
                ),
            shape = RoundedCornerShape(14.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                ),
        )
    }
}

internal fun UserGender.genderLabel() =
    when (this) {
        UserGender.UNSPECIFIED -> "不设置"
        UserGender.MALE -> "男"
        UserGender.FEMALE -> "女"
    }

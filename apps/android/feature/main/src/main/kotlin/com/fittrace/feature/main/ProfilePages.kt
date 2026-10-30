package com.fittrace.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

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
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background,
                    ),
                    endY = 700f,
                )
            )
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
    onAvatar: (String?) -> Unit = {},
    onUpload: (ByteArray, String) -> Unit = { _, _ -> },
    onUploadError: (String) -> Unit = {},
    onRetryAvatar: () -> Unit = {},
) {
    var nicknameDialog by rememberSaveable { mutableStateOf(false) }
    var nicknameDraft by rememberSaveable { mutableStateOf("") }
    var nicknameError by remember { mutableStateOf(false) }
    var genderDialog by remember { mutableStateOf(false) }
    var avatarDialog by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val dirty =
        state.nickname != state.profile?.nickname.orEmpty() ||
            state.gender != state.profile?.gender ||
            state.avatarUrl != state.profile?.avatarUrl ||
            state.avatarError != null
    val back = {
        if (!state.saving && !reading) {
            if (dirty) discard = true else onBack()
        }
    }
    androidx.activity.compose.BackHandler { back() }
    val picker =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                reading = true
                scope.launch {
                    val result =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { readSelectedImage(context, uri) }
                        }
                    reading = false
                    result.fold(
                        { onUpload(it.first, it.second) },
                        { onUploadError(it.message ?: "无法读取照片，请重试") },
                    )
                }
            }
        }
    if (avatarDialog)
        FitDialog(
            onDismissRequest = { avatarDialog = false },
            title = { Text("修改头像") },
            text = { Text("选择照片后，点击保存资料统一提交。") },
            confirmButton = {
                FitDialogButton(
                    onClick = {
                        avatarDialog = false
                        picker.launch("image/*")
                    }
                ) {
                    Text("从相册选择")
                }
            },
            dismissButton = {
                FitDialogButton(
                    primary = false,
                    onClick = {
                        avatarDialog = false
                        onAvatar(null)
                    },
                ) {
                    Text("移除头像")
                }
            },
        )
    if (nicknameDialog) {
        FitDialog(
            onDismissRequest = { nicknameDialog = false },
            title = { Text("修改昵称") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitTextField(
                        value = nicknameDraft,
                        onValueChange = {
                            nicknameDraft = it
                            nicknameError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("昵称") },
                        singleLine = true,
                        textStyle =
                            LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    )
                    if (nicknameError) FitInlineMessage("昵称长度应为 1–40 字")
                    else Text("1–40 字", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                FitDialogButton(
                    onClick = {
                        if (nicknameDraft.trim().length !in 1..40) nicknameError = true
                        else {
                            onNickname(nicknameDraft.trim())
                            nicknameDialog = false
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { nicknameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
    if (genderDialog)
        FitDialog(
            onDismissRequest = { genderDialog = false },
            title = { Text("性别") },
            text = {
                Column {
                    UserGender.entries.forEach { gender ->
                        TextButton(
                            onClick = {
                                onGender(gender)
                                genderDialog = false
                            }
                        ) {
                            Text(gender.genderLabel())
                        }
                    }
                }
            },
            confirmButton = { FitDialogButton(onClick = { genderDialog = false }) { Text("取消") } },
        )
    if (discard)
        FitDialog(
            onDismissRequest = { discard = false },
            title = { Text("放弃资料修改？") },
            text = { Text("未保存的资料将不会更新。") },
            confirmButton = {
                FitDialogButton(destructive = true, onClick = onBack) { Text("放弃修改") }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { discard = false }) { Text("继续编辑") }
            },
        )
    ProfilePage("编辑资料", back, !state.saving && !reading) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(state.avatarUrl, 88.dp)
            TextButton(onClick = { avatarDialog = true }, enabled = !state.saving && !reading) {
                Text(if (reading || state.saving) "处理中…" else "修改头像")
            }
        }
        ProfileCard {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !state.saving) {
                    nicknameDraft = state.nickname
                    nicknameError = false
                    nicknameDialog = true
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("昵称")
                Text(state.nickname.ifBlank { "设置昵称" } + "  ›")
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !state.saving) {
                    genderDialog = true
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("性别")
                Text(state.gender.genderLabel() + "  ›")
            }
        }
        state.avatarError?.let { error ->
            ProfileError(error, if (state.canRetryAvatar) onRetryAvatar else null)
            TextButton(onClick = { onAvatar(state.profile?.avatarUrl) }) { Text("取消头像更改") }
        }
        state.formError?.let { ProfileError(it) }
        Button(
            onClick = onSave,
            enabled = !state.saving && !state.loading && !reading && state.avatarError == null,
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
        ProfileNavigationRow("登录安全", null, R.drawable.home_profile, onPassword)
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
    var step by rememberSaveable { mutableIntStateOf(0) }
    var discard by remember { mutableStateOf(false) }
    val original = remember {
        listOf(
            state.goalType,
            state.targetWeight,
            state.currentWeight,
            state.targetDate,
            state.editingWeight,
        )
    }
    val changed =
        original !=
            listOf(
                state.goalType,
                state.targetWeight,
                state.currentWeight,
                state.targetDate,
                state.editingWeight,
            )
    val back = {
        if (!state.saving) {
            if (step == 1) step = 0 else if (changed) discard = true else onBack()
        }
    }
    androidx.activity.compose.BackHandler { back() }
    if (discard)
        FitDialog(
            onDismissRequest = { discard = false },
            title = { Text("放弃目标修改？") },
            text = { Text("未保存的目标将不会更新。") },
            confirmButton = {
                FitDialogButton(destructive = true, onClick = onBack) { Text("放弃修改") }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { discard = false }) { Text("继续编辑") }
            },
        )
    ProfilePage("我的目标", back, !state.saving) {
        LinearProgressIndicator(
            progress = { if (step == 0) .5f else 1f },
            modifier = Modifier.fillMaxWidth(),
        )
        if (step == 0) {
            Text("找到适合自己的节奏", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("你现在更关注什么？", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            GoalType.entries.forEach { type ->
                ProfileCard(onClick = if (state.saving) null else ({ onType(type) })) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(type.goalLabel(), fontWeight = FontWeight.Bold)
                        RadioButton(
                            state.goalType == type,
                            { onType(type) },
                            enabled = !state.saving,
                        )
                    }
                }
            }
            Text(
                when (state.goalType) {
                    GoalType.LOSE_FAT -> "让体重和围度稳步下降"
                    GoalType.GAIN_MUSCLE -> "关注体重、训练与身体变化"
                    GoalType.MAINTAIN -> "维持当前状态和记录习惯"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("下一步")
            }
        } else {
            Text("设置目标体重", style = MaterialTheme.typography.headlineSmall)
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
                    Text(
                        "当前体重",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
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
            state.bodyError?.let { FitInlineMessage(it) }
            ProfileCard {
                Text("目标体重", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FitTextField(
                    state.targetWeight,
                    onTarget,
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    textStyle =
                        LocalTextStyle.current.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        ),
                    label = { Text("目标体重（kg）") },
                    singleLine = true,
                    enabled = !state.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    "点击数字输入，最多两位小数",
                    Modifier.padding(top = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "目标日期 · 选填",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    }
    if (calendar)
        FitDatePickerDialog(
            date = state.targetDate?.let(LocalDate::parse) ?: LocalDate.now(),
            onDismiss = { calendar = false },
            onSave = {
                onDate(it.toString())
                calendar = false
            },
            title = "选择目标日期",
            minDate = LocalDate.now(),
        )
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
    onSecurity: () -> Unit = {},
) {
    var logout by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(false) }
    ProfilePage("设置", onBack, !busy) {
        Text(
            "账号与使用偏好",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProfileMenuGroup("账号设置") {
            ProfileMenuRow("编辑资料", R.drawable.home_profile, enabled = !busy, onClick = onAccount)
            ProfileMenuRow(
                "登录安全",
                R.drawable.profile_archive,
                enabled = !busy,
                onClick = onSecurity,
            )
        }
        ProfileMenuGroup("通用") {
            ProfileMenuRow("显示模式", R.drawable.home_photo, mode.modeLabel(), !busy) {
                appearance = true
            }
        }
        ProfileMenuGroup("关于循形Fitness") {
            ProfileMenuRow("用户协议", R.drawable.profile_archive, enabled = !busy) { onPolicy(false) }
            ProfileMenuRow("隐私政策", R.drawable.profile_archive, enabled = !busy) { onPolicy(true) }
            ProfileMenuRow("版本与关于", R.drawable.home_home, enabled = !busy, onClick = onAbout)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { logout = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = CircleShape,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Text(if (busy) "退出中…" else "退出登录", fontWeight = FontWeight.Bold)
        }
        Text(
            "记录日常，看见改变",
            Modifier.align(Alignment.CenterHorizontally),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (appearance) ProfileAppearanceSheet(mode, onMode) { appearance = false }
    if (logout)
        FitDialog(
            onDismissRequest = { if (!busy) logout = false },
            title = { Text("退出登录？") },
            text = { Text("退出后将清除本机登录状态，账号中的记录会保留。若已开启指纹登录，本机仍可使用；可在登录安全中关闭。") },
            confirmButton = {
                FitDialogButton(
                    destructive = true,
                    onClick = {
                        logout = false
                        onLogout()
                    },
                    enabled = !busy,
                ) {
                    Text("退出登录")
                }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { logout = false }, enabled = !busy) {
                    Text("取消")
                }
            },
        )
}

@Composable
internal fun ProfileSecurityScreen(
    email: String,
    onBack: () -> Unit,
    onPassword: () -> Unit,
    onBiometric: (() -> Unit)? = null,
) {
    ProfilePage("登录安全", onBack) {
        ProfileMenuGroup("当前账号") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("登录邮箱", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(email, fontSize = 15.sp)
            }
        }
        onBiometric?.let { open ->
            ProfileMenuGroup("快捷登录") {
                ProfileMenuRow("指纹 / 生物识别登录", R.drawable.profile_archive, onClick = open)
            }
        }
        ProfileMenuGroup("密码管理") {
            ProfileMenuRow("设置或修改密码", R.drawable.profile_archive, onClick = onPassword)
        }
        Text(
            "通过邮箱验证码验证身份后设置新密码。邮箱为当前登录账号，不可在此修改。",
            fontSize = 13.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ProfileAboutScreen(versionName: String, onBack: () -> Unit, onPolicy: () -> Unit) {
    ProfilePage(stringResource(R.string.about_title), onBack) {
        Column(
            Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource(R.drawable.design_sprout), null, Modifier.size(100.dp))
            Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
        FitTextField(
            value,
            onChange,
            Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled,
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text
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

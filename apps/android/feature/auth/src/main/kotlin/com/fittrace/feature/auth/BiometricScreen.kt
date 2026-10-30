package com.fittrace.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*

@Composable
internal fun BiometricScreen(
    state: BiometricUiState,
    settings: Boolean,
    email: String?,
    code: String = "",
    onCode: (String) -> Unit = {},
    countdown: Int = 0,
    sending: Boolean = false,
    sendNotice: String? = null,
    onBack: () -> Unit = {},
    onEnable: () -> Unit = {},
    onLogin: () -> Unit = {},
    onEmail: () -> Unit = {},
    onSend: () -> Unit = {},
    onDisable: () -> Unit = {},
    onRemove: () -> Unit = {},
    onRetry: () -> Unit = {},
    onEnroll: () -> Unit = {},
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = state.busy || sending
    if (settings) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
                BiometricToolbar(if (state.needsEmail) "验证身份" else "指纹登录", onBack)
                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 24.dp)
                ) {
                    if (state.needsEmail) {
                        BiometricEmailVerification(
                            state,
                            email.orEmpty(),
                            code,
                            onCode,
                            countdown,
                            busy,
                            sendNotice,
                            onSend,
                            onEnable,
                            onBack,
                        )
                    } else {
                        BiometricSettings(
                            state,
                            email.orEmpty(),
                            onToggle = { if (it) onEnable() else confirmation = "disable" },
                            onDisable = { confirmation = "disable" },
                            onRemove = { confirmation = "remove" },
                            onRetry = onRetry,
                            onEnroll = onEnroll,
                        )
                    }
                }
            }
        }
    } else {
        BiometricLogin(state, onLogin, onEmail, onRetry, onRemove = { confirmation = "remove" })
    }
    confirmation?.let { action ->
        FitDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (action == "disable") "关闭指纹登录？" else "移除此设备上的账号？") },
            text = {
                Text(
                    if (action == "disable") "关闭后，这台设备将无法使用指纹登录。当前账号会保持登录，之后仍可用邮箱登录。"
                    else "移除后，这台设备将不再保留此账号的指纹登录方式。账号和记录会保留；其他设备上的登录方式不受影响。"
                )
            },
            confirmButton = {
                FitDialogButton(
                    enabled = !busy,
                    onClick = {
                        confirmation = null
                        if (action == "disable") onDisable() else onRemove()
                    },
                ) {
                    Text(if (action == "disable") "确认关闭" else "确认移除")
                }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { confirmation = null }) {
                    Text(if (action == "disable") "暂不关闭" else "取消")
                }
            },
        )
    }
}

@Composable
private fun BiometricToolbar(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(painterResource(R.drawable.biometric_back), "返回登录安全", Modifier.size(20.dp))
        }
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp).semantics { heading() },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BiometricSettings(
    state: BiometricUiState,
    email: String,
    onToggle: (Boolean) -> Unit,
    onDisable: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onEnroll: () -> Unit,
) {
    val device = state.device
    val binding = device?.binding
    val pending = binding?.pending == true
    val known = state.statusConfirmed && device != null && !pending
    val enabled = known && device?.enabled == true
    val mismatch = (state.error as? AppError.Business)?.code == "DEVICE_ACCOUNT_MISMATCH"
    val stale = known && binding != null && !enabled
    val title =
        when {
            state.busy -> "正在检查登录状态"
            pending -> "正在确认开启结果"
            !known -> "暂时无法确认状态"
            enabled -> "指纹登录已开启"
            stale -> "需要重新开启指纹登录"
            device.capability == BiometricCapability.NOT_ENROLLED -> "先录入指纹，再开启"
            device.capability == BiometricCapability.UNSUPPORTED -> "当前设备暂不支持"
            device.capability == BiometricCapability.TEMPORARILY_UNAVAILABLE -> "生物识别暂不可用"
            else -> "用指纹，轻松登录"
        }
    FitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Fingerprint(54.dp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                title,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (enabled) "下次登录，验证指纹即可" else "退出账号后，也能快捷回来",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    if (enabled) "已开启 · 本机有效" else "仅在本机生效",
                    Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    SectionLabel("本机登录方式")
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("指纹登录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "验证后登录当前账号",
                        Modifier.padding(top = 6.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!known || state.busy)
                    Box(
                        Modifier.size(52.dp, 48.dp).semantics { contentDescription = "正在确认指纹登录状态" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.busy)
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                else
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        enabled =
                            !stale &&
                                (enabled || device?.capability == BiometricCapability.AVAILABLE),
                        modifier = Modifier.semantics { contentDescription = "指纹登录开关" },
                        colors =
                            SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = Color.White,
                                uncheckedBorderColor = Color.Transparent,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                uncheckedThumbColor = Color.White,
                            ),
                    )
            }
            Text(
                "${maskedEmail(email)} · 当前账号",
                Modifier.padding(top = 10.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val capabilityNotice =
        when (device?.capability) {
            BiometricCapability.NOT_ENROLLED -> "尚未录入可用的生物特征，请先到系统设置录入。"
            BiometricCapability.UNSUPPORTED -> "此设备不支持所需的生物识别。你仍可使用邮箱登录。"
            BiometricCapability.TEMPORARILY_UNAVAILABLE -> "生物识别暂不可用，请稍后重新检查。"
            else -> null
        }
    val message =
        when {
            pending -> "开启结果尚未确认，请联网检查。确认前不会重复开启。"
            mismatch -> "此设备保留了其他账号的登录方式，请先移除，再为当前账号开启。"
            stale -> "本机指纹登录已失效，请关闭后重新开启。"
            else -> state.notice ?: capabilityNotice
        }
    message?.let {
        Spacer(Modifier.height(16.dp))
        FitNotice(
            it,
            error =
                state.error != null &&
                    !pending &&
                    (state.error as? AppError.Business)?.code != "BIOMETRIC_CANCELLED",
            onRetry =
                if (
                    !state.busy &&
                        !stale &&
                        !mismatch &&
                        device?.capability != BiometricCapability.UNSUPPORTED &&
                        device?.capability != BiometricCapability.NOT_ENROLLED
                )
                    onRetry
                else null,
        )
    }
    if (device?.capability == BiometricCapability.NOT_ENROLLED)
        TextButton(onClick = onEnroll, enabled = !state.busy) { Text("前往系统设置") }
    if (stale) TextButton(onClick = onDisable, enabled = !state.busy) { Text("关闭本机指纹登录") }
    if (mismatch) TextButton(onClick = onRemove, enabled = !state.busy) { Text("移除此设备上的账号") }
    SectionLabel("使用说明")
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (copy in
                listOf(
                    "本机已录入的有效生物特征均可用于登录，请仅录入你信任的生物特征。",
                    "循形Fitness不会读取或保存你的指纹信息。",
                    "普通退出仍可使用；关闭后，当前账号保持登录。",
                )) Row {
                Box(
                    Modifier.padding(top = 8.dp)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                )
                Text(
                    copy,
                    Modifier.padding(start = 11.dp),
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Text(
        "开启与关闭仅影响当前设备",
        Modifier.fillMaxWidth().padding(top = 40.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BiometricEmailVerification(
    state: BiometricUiState,
    email: String,
    code: String,
    onCode: (String) -> Unit,
    countdown: Int,
    busy: Boolean,
    sendNotice: String?,
    onSend: () -> Unit,
    onEnable: () -> Unit,
    onBack: () -> Unit,
) {
    FitCard {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                "@",
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text("确认是你，再开启", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "为保护账号，需先验证当前邮箱。验证成功后，继续系统指纹验证。",
            fontSize = 14.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(maskedEmail(email), Modifier.padding(top = 12.dp), fontSize = 15.sp)
    }
    Spacer(Modifier.height(28.dp))
    val errorCode = (state.error as? AppError.Business)?.code
    val codeError = errorCode in setOf("EMAIL_CODE_INVALID", "EMAIL_CODE_EXPIRED")
    FitTextField(
        value = code,
        onValueChange = { onCode(it.filter(Char::isDigit).take(6)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱验证码") },
        placeholder = { Text("输入 6 位验证码") },
        enabled = !busy,
        isError = codeError,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!busy && code.length == 6) onEnable() }),
        trailingIcon = {
            TextButton(onClick = onSend, enabled = !busy && countdown == 0) {
                Text(if (countdown > 0) "${countdown}秒后重发" else "获取验证码", fontSize = 12.sp)
            }
        },
    )
    if (codeError)
        state.notice?.let {
            Spacer(Modifier.height(8.dp))
            FitInlineMessage(it)
        }
    else if (state.error != null && errorCode != "EMAIL_VERIFICATION_REQUIRED")
        state.notice?.let {
            Spacer(Modifier.height(8.dp))
            FitNotice(it, error = errorCode != "BIOMETRIC_CANCELLED")
        }
    sendNotice?.let {
        Spacer(Modifier.height(8.dp))
        FitNotice(it)
    }
    Text(
        "验证码仅用于本次身份验证",
        Modifier.padding(top = 12.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    BiometricPrimaryButton(
        "验证并继续",
        onEnable,
        enabled = !busy && code.length == 6,
        busy = state.busy,
    )
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("暂不开启")
    }
    Text(
        "身份验证通过后才会开启指纹登录",
        Modifier.fillMaxWidth().padding(top = 48.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BiometricLogin(
    state: BiometricUiState,
    onLogin: () -> Unit,
    onEmail: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val device = state.device
    val binding = device?.binding
    val errorCode = (state.error as? AppError.Business)?.code
    val invalid =
        device?.invalidated == true ||
            errorCode in
                setOf(
                    "DEVICE_CREDENTIAL_INVALID",
                    "BIOMETRIC_INVALIDATED",
                    "BIOMETRIC_KEY_UNAVAILABLE",
                )
    val pending = binding?.pending == true
    val locked = errorCode == "BIOMETRIC_LOCKED"
    val available = device?.capability == BiometricCapability.AVAILABLE
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (dark) MaterialTheme.colorScheme.primaryContainer
                            else Color(0xFFD3F8C4),
                            MaterialTheme.colorScheme.background,
                        ),
                        endY = 1000f,
                    )
                )
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            AuthBrandHeader()
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 500.dp)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        "欢迎回来",
                        Modifier.semantics { heading() },
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "验证指纹，继续你的记录",
                        Modifier.padding(top = 6.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        Modifier.align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "循",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Text(
                        binding?.email?.let(::maskedEmail) ?: "正在读取本机账号…",
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
                            state.busy && !state.statusConfirmed -> "正在检查登录状态…"
                            invalid -> "需要使用邮箱重新登录"
                            pending -> "开启结果待确认"
                            !state.statusConfirmed -> "登录状态尚未确认"
                            else -> "本机已开启指纹登录"
                        },
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    val message =
                        when {
                            invalid -> "本机指纹登录已失效，请使用邮箱登录。"
                            pending -> "开启结果尚未确认，请先使用邮箱登录，再到设置中检查。"
                            locked -> "验证次数过多，请稍后重试或使用邮箱。"
                            device?.capability == BiometricCapability.NOT_ENROLLED ->
                                "本机没有可用的生物特征，请使用邮箱登录。"
                            !available && device != null -> "生物识别暂不可用，你仍可使用邮箱登录。"
                            else -> state.notice
                        }
                    if (message != null)
                        FitNotice(
                            message,
                            error =
                                invalid ||
                                    locked ||
                                    (state.error != null && errorCode != "BIOMETRIC_CANCELLED"),
                            onRetry =
                                if (
                                    !state.busy &&
                                        !invalid &&
                                        !pending &&
                                        errorCode != "BIOMETRIC_CANCELLED"
                                )
                                    onRetry
                                else null,
                        )
                    else
                        Box(
                            Modifier.fillMaxWidth().height(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Fingerprint(40.dp)
                        }
                    Spacer(Modifier.height(20.dp))
                    BiometricPrimaryButton(
                        if (invalid) "使用邮箱登录" else if (locked) "指纹暂不可用" else "指纹登录",
                        if (invalid) onEmail else onLogin,
                        enabled =
                            invalid ||
                                (available &&
                                    binding != null &&
                                    !pending &&
                                    !locked &&
                                    state.statusConfirmed &&
                                    !state.busy),
                        busy = state.busy,
                    )
                    TextButton(
                        onClick = if (invalid) onRemove else onEmail,
                        enabled = !invalid || !state.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(if (invalid) "移除此设备上的账号" else "使用邮箱登录", fontSize = 14.sp)
                    }
                    Text(
                        if (invalid) "邮箱登录后，可在设置中重新开启指纹登录。" else "指纹信息由系统验证，循形Fitness不会读取或保存",
                        Modifier.fillMaxWidth().padding(top = 32.dp),
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                "每一点记录，都算数。",
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BiometricPrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors =
            ButtonDefaults.buttonColors(
                contentColor = Color(0xFF113821),
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (busy) "处理中…" else label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        Modifier.padding(start = 8.dp, top = 24.dp, bottom = 12.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Fingerprint(size: androidx.compose.ui.unit.Dp) {
    Icon(
        painterResource(R.drawable.biometric_fingerprint),
        null,
        Modifier.size(size),
        tint = MaterialTheme.colorScheme.secondary,
    )
}

internal fun maskedEmail(email: String): String =
    email.substringBefore('@').take(1) + "***@" + email.substringAfter('@', "")

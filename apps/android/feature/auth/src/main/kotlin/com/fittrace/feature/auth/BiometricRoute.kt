package com.fittrace.feature.auth

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fittrace.core.designsystem.*
import com.fittrace.core.domain.*
import com.fittrace.core.model.*

@Composable
fun BiometricRoute(
    repository: BiometricRepository,
    auth: AuthRepository,
    signer: BiometricSigner,
    email: String? = null,
    onBack: () -> Unit = {},
    emailPage: @Composable ((() -> Unit)?) -> Unit = {},
) {
    val settings = email != null
    val model: BiometricViewModel =
        viewModel(
            key = "biometric-$email",
            factory =
                remember(repository, email) {
                    viewModelFactory { initializer { BiometricViewModel(repository, settings) } }
                },
        )
    val state by model.state.collectAsStateWithLifecycle()
    var useEmail by rememberSaveable { mutableStateOf(false) }
    val senderOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.cancel() }
    val sender: AuthViewModel =
        viewModel(
            viewModelStoreOwner = senderOwner,
            key = "biometric-email-$email",
            factory =
                remember(auth) {
                    viewModelFactory {
                        initializer {
                            AuthViewModel(auth).apply {
                                setEmail(email ?: "")
                                acceptTerms(true)
                            }
                        }
                    }
                },
        )
    val sending by sender.state.collectAsStateWithLifecycle()
    DisposableEffect(model, signer) {
        onDispose {
            model.cancel()
            senderOwner.viewModelStore.clear()
        }
    }
    if (!settings && (useEmail || (state.device != null && state.device?.binding == null))) {
        emailPage(
            if (state.device?.binding != null)
                ({
                    useEmail = false
                    model.refresh()
                })
            else null
        )
        return
    }
    BackHandler(enabled = settings) {
        model.cancel()
        onBack()
    }
    val context = LocalContext.current
    BiometricScreen(
        state,
        settings,
        email,
        sending.code,
        sender::setCode,
        sending.countdown,
        sending.busy,
        sending.notice,
        onBack = {
            model.cancel()
            onBack()
        },
        onEnable = { model.enable(sending.code, signer) },
        onLogin = { model.login(signer) },
        onEmail = {
            model.cancel()
            useEmail = true
        },
        onSend = sender::requestCode,
        onDisable = model::disable,
        onRemove = model::remove,
        onRetry = model::refresh,
        onEnroll = {
            val intent =
                if (Build.VERSION.SDK_INT >= 30)
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                        .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, 15)
                else Intent(Settings.ACTION_SECURITY_SETTINGS)
            runCatching { context.startActivity(intent) }
        },
    )
    sending.challenge?.let { challenge ->
        key(challenge.id) {
            CaptchaDialog(
                challenge,
                { sender.captchaCompleted(challenge.id, it) },
                { sender.captchaFailed(challenge.id, it) },
            )
        }
    }
}

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
    var confirm by remember { mutableStateOf<String?>(null) }
    val device = state.device
    val binding = device?.binding
    val available = device?.capability == BiometricCapability.AVAILABLE
    val busy = state.busy || sending
    Scaffold { padding ->
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
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (settings) TextButton(onClick = onBack, enabled = !busy) { Text("返回登录安全") }
            Text(
                if (settings) "指纹 / 生物识别登录" else "欢迎回来",
                style = MaterialTheme.typography.headlineMedium,
            )
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (settings) email.orEmpty()
                        else binding?.email?.let(::maskedEmail).orEmpty()
                    )
                    val label =
                        when {
                            device == null -> "正在检查设备状态…"
                            device.invalidated -> "需使用邮箱登录后重新开启"
                            binding?.pending == true -> "待确认开启结果，请联网检查"
                            settings && device.enabled -> "已开启"
                            settings && binding != null -> "绑定已失效，请关闭后重新开启"
                            device.capability == BiometricCapability.NOT_ENROLLED -> "尚未录入强生物识别"
                            device.capability == BiometricCapability.UNSUPPORTED -> "此设备不支持强生物识别"
                            !available -> "生物识别暂不可用"
                            else -> if (settings) "尚未开启" else "验证后继续记录"
                        }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "开启后，本机已录入的有效强生物识别均可能用于登录，请确保只录入你信任的生物特征。循形不会读取或保存指纹。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.notice?.let { FitMessage(it) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (settings && state.needsEmail && binding == null) {
                        FitTextField(
                            value = code,
                            onValueChange = onCode,
                            label = { Text("邮箱验证码") },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = onSend, enabled = !busy && countdown == 0) {
                            Text(if (countdown > 0) "${countdown}秒后重发" else "发送验证码")
                        }
                        sendNotice?.let { FitMessage(it) }
                    }
                    if (settings && binding == null)
                        Button(
                            onClick = onEnable,
                            enabled = available && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.needsEmail) "验证邮箱并开启" else "开启指纹登录")
                        }
                    if (!settings) {
                        Button(
                            onClick = onLogin,
                            enabled =
                                available &&
                                    binding != null &&
                                    !binding.pending &&
                                    device?.invalidated != true &&
                                    !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("指纹 / 生物识别登录")
                        }
                        TextButton(onClick = onEmail, enabled = !busy) { Text("使用邮箱登录") }
                    }
                    if (device?.capability == BiometricCapability.NOT_ENROLLED)
                        TextButton(onClick = onEnroll, enabled = !busy) { Text("前往系统设置") }
                    TextButton(onClick = onRetry, enabled = !busy) { Text("重新检查状态") }
                    if (settings && binding != null)
                        OutlinedButton(onClick = { confirm = "disable" }, enabled = !busy) {
                            Text("关闭本机指纹登录")
                        }
                    if (binding != null)
                        TextButton(onClick = { confirm = "remove" }, enabled = !busy) {
                            Text("移除本机凭据")
                        }
                }
            }
            Text("普通退出会保留已开启的本机登录凭据；邮箱始终可用于恢复登录。", style = MaterialTheme.typography.bodySmall)
        }
    }
    confirm?.let { action ->
        FitDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "disable") "关闭本机指纹登录？" else "移除本机凭据？") },
            text = {
                Text(
                    if (action == "disable") "联网撤销后，本机将无法再通过指纹重新登录；当前会话保持登录。"
                    else "删除设备密钥后本机无法再签名登录。服务端设备记录需登录原账号后管理。"
                )
            },
            confirmButton = {
                FitDialogButton(
                    onClick = {
                        confirm = null
                        if (action == "disable") onDisable() else onRemove()
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { confirm = null }) { Text("取消") }
            },
        )
    }
}

internal fun maskedEmail(email: String): String =
    email.substringBefore('@').take(1) + "***@" + email.substringAfter('@', "")

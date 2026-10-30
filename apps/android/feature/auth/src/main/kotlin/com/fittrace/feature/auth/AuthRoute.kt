package com.fittrace.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fittrace.core.designsystem.*
import com.fittrace.core.designsystem.FitTraceTheme
import com.fittrace.core.domain.AuthRepository

@Composable
fun AuthRoute(
    repository: AuthRepository,
    owner: ViewModelStoreOwner,
    initialNotice: String? = null,
    initialResetEmail: String? = null,
    onResetClosed: (() -> Unit)? = null,
    onBiometric: (() -> Unit)? = null,
) {
    // owner 由应用级 ViewModel 持有：旋转时保留状态，登录完成后清除敏感输入。
    val factory =
        remember(repository) { viewModelFactory { initializer { AuthViewModel(repository) } } }
    val model: AuthViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialResetEmail) {
        if (initialResetEmail != null) {
            model.setEmail(initialResetEmail)
            model.switchMode(AuthMode.RESET_EMAIL)
        }
    }
    LaunchedEffect(state.notice) {
        if (initialResetEmail != null && state.notice == "密码已更新，请使用新密码登录") onResetClosed?.invoke()
    }
    val back: () -> Unit = {
        if (initialResetEmail != null && state.mode == AuthMode.RESET_EMAIL) {
            model.switchMode(AuthMode.PASSWORD)
            onResetClosed?.invoke()
        } else model.back()
    }
    BackHandler(
        enabled =
            state.mode in listOf(AuthMode.RESET_EMAIL, AuthMode.RESET_PASSWORD) &&
                state.challenge == null &&
                state.policy == null
    ) {
        back()
    }
    AuthScreen(
        state.copy(notice = state.notice ?: initialNotice),
        model::setEmail,
        model::setCode,
        model::setPassword,
        model::setConfirmPassword,
        model::setCaptcha,
        model::acceptTerms,
        model::togglePassword,
        model::switchMode,
        back,
        model::requestCode,
        model::submit,
        model::openPolicy,
        model::refreshLoginCaptcha,
        onBiometric = onBiometric,
    )
    state.policy?.let { PolicyDialog(it) { model.openPolicy(null) } }
    state.challenge?.let { challenge ->
        key(challenge.id) {
            CaptchaDialog(
                challenge,
                { model.captchaCompleted(challenge.id, it) },
                { model.captchaFailed(challenge.id, it) },
            )
        }
    }
}

@Composable
internal fun AuthScreen(
    state: AuthUiState,
    onEmail: (String) -> Unit = {},
    onCode: (String) -> Unit = {},
    onPassword: (String) -> Unit = {},
    onConfirmPassword: (String) -> Unit = {},
    onCaptcha: (String) -> Unit = {},
    onTerms: (Boolean) -> Unit = {},
    onTogglePassword: () -> Unit = {},
    onMode: (AuthMode) -> Unit = {},
    onBack: () -> Unit = {},
    onRequestCode: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onPolicy: (PolicyKind?) -> Unit = {},
    onRefreshCaptcha: () -> Unit = {},
    onBiometric: (() -> Unit)? = null,
) {
    val reset = state.mode in listOf(AuthMode.RESET_EMAIL, AuthMode.RESET_PASSWORD)
    val title =
        when (state.mode) {
            AuthMode.EMAIL -> if (onBiometric != null) "邮箱登录" else "开始你的记录"
            AuthMode.PASSWORD -> "欢迎回来"
            AuthMode.RESET_EMAIL -> "验证邮箱"
            AuthMode.RESET_PASSWORD -> "设置新密码"
        }
    val subtitle =
        when (state.mode) {
            AuthMode.EMAIL -> "邮箱验证码登录 · 新邮箱验证后自动注册"
            AuthMode.PASSWORD -> "欢迎回来，继续你的记录"
            AuthMode.RESET_EMAIL -> "验证通过后，可以设置新的密码"
            AuthMode.RESET_PASSWORD -> "邮箱已验证，设置一个新的登录密码"
        }
    val label =
        when (state.mode) {
            AuthMode.EMAIL -> "验证并登录"
            AuthMode.PASSWORD -> "登录"
            AuthMode.RESET_EMAIL -> "验证并继续"
            AuthMode.RESET_PASSWORD -> "保存新密码"
        }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val headerColor = if (dark) MaterialTheme.colorScheme.primaryContainer else Color(0xFFD3F8C4)
    val baseColor = if (dark) MaterialTheme.colorScheme.background else Color(0xFFF0F6EE)
    Scaffold(containerColor = baseColor) { insets ->
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(headerColor, baseColor), endY = 1000f))
                .padding(insets)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            if (reset) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onBack,
                        enabled = !state.busy,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("返回")
                    }
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    subtitle,
                    Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(36.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        if (state.mode == AuthMode.RESET_EMAIL) "1 验证邮箱 → 2 设置新密码"
                        else "已验证 ${state.email.trim()}",
                        Modifier.fillMaxWidth().padding(16.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(28.dp))
            } else {
                AuthBrandHeader(compact = keyboardVisible)
            }
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 20.dp)
                    .animateContentSize(animationSpec = tween(220))
            ) {
                if (!reset) {
                    Text(
                        title,
                        fontSize = 23.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        subtitle,
                        Modifier.padding(top = 6.dp, bottom = 12.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.mode != AuthMode.RESET_PASSWORD) {
                    AuthInput(
                        "邮箱",
                        state.email,
                        onEmail,
                        "输入你的邮箱",
                        state.errors[AuthField.EMAIL],
                        !state.busy,
                        KeyboardType.Email,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (state.mode in listOf(AuthMode.EMAIL, AuthMode.RESET_EMAIL)) {
                    AuthInput(
                        "邮箱验证码",
                        state.code,
                        onCode,
                        "6 位验证码",
                        state.errors[AuthField.CODE],
                        !state.busy,
                        KeyboardType.Number,
                        onDone = onSubmit,
                        trailing = {
                            TextButton(
                                onClick = onRequestCode,
                                enabled = !state.busy && state.countdown == 0,
                            ) {
                                Text(
                                    if (state.sendingCode) "验证中…"
                                    else if (state.countdown > 0) "${state.countdown}s 后重发"
                                    else "获取验证码",
                                    fontSize = 12.sp,
                                )
                            }
                        },
                    )
                    if (state.mode == AuthMode.RESET_EMAIL || state.countdown > 0)
                        Text(
                            if (state.mode == AuthMode.RESET_EMAIL) "验证码 10 分钟内有效"
                            else "验证码已发送至 ${state.email.trim()}",
                            Modifier.padding(top = 8.dp),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else Spacer(Modifier.height(10.dp))
                } else {
                    AuthInput(
                        if (reset) "新密码" else "密码",
                        state.password,
                        onPassword,
                        if (reset) "设置 8–72 位密码" else "输入密码",
                        state.errors[AuthField.PASSWORD],
                        !state.busy,
                        KeyboardType.Password,
                        visual =
                            if (state.passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        onDone = onSubmit,
                        trailing = {
                            TextButton(onClick = onTogglePassword) {
                                Text(if (state.passwordVisible) "隐藏" else "显示", fontSize = 12.sp)
                            }
                        },
                    )
                    if (state.mode == AuthMode.RESET_PASSWORD) {
                        Spacer(Modifier.height(12.dp))
                        AuthInput(
                            "确认新密码",
                            state.confirmPassword,
                            onConfirmPassword,
                            "再次输入新密码",
                            state.errors[AuthField.CONFIRM_PASSWORD],
                            !state.busy,
                            KeyboardType.Password,
                            visual =
                                if (state.passwordVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            onDone = onSubmit,
                        )
                        Text(
                            "建议组合字母、数字与符号",
                            Modifier.padding(top = 8.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.mode == AuthMode.PASSWORD && state.captchaRequired) {
                    Spacer(Modifier.height(12.dp))
                    AuthInput(
                        "图形验证码",
                        state.captchaInput,
                        onCaptcha,
                        "图片中的 4 位字符",
                        state.errors[AuthField.CAPTCHA],
                        !state.busy,
                        KeyboardType.Ascii,
                        onDone = onSubmit,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        state.loginCaptcha?.let {
                            PasswordCaptchaImage(it.image, Modifier.weight(1f).height(60.dp))
                        }
                        TextButton(
                            onClick = onRefreshCaptcha,
                            enabled = !state.busy && !state.captchaLoading,
                        ) {
                            Text(if (state.captchaLoading) "加载中…" else "换一张")
                        }
                    }
                }
                state.notice?.let { FitNotice(it, Modifier.padding(top = 12.dp)) }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onSubmit,
                    enabled =
                        !state.busy &&
                            when (state.mode) {
                                AuthMode.EMAIL ->
                                    state.email.isNotBlank() &&
                                        state.code.length == 6 &&
                                        state.acceptedTerms
                                AuthMode.PASSWORD ->
                                    state.email.isNotBlank() &&
                                        state.password.isNotEmpty() &&
                                        state.acceptedTerms
                                AuthMode.RESET_EMAIL ->
                                    state.email.isNotBlank() && state.code.length == 6
                                AuthMode.RESET_PASSWORD ->
                                    state.password.isNotEmpty() &&
                                        state.confirmPassword.isNotEmpty()
                            },
                    shape = RoundedCornerShape(25.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            contentColor =
                                if (onBiometric != null) Color(0xFF113821)
                                else MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    if (state.busy && !state.sendingCode) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.busy && !state.sendingCode) "处理中…" else label)
                }
                if (!reset)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick =
                                onBiometric
                                    ?: {
                                        onMode(
                                            if (state.mode == AuthMode.EMAIL) AuthMode.PASSWORD
                                            else AuthMode.EMAIL
                                        )
                                    },
                            enabled = !state.busy,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                if (onBiometric != null) "使用指纹登录"
                                else if (state.mode == AuthMode.EMAIL) "密码登录" else "邮箱验证码登录",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (onBiometric != null)
                            TextButton(
                                onClick = {
                                    onMode(
                                        if (state.mode == AuthMode.EMAIL) AuthMode.PASSWORD
                                        else AuthMode.EMAIL
                                    )
                                },
                                enabled = !state.busy,
                            ) {
                                Text(
                                    if (state.mode == AuthMode.EMAIL) "密码登录" else "邮箱验证码登录",
                                    fontSize = 12.sp,
                                )
                            }
                        if (state.mode == AuthMode.PASSWORD && onBiometric == null)
                            TextButton(
                                onClick = { onMode(AuthMode.RESET_EMAIL) },
                                enabled = !state.busy,
                            ) {
                                Text("设置或找回密码", fontSize = 12.sp)
                            }
                    }
                if (!reset && state.mode == AuthMode.PASSWORD && onBiometric != null)
                    TextButton(
                        onClick = { onMode(AuthMode.RESET_EMAIL) },
                        enabled = !state.busy,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("设置或找回密码", fontSize = 12.sp)
                    }
                if (!reset) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.acceptedTerms,
                            onCheckedChange = onTerms,
                            enabled = !state.busy,
                            modifier =
                                Modifier.semantics { contentDescription = "我已阅读并同意用户协议和隐私政策" },
                        )
                        Text(
                            buildAnnotatedString {
                                append("我已阅读并同意")
                                val linkStyle =
                                    TextLinkStyles(
                                        SpanStyle(color = MaterialTheme.colorScheme.primary)
                                    )
                                withLink(
                                    LinkAnnotation.Clickable("agreement", linkStyle) {
                                        onPolicy(PolicyKind.AGREEMENT)
                                    }
                                ) {
                                    append("用户协议")
                                }
                                append("与")
                                withLink(
                                    LinkAnnotation.Clickable("privacy", linkStyle) {
                                        onPolicy(PolicyKind.PRIVACY)
                                    }
                                ) {
                                    append("隐私政策")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.errors[AuthField.AGREEMENT]?.let { FitInlineMessage(it) }
                }
                Text(
                    "每一点记录，都算数。",
                    Modifier.align(Alignment.CenterHorizontally)
                        .padding(top = 24.dp, bottom = 12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.mode == AuthMode.RESET_PASSWORD)
                    Text(
                        "重置后需重新登录",
                        Modifier.padding(top = 16.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AuthInput(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    error: String?,
    enabled: Boolean,
    keyboard: KeyboardType,
    visual: VisualTransformation = VisualTransformation.None,
    onDone: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FitTextField(
            value,
            onValue,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            visualTransformation = visual,
            trailingIcon = trailing,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboard,
                    imeAction = if (onDone == null) ImeAction.Next else ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            supportingText =
                if (error == null) null
                else {
                    { FitInlineMessage(error) }
                },
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, locale = "zh")
@Composable
private fun EmailLoginPreview() {
    FitTraceTheme { AuthScreen(AuthUiState()) }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 720, locale = "zh")
@Composable
private fun PasswordLoginPreview() {
    FitTraceTheme { AuthScreen(AuthUiState(mode = AuthMode.PASSWORD)) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, locale = "zh")
@Composable
private fun ResetPasswordPreview() {
    FitTraceTheme {
        AuthScreen(AuthUiState(mode = AuthMode.RESET_PASSWORD, email = "user@example.com"))
    }
}

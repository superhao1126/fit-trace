package com.fittrace.feature.auth

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
        if (state.needsEmail) {
            model.dismissEmail()
            sender.setCode("")
        } else {
            model.cancel()
            onBack()
        }
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
            if (state.needsEmail) {
                model.dismissEmail()
                sender.setCode("")
            } else {
                model.cancel()
                onBack()
            }
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

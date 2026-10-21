package com.fittrace.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fittrace.core.designsystem.FitTraceTheme
import com.fittrace.core.model.SessionStatus
import com.fittrace.feature.auth.AuthRoute
import com.fittrace.feature.auth.PolicyDialog
import com.fittrace.feature.auth.PolicyKind
import com.fittrace.feature.auth.message
import com.fittrace.feature.main.MainRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FitTraceApplication).container
        val auth = container.authRepository
        val preferences = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            var themeMode by remember {
                mutableStateOf(preferences.getString("theme", "SYSTEM") ?: "SYSTEM")
            }
            var policy by remember { mutableStateOf<PolicyKind?>(null) }

            val factory = remember { viewModelFactory { initializer { AppViewModel(auth) } } }
            val model: AppViewModel = viewModel(factory = factory)
            val status by model.sessionStatus.collectAsStateWithLifecycle()
            val busy by model.busy.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_START) { model.restore() }
            FitTraceTheme(
                darkTheme =
                    when (themeMode) {
                        "LIGHT" -> false
                        "DARK" -> true
                        else -> isSystemInDarkTheme()
                    }
            ) {
                val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > .5f
                SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        .apply {
                            isAppearanceLightStatusBars = lightSystemBars
                            isAppearanceLightNavigationBars = lightSystemBars
                        }
                }
                when (val session = status) {
                    SessionStatus.Loading ->
                        Surface(Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    SessionStatus.SignedOut -> AuthRoute(auth, model.authFlowOwner)
                    is SessionStatus.RecoveryRequired ->
                        Scaffold { padding ->
                            Column(
                                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("暂时无法恢复登录", style = MaterialTheme.typography.titleLarge)
                                Text(session.error.message(), Modifier.padding(vertical = 16.dp))
                                Button(onClick = model::restore, enabled = !busy) { Text("重新连接") }
                                TextButton(onClick = model::logout, enabled = !busy) {
                                    Text("清除本机会话，重新登录")
                                }
                            }
                        }
                    is SessionStatus.SignedIn ->
                        MainRoute(
                            BuildConfig.VERSION_NAME,
                            session.user.email,
                            model::logout,
                            busy,
                            container.dashboardRepository,
                            session.user.id,
                            container.profileRepository,
                            themeMode,
                            { mode ->
                                themeMode = mode
                                preferences.edit { putString("theme", mode) }
                            },
                            { privacy ->
                                policy = if (privacy) PolicyKind.PRIVACY else PolicyKind.AGREEMENT
                            },
                            container.recordsRepository,
                            container.trainingRepository,
                            { back ->
                                AuthRoute(
                                    auth,
                                    model.authFlowOwner,
                                    initialResetEmail = session.user.email,
                                    onResetClosed = back,
                                )
                            },
                        )
                }
                if (status is SessionStatus.SignedIn)
                    policy?.let { PolicyDialog(it) { policy = null } }
                else LaunchedEffect(Unit) { policy = null }
            }
        }
    }
}

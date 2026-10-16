package com.fittrace.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fittrace.core.domain.DashboardRepository

@Composable
fun MainRoute(
    versionName: String,
    userEmail: String,
    onLogout: () -> Unit,
    busy: Boolean = false,
    dashboardRepository: DashboardRepository,
    userId: String,
) {
    key(userId) {
        val factory =
            remember(dashboardRepository) {
                viewModelFactory { initializer { DashboardViewModel(dashboardRepository) } }
            }
        val model: DashboardViewModel = viewModel(key = "dashboard-$userId", factory = factory)
        val state by model.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var notice by remember { mutableStateOf<String?>(null) }
        var about by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = tab != 0 || about) {
            about = false
            tab = 0
        }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { model.refresh() }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                HomeNavigation(tab) {
                    if (it == 1 || it == 2) notice = if (it == 1) "趋势" else "档案"
                    else {
                        tab = it
                        about = false
                    }
                }
            },
        ) { padding ->
            if (tab == 0)
                DashboardScreen(
                    state,
                    model::selectDate,
                    model::refresh,
                    { notice = it },
                    Modifier.padding(padding),
                )
            else
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (about) stringResource(R.string.about_title) else "我的",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(userEmail)
                    if (about) {
                        Text(stringResource(R.string.version, versionName))
                        Text(stringResource(R.string.brand_slogan))
                        TextButton(onClick = { about = false }) {
                            Text(stringResource(R.string.back))
                        }
                    } else {
                        TextButton(onClick = { about = true }) {
                            Text(stringResource(R.string.about_title))
                        }
                        TextButton(onClick = onLogout, enabled = !busy) {
                            Text(if (busy) "退出中…" else "退出登录")
                        }
                    }
                }
        }
        notice?.let { title ->
            AlertDialog(
                onDismissRequest = { notice = null },
                title = { Text(title) },
                text = { Text("此功能暂未开放。你可以先在首页按日期查看已有记录。") },
                confirmButton = { TextButton(onClick = { notice = null }) { Text("知道了") } },
            )
        }
    }
}

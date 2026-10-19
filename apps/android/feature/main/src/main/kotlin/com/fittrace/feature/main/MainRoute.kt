package com.fittrace.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fittrace.core.domain.DashboardRepository
import com.fittrace.core.domain.ProfileRepository

@Composable
fun MainRoute(
    versionName: String,
    userEmail: String,
    onLogout: () -> Unit,
    busy: Boolean = false,
    dashboardRepository: DashboardRepository,
    userId: String,
    profileRepository: ProfileRepository,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    onPolicy: (Boolean) -> Unit,
) {
    key(userId) {
        val factory =
            remember(dashboardRepository) {
                viewModelFactory { initializer { DashboardViewModel(dashboardRepository) } }
            }
        val model: DashboardViewModel = viewModel(key = "dashboard-$userId", factory = factory)
        val profileFactory =
            remember(profileRepository) {
                viewModelFactory { initializer { ProfileViewModel(profileRepository) } }
            }
        val profile: ProfileViewModel = viewModel(key = "profile-$userId", factory = profileFactory)
        val state by model.state.collectAsStateWithLifecycle()
        val profileState by profile.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var pages by rememberSaveable { mutableStateOf(listOf<String>()) }
        var pendingGoal by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<String?>(null) }
        val page = pages.lastOrNull()
        val back: () -> Unit = {
            if (!profileState.saving) {
                if (pages.isNotEmpty()) pages = pages.dropLast(1) else tab = 0
            }
        }
        val open: (String) -> Unit = { target ->
            if (profileState.profile != null) {
                if (target == "goal") profile.beginGoal()
                if (target == "edit") profile.beginProfile()
                pages = pages + target
            } else notice = "个人资料尚未加载，请返回重试。"
        }
        BackHandler(enabled = tab != 0 || page != null) { back() }
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            model.refresh()
            if (tab == 3) profile.load()
        }
        LaunchedEffect(tab) { if (tab == 3) profile.load() }
        LaunchedEffect(pendingGoal, profileState.loading) {
            if (pendingGoal && !profileState.loading) {
                pendingGoal = false
                open("goal")
            }
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (page == null)
                    HomeNavigation(tab) {
                        if (it == 1 || it == 2)
                            notice = if (it == 1) "趋势暂未开放。" else "完整档案暂未开放，可先在首页按日期查看已有记录。"
                        else tab = it
                    }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "info" ->
                        profileState.profile?.let { ProfileInfoScreen(it, back) { open("edit") } }
                    "edit" ->
                        ProfileEditScreen(
                            profileState,
                            back,
                            profile::nickname,
                            profile::gender,
                            { profile.saveProfile(back) },
                            { notice = "密码管理暂未开放，可在登录页使用“设置或找回密码”。" },
                        )
                    "goal" ->
                        GoalEditScreen(
                            profileState,
                            back,
                            profile::goalType,
                            profile::targetWeight,
                            profile::currentWeight,
                            profile::targetDate,
                            profile::editWeight,
                        ) {
                            profile.saveGoal {
                                back()
                                model.refresh()
                            }
                        }
                    "settings" ->
                        ProfileSettingsScreen(
                            back,
                            { open("edit") },
                            onPolicy,
                            { pages = pages + "about" },
                            themeMode,
                            onThemeMode,
                            busy,
                            onLogout,
                        )
                    "about" -> ProfileAboutScreen(versionName, back) { onPolicy(false) }
                    else ->
                        if (tab == 0)
                            DashboardScreen(
                                state,
                                model::selectDate,
                                model::refresh,
                                {
                                    if (it == "体重目标") {
                                        pendingGoal = true
                                        profile.load()
                                    } else notice = "$it 暂未开放。你可以先在首页按日期查看已有记录。"
                                },
                            )
                        else
                            ProfileScreen(
                                profileState,
                                profile::load,
                                { pages = pages + "settings" },
                                { open("info") },
                                { open("goal") },
                                { notice = "完整档案暂未开放，可先在首页按日期查看已有记录。" },
                            )
                }
            }
        }
        notice?.let { text ->
            AlertDialog(
                onDismissRequest = { notice = null },
                title = { Text("提示") },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { notice = null }) { Text("知道了") } },
            )
        }
    }
}

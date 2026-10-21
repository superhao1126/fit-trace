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
import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.model.RecordKind
import java.time.LocalDate
import java.time.YearMonth

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
    recordsRepository: RecordsRepository,
    trainingRepository: com.fittrace.core.domain.TrainingRepository,
    passwordPage: @Composable (() -> Unit) -> Unit,
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
        val recordsFactory =
            remember(recordsRepository) {
                viewModelFactory { initializer { RecordsViewModel(recordsRepository) } }
            }
        val records: RecordsViewModel = viewModel(key = "records-$userId", factory = recordsFactory)
        val recordsState by records.state.collectAsStateWithLifecycle()
        val trendsFactory =
            remember(recordsRepository) {
                viewModelFactory { initializer { TrendsViewModel(recordsRepository) } }
            }
        val trends: TrendsViewModel = viewModel(key = "trends-$userId", factory = trendsFactory)
        val trendsState by trends.state.collectAsStateWithLifecycle()
        var recordKind by rememberSaveable { mutableStateOf<String?>(null) }
        var archiveDate by rememberSaveable { mutableStateOf<String?>(null) }
        var archiveMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
        val selectedArchiveMonth =
            archiveDate?.let { YearMonth.from(LocalDate.parse(it)) }
                ?: YearMonth.parse(archiveMonth)
        var editorKind by rememberSaveable { mutableStateOf("BODY") }
        var editorId by rememberSaveable { mutableStateOf<String?>(null) }
        var editorDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        var editorKey by rememberSaveable { mutableIntStateOf(0) }
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
        val create: (RecordKind) -> Unit = { kind ->
            editorKind = kind.name
            editorId = null
            editorDate =
                when {
                    page == "records" || tab == 2 -> archiveDate ?: LocalDate.now().toString()
                    tab == 0 -> state.date.toString()
                    else -> LocalDate.now().toString()
                }
            editorKey++
            pages = pages + if (kind == RecordKind.WORKOUT) "training" else "record-editor"
        }
        val openRecord: (RecordKind, String) -> Unit = { kind, id ->
            editorKind = kind.name
            editorId = id
            editorDate = state.date.toString()
            editorKey++
            pages = pages + "record-editor"
        }
        LaunchedEffect(tab, page, recordKind, archiveDate, archiveMonth) {
            if (tab == 2 || page == "records")
                records.select(
                    recordKind?.let(RecordKind::valueOf),
                    archiveDate?.let(LocalDate::parse),
                    month = selectedArchiveMonth,
                )
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
            containerColor =
                if (tab == 3 && page == null) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.background,
            bottomBar = {
                if (page == null)
                    HomeNavigation(tab) {
                        tab = it
                        if (it == 2) {
                            recordKind = null
                            archiveDate = null
                        }
                    }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "training" ->
                        key(editorKey) {
                            val factory = remember {
                                viewModelFactory {
                                    initializer {
                                        TrainingViewModel(
                                            trainingRepository,
                                            recordsRepository,
                                            LocalDate.parse(editorDate),
                                        )
                                    }
                                }
                            }
                            val training: TrainingViewModel =
                                viewModel(key = "training-$userId-$editorKey", factory = factory)
                            val trainingState by training.state.collectAsStateWithLifecycle()
                            LaunchedEffect(trainingState.saved) {
                                if (trainingState.saved) {
                                    back()
                                    model.refresh()
                                    records.refresh()
                                    trends.load()
                                }
                            }
                            TrainingScreen(trainingState, training, back) {
                                pages = pages + "record-editor"
                            }
                        }
                    "record-editor" ->
                        key(editorKey) {
                            val editorFactory = remember {
                                viewModelFactory {
                                    initializer {
                                        RecordEditorViewModel(
                                            recordsRepository,
                                            RecordKind.valueOf(editorKind),
                                            LocalDate.parse(editorDate),
                                            editorId,
                                        )
                                    }
                                }
                            }
                            val editor: RecordEditorViewModel =
                                viewModel(
                                    key = "editor-$userId-$editorKey",
                                    factory = editorFactory,
                                )
                            val editorState by editor.state.collectAsStateWithLifecycle()
                            LaunchedEffect(editorState.saved) {
                                if (editorState.saved) {
                                    back()
                                    model.refresh()
                                    records.refresh()
                                    trends.load()
                                    profile.load()
                                }
                            }
                            RecordEditorScreen(editorState, editor, back)
                        }
                    "records" ->
                        RecordsScreen(
                            recordsState,
                            recordKind?.let(RecordKind::valueOf),
                            archiveDate?.let(LocalDate::parse),
                            { recordKind = it?.name },
                            {
                                archiveMonth =
                                    (it?.let(YearMonth::from) ?: selectedArchiveMonth).toString()
                                archiveDate = it?.toString()
                            },
                            records::refresh,
                            records::load,
                            { openRecord(it.kind, it.id) },
                            create,
                            back,
                            month = selectedArchiveMonth,
                            onMonth = {
                                archiveMonth = it.toString()
                                archiveDate = null
                            },
                        )
                    "security" ->
                        ProfileSecurityScreen(userEmail, back) { pages = pages + "password" }
                    "password" -> passwordPage(back)
                    "info" ->
                        profileState.profile?.let { ProfileInfoScreen(it, back) { open("edit") } }
                    "edit" ->
                        ProfileEditScreen(
                            profileState,
                            back,
                            profile::nickname,
                            profile::gender,
                            { profile.saveProfile(back) },
                            { pages = pages + "security" },
                            profile::avatar,
                            { bytes, mime -> profile.uploadAvatar(recordsRepository, bytes, mime) },
                            profile::uploadError,
                            { profile.retryAvatar(recordsRepository) },
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
                            { pages = pages + "security" },
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
                                    } else
                                        when (it) {
                                            "记体重" -> create(RecordKind.BODY)
                                            "记饮食" -> create(RecordKind.MEAL)
                                            "记训练" -> create(RecordKind.WORKOUT)
                                            "记照片" -> create(RecordKind.PHOTO)
                                            "全部记录" -> {
                                                archiveDate = state.date.toString()
                                                recordKind = null
                                                pages = pages + "records"
                                            }
                                        }
                                },
                                onRecord = { openRecord(it.kind, it.id) },
                            )
                        else if (tab == 1)
                            TrendsScreen(
                                trendsState,
                                trends::load,
                                { create(RecordKind.BODY) },
                                trends::loadInsights,
                            )
                        else if (tab == 2)
                            RecordsScreen(
                                recordsState,
                                recordKind?.let(RecordKind::valueOf),
                                archiveDate?.let(LocalDate::parse),
                                { recordKind = it?.name },
                                {
                                    archiveMonth =
                                        (it?.let(YearMonth::from) ?: selectedArchiveMonth)
                                            .toString()
                                    archiveDate = it?.toString()
                                },
                                records::refresh,
                                records::load,
                                { openRecord(it.kind, it.id) },
                                create,
                                month = selectedArchiveMonth,
                                onMonth = {
                                    archiveMonth = it.toString()
                                    archiveDate = null
                                },
                            )
                        else
                            ProfileScreen(
                                profileState,
                                profile::load,
                                { pages = pages + "settings" },
                                { open("edit") },
                                { open("goal") },
                                {
                                    archiveDate = null
                                    recordKind = null
                                    tab = 2
                                },
                                onRecords = { kind ->
                                    archiveDate = null
                                    recordKind = kind.name
                                    pages = pages + "records"
                                },
                                onSecurity = { pages = pages + "security" },
                                onPolicy = onPolicy,
                                onAbout = { pages = pages + "about" },
                                themeMode = themeMode,
                                onThemeMode = onThemeMode,
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

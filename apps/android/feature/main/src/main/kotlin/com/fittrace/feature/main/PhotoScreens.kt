package com.fittrace.feature.main

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.fittrace.core.designsystem.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoRecordScreen(
    state: EditorState,
    model: RecordEditorViewModel,
    onBack: () -> Unit,
) {
    val d = state.draft
    val creating = d.id == null
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val time = OffsetDateTime.parse(d.time).atZoneSameInstant(ZoneId.systemDefault())
    val busy = state.saving || reading
    fun upload(uri: Uri) {
        reading = true
        scope.launch {
            val result =
                withContext(Dispatchers.IO) { runCatching { readSelectedImage(context, uri) } }
            reading = false
            result.fold(
                { model.upload(it.first, it.second) },
                { model.error(it.message ?: "无法读取照片") },
            )
        }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                selectedUri = it.toString()
                model.edit { draft -> draft.copy(imageUrl = null) }
                upload(it)
            }
        }
    val back = {
        if (!busy) {
            if (state.dirty) leave = true else onBack()
        }
    }
    BackHandler { back() }
    val content: @Composable () -> Unit = {
        PhotoPage(
            if (creating) "添加身材照片" else "照片详情",
            back,
            footer = {
                if (!state.loading && !state.loadingFailed) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (creating) {
                            Button(
                                onClick = model::save,
                                enabled = !busy && d.imageUrl != null,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) {
                                Text(
                                    if (state.uploading || reading) "正在上传…"
                                    else if (state.saving) "正在保存…"
                                    else if (state.error != null && d.imageUrl != null) "重新保存"
                                    else "保存照片"
                                )
                            }
                        } else
                            OutlinedButton(
                                onClick = { deleting = true },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) {
                                Text("删除照片", color = MaterialTheme.colorScheme.error)
                            }
                    }
                }
            },
        ) {
            when {
                state.loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在读取照片…", fontSize = 13.sp)
                }
                state.loadingFailed -> FitMessage("照片读取失败", model::load, true)
                else -> {
                    state.error?.let { FitMessage(it, error = true) }
                    if (!creating)
                        Text(
                            "${time.format(DateTimeFormatter.ofPattern("M月d日"))} · ${photoLabels[d.type] ?: "其他"}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    FitCard {
                        val image = selectedUri ?: d.imageUrl
                        if (image == null)
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(220.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(16.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("选择一张照片，记录身体的变化", fontSize = 13.sp)
                            }
                        else
                            PhotoImage(
                                image,
                                Modifier.fillMaxWidth().height(if (creating) 160.dp else 390.dp),
                                fit = true,
                            )
                        if (creating) {
                            OutlinedButton(
                                onClick = {
                                    picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (image == null) "从相册选择" else "重新选择")
                            }
                            if (!busy && selectedUri != null && d.imageUrl == null)
                                TextButton(onClick = { upload(selectedUri!!.toUri()) }) {
                                    Text("重试上传")
                                }
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                if (state.uploading || reading) "正在上传，请稍候…"
                                else if (d.imageUrl != null) "已上传，保存后加入照片归档"
                                else "支持 JPG / PNG / WebP，最大 6 MB",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (creating) {
                        PhotoAngles(
                            d.type,
                            { value -> model.edit { it.copy(type = value) } },
                            !busy,
                            all = false,
                        )
                        FitCard {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("拍摄日期", fontSize = 13.sp)
                                TextButton(onClick = { date = true }, enabled = !busy) {
                                    Text(
                                        time.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) +
                                            " ›",
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            TextButton(onClick = { clock = true }, enabled = !busy) {
                                Text("拍摄时间 · ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                            }
                            FitTextField(
                                d.note,
                                { value -> model.edit { it.copy(note = value) } },
                                Modifier.fillMaxWidth(),
                                label = { Text("备注（选填）") },
                                maxLines = 4,
                                enabled = !busy,
                            )
                        }
                        Text(
                            "尽量保持相同光线、距离和拍摄角度。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else
                        FitCard {
                            Text(
                                "备注",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(d.note.ifBlank { "未填写备注" }, fontSize = 14.sp)
                            Text(
                                d.time.localTime(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }
    }
    val latestBusy by rememberUpdatedState(busy)
    val latestDirty by rememberUpdatedState(state.dirty)
    val photoSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                if (target == SheetValue.Hidden && (latestBusy || latestDirty)) {
                    if (!latestBusy) leave = true
                    false
                } else true
            },
        )
    if (creating)
        ModalBottomSheet(onDismissRequest = back, sheetState = photoSheetState, dragHandle = null) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(.94f)) { content() }
        }
    else content()
    if (date)
        RecordDateDialog(time.toLocalDate(), { date = false }) { value ->
            model.edit {
                it.copy(
                    time =
                        value
                            .atTime(time.toLocalTime())
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                )
            }
            date = false
        }
    if (clock)
        RecordTimeDialog(time.toLocalTime(), { clock = false }) { value ->
            model.edit {
                it.copy(
                    time =
                        time
                            .toLocalDate()
                            .atTime(value)
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                )
            }
            clock = false
        }
    if (leave)
        FitDialog(
            onDismissRequest = { leave = false },
            title = { Text("放弃本次照片记录？") },
            text = { Text("照片尚未加入归档，未保存的填写内容会丢失。") },
            confirmButton = { FitDialogButton(onClick = onBack) { Text("放弃") } },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { leave = false }) { Text("继续记录") }
            },
        )
    if (deleting)
        FitDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除这张照片？") },
            text = { Text("删除后无法恢复，照片记录和备注将一起移除。") },
            confirmButton = {
                FitDialogButton(
                    destructive = true,
                    onClick = {
                        deleting = false
                        model.delete()
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { deleting = false }) { Text("取消") }
            },
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoArchiveScreen(
    state: RecordsState,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    refresh: () -> Unit,
    more: () -> Unit,
    open: (RecordItem) -> Unit,
    create: () -> Unit,
    back: (() -> Unit)?,
    allRecords: () -> Unit,
) {
    var angle by rememberSaveable { mutableStateOf("") }
    var monthPicker by remember { mutableStateOf(false) }
    PullToRefreshBox(state.loading && state.items.isNotEmpty(), refresh) {
        PhotoPage(
            "身材照片",
            back,
            footer = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Button(onClick = create, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("＋ 添加照片")
                    }
                }
            },
        ) {
            Text("按月份回看身体的变化", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PhotoAngles(angle, { angle = it })
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { monthPicker = true }) {
                    Text(
                        "${month.year}年${month.monthValue}月 ⌄",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = allRecords) { Text("所有档案", fontSize = 12.sp) }
            }
            when {
                state.loading && state.items.isEmpty() -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在读取照片…", fontSize = 13.sp)
                }
                state.error != null && state.items.isEmpty() -> FitMessage("照片加载失败", refresh, true)
                else -> {
                    state.error?.let {
                        FitMessage(it, if (state.loadMoreError) more else refresh, true)
                    }
                    val items =
                        state.items.filter {
                            it.kind == com.fittrace.core.model.RecordKind.PHOTO &&
                                (angle.isBlank() || it.title == photoLabels[angle])
                        }
                    if (items.isEmpty())
                        FitCard {
                            Text(
                                if (state.items.isEmpty()) "留下第一张身材照片" else "当前已加载照片中暂无这个角度",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "每一次记录都值得留下",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    items.chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { item ->
                                Column(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { open(item) }
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item.image?.let {
                                        PhotoImage(it, Modifier.fillMaxWidth().aspectRatio(.8f))
                                    }
                                    Text(
                                        "${OffsetDateTime.parse(item.time).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日"))} · ${item.title}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (state.items.size < state.total)
                        OutlinedButton(
                            onClick = more,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.loading) "正在加载…"
                                else if (state.loadMoreError) "重试加载更多" else "加载更多照片"
                            )
                        }
                }
            }
        }
    }
    if (monthPicker)
        ArchiveMonthPicker(month, { monthPicker = false }) {
            onMonth(it)
            monthPicker = false
        }
}

@Composable
private fun PhotoAngles(
    value: String,
    change: (String) -> Unit,
    enabled: Boolean = true,
    all: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (if (all) linkedMapOf("" to "全部") + photoLabels else photoLabels).forEach { (key, label) ->
            FilterChip(
                value == key,
                { change(key) },
                { Text(label, fontSize = 12.sp) },
                enabled = enabled,
            )
        }
    }
}

@Composable
internal fun PhotoImage(url: String, modifier: Modifier, fit: Boolean = false) {
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    var retry by remember(url) { mutableIntStateOf(0) }
    Box(
        modifier
            .semantics {
                stateDescription = if (loading) "照片加载中" else if (failed) "照片加载失败" else "照片已加载"
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        key(url, retry) {
            AsyncImage(
                url,
                "身材照片",
                Modifier.fillMaxSize(),
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                onLoading = {
                    loading = true
                    failed = false
                },
                onSuccess = {
                    loading = false
                    failed = false
                },
                onError = {
                    loading = false
                    failed = true
                },
            )
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        if (failed)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("照片加载失败", fontSize = 12.sp)
                TextButton(
                    onClick = {
                        failed = false
                        loading = true
                        retry++
                    }
                ) {
                    Text("重试图片")
                }
            }
    }
}

@Composable
private fun PhotoPage(
    title: String,
    back: (() -> Unit)?,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { Surface { footer() } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (back != null)
                    TextButton(
                        onClick = back,
                        modifier = Modifier.width(32.dp).semantics { contentDescription = "返回" },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("‹", fontSize = 28.sp)
                    }
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

package com.fittrace.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealToolsSheet(
    controller: MealToolsController,
    draft: RecordDraft,
    dismiss: () -> Unit,
    add: (List<FoodEntry>) -> Boolean,
) {
    val state by controller.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var description by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<MealFavorite?>(null) }
    LaunchedEffect(Unit) { controller.load() }
    ModalBottomSheet(
        onDismissRequest = { if (!state.busy) dismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight(.92f)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("饮食工具", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            InsightsSegments(listOf("AI 速记", "最近", "常用餐"), tab) { if (!state.busy) tab = it }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.error?.let { FitMessage(it, error = true) }
                state.notice?.let {
                    Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                }
                when (tab) {
                    0 -> {
                        Text(
                            "描述这一餐吃了什么，识别后核对份量与热量。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            description,
                            { description = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("餐食描述 / 照片补充说明") },
                            enabled = !state.busy,
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(16.dp),
                        )
                        FitSaveButton(state.busy, { controller.recognize(description) }, "识别餐食描述")
                        if (draft.imageUrl != null)
                            OutlinedButton(
                                onClick = { controller.recognize(description, draft.imageUrl) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("识别已上传的餐食照片")
                            }
                        else
                            Text(
                                "照片识别可先返回核对页添加并上传餐食照片。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        state.recognition?.let { result ->
                            val mock =
                                result.provider.equals("mock", true) || result.provider.isBlank()
                            Text(
                                if (mock) "当前为模拟识别，示例结果不能加入餐食" else "AI 估算 · 请逐项核对",
                                color =
                                    if (mock) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.secondary,
                                fontSize = 13.sp,
                            )
                            result.foods.forEachIndexed { i, food ->
                                FitCard {
                                    Text(food.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${food.amount ?: "份量待补充"} · ${food.calories?.let { "$it kcal" } ?: "热量待补充"}",
                                        fontSize = 12.sp,
                                    )
                                    if (!mock)
                                        Row {
                                            TextButton(onClick = { editing = i }) { Text("修改") }
                                            TextButton(
                                                onClick = { controller.suggestion(i, null) }
                                            ) {
                                                Text("移除")
                                            }
                                        }
                                }
                            }
                            Text(
                                "加入清单后仍需保存这餐，不会自动写入记录。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    1 -> {
                        Text(
                            "复用最近 20 条记录中的食物，保留本次日期与餐次。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.recentError?.let { FitMessage(it, controller::load, true) }
                        if (!state.loading && state.recent.isEmpty() && state.recentError == null)
                            FitMessage("还没有可复用的饮食记录")
                        state.recent.forEach { meal ->
                            FitCard {
                                Text(
                                    "${meal.recordedAt.localTime()} · ${mealLabels[meal.type] ?: "饮食"}",
                                    fontSize = 12.sp,
                                )
                                Text(meal.foods.joinToString("、") { it.name }, fontSize = 13.sp)
                                TextButton(
                                    onClick = { if (add(meal.foods)) dismiss() },
                                    enabled = !state.busy && meal.foods.isNotEmpty(),
                                ) {
                                    Text("复用这餐食物")
                                }
                            }
                        }
                    }
                    else -> {
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.favoritesError?.let { FitMessage(it, controller::load, true) }
                        if (draft.foods.isNotEmpty())
                            FitCard {
                                OutlinedTextField(
                                    name,
                                    { name = it },
                                    Modifier.fillMaxWidth(),
                                    label = { Text("常用餐名称") },
                                    singleLine = true,
                                    enabled = !state.busy,
                                )
                                TextButton(
                                    onClick = { controller.favorite(name, draft.foods) },
                                    enabled = !state.busy,
                                ) {
                                    Text("将这餐保存为常用")
                                }
                            }
                        if (
                            !state.loading &&
                                state.favorites.isEmpty() &&
                                state.favoritesError == null
                        )
                            FitMessage(
                                if (draft.foods.isEmpty()) "还没有常用餐，先添加食物后保存为常用" else "还没有已保存的常用餐"
                            )
                        state.favorites.forEach { favorite ->
                            FitCard {
                                Text(favorite.name, fontWeight = FontWeight.Bold)
                                Text(favorite.foods.joinToString("、") { it.name }, fontSize = 12.sp)
                                Row {
                                    TextButton(
                                        onClick = { if (add(favorite.foods)) dismiss() },
                                        enabled = !state.busy,
                                    ) {
                                        Text("加入这餐")
                                    }
                                    TextButton(
                                        onClick = { deleting = favorite },
                                        enabled = !state.busy,
                                    ) {
                                        Text(
                                            "移除常用",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (tab == 0)
                state.recognition?.let { result ->
                    Button(
                        onClick = {
                            if (add(result.foods)) {
                                controller.clearRecognition()
                                dismiss()
                            }
                        },
                        enabled =
                            result.provider.isNotBlank() &&
                                !result.provider.equals("mock", true) &&
                                result.foods.isNotEmpty() &&
                                !state.busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("核对后加入清单")
                    }
                }
            TextButton(
                onClick = dismiss,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回核对这餐")
            }
        }
    }
    editing?.let { i ->
        state.recognition?.foods?.getOrNull(i)?.let { food ->
            FoodDialog(food, { editing = null }) {
                controller.suggestion(i, it.copy(aiGenerated = true))
                editing = null
            }
        }
    }
    deleting?.let { favorite ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("移除这份常用餐？") },
            text = { Text("不会删除已有饮食记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.removeFavorite(favorite.id)
                        deleting = null
                    }
                ) {
                    Text("确认移除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

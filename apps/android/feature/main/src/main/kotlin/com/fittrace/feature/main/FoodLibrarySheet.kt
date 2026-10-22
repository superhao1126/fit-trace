package com.fittrace.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fittrace.core.model.FoodEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodLibrarySheet(
    onDismiss: () -> Unit,
    onCustom: () -> Unit,
    selectedFoods: List<FoodEntry> = emptyList(),
    mealLabel: String = "选择食物",
    portionFoodName: String? = null,
    portionGrams: String = "100",
    onTools: (() -> Unit)? = null,
    onAdd: (FoodEntry) -> Unit,
) {
    val foods = catalogFoodsRemembered()
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var selectedName by rememberSaveable { mutableStateOf(portionFoodName) }
    var grams by rememberSaveable { mutableStateOf(portionGrams) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = foods.firstOrNull { it.name == selectedName }
    val matches =
        remember(foods, search, category) {
            foods.filter {
                (category == "全部" || category == it.category) &&
                    (it.name.contains(search.trim(), true) ||
                        it.aliases.contains(search.trim(), true))
            }
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        if (selected == null)
            Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(0.dp),
                            modifier =
                                Modifier.width(30.dp).semantics { contentDescription = "返回饮食清单" },
                        ) {
                            Text("‹", fontSize = 26.sp)
                        }
                        Text(
                            mealLabel,
                            Modifier.weight(1f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = onCustom) { Text("自定义", fontSize = 13.sp) }
                    }
                    OutlinedTextField(
                        search,
                        { search = it },
                        Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索食物名称，例如：米饭", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                }
                if (onTools != null)
                    TextButton(onClick = onTools, modifier = Modifier.fillMaxWidth()) {
                        Text("AI 速记 · 最近 · 常用餐", fontSize = 13.sp)
                    }
                Row(Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.width(86.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(listOf("全部") + foods.map { it.category }.distinct()) { name ->
                            Surface(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable { category = name }
                                    .semantics { this.selected = category == name },
                                shape = RoundedCornerShape(22.dp),
                                color =
                                    if (category == name) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                            ) {
                                Box(
                                    Modifier.heightIn(min = 42.dp).padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        name,
                                        fontSize = 12.sp,
                                        fontWeight =
                                            if (category == name) FontWeight.Bold
                                            else FontWeight.Normal,
                                        color =
                                            if (category == name)
                                                MaterialTheme.colorScheme.secondary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (matches.isEmpty())
                        Column(
                            Modifier.weight(1f).fillMaxHeight().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("没有找到相关食物", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "换个关键词，或手动添加",
                                Modifier.padding(top = 8.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onCustom) { Text("自定义食物") }
                        }
                    else
                        LazyColumn(
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(matches, key = { it.name }) { food ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selectedName = food.name
                                        grams = "100"
                                        error = null
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    AsyncImage(
                                        food.image,
                                        null,
                                        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(
                                            food.name,
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${food.kcal.number()} kcal / 100 g",
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        val count = selectedFoods.count { it.name == food.name }
                                        if (count > 0)
                                            Text(
                                                "已选 $count 项",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                    }
                                    FilledIconButton(
                                        onClick = {
                                            selectedName = food.name
                                            grams = "100"
                                            error = null
                                        },
                                        modifier =
                                            Modifier.size(32.dp).semantics {
                                                contentDescription = "添加${food.name}"
                                            },
                                    ) {
                                        Text(
                                            "+",
                                            fontSize = 22.sp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(painterResource(R.drawable.design_sprout), null, Modifier.size(42.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "已选 ${selectedFoods.size} 项",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        val known = selectedFoods.mapNotNull { it.calories }
                        Text(
                            if (known.isEmpty()) "热量待补充" else "已记录 ${known.sum()} kcal",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Button(onClick = onDismiss, shape = RoundedCornerShape(24.dp)) {
                        Text("核对这餐", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        else
            Column(
                Modifier.fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.name} · 调整份量",
                        Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = {
                            if (portionFoodName != null) onDismiss() else selectedName = null
                        },
                        modifier =
                            Modifier.width(40.dp).semantics { contentDescription = "关闭份量编辑" },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("×", fontSize = 24.sp)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(
                        selected.image,
                        null,
                        Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("每 100 g · ${selected.kcal.number()} kcal", fontSize = 14.sp)
                        Text(
                            "按可食部分的实际克数记录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                grams =
                                    ((grams.toDoubleOrNull() ?: 100.0) - 25)
                                        .coerceAtLeast(1.0)
                                        .number()
                                error = null
                            },
                            modifier =
                                Modifier.width(40.dp).semantics { contentDescription = "减少 25 克" },
                        ) {
                            Text("−", fontSize = 24.sp)
                        }
                        OutlinedTextField(
                            grams,
                            {
                                grams = it
                                error = null
                            },
                            Modifier.weight(1f),
                            label = { Text("可食份量 · g") },
                            singleLine = true,
                            textStyle =
                                LocalTextStyle.current.copy(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(20.dp),
                        )
                        TextButton(
                            onClick = {
                                grams =
                                    ((grams.toDoubleOrNull() ?: 100.0) + 25)
                                        .coerceAtMost(10000.0)
                                        .number()
                                error = null
                            },
                            modifier =
                                Modifier.width(40.dp).semantics { contentDescription = "增加 25 克" },
                        ) {
                            Text("+", fontSize = 24.sp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(50, 100, 150, 200).forEach { value ->
                        TextButton(
                            onClick = {
                                grams = value.toString()
                                error = null
                            }
                        ) {
                            Text("${value}g", fontSize = 12.sp)
                        }
                    }
                }
                val weight =
                    grams.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 10000 }
                Text(
                    "当前约 ${weight?.let { (it * selected.kcal / 100).number() } ?: "—"} kcal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    "营养参考 USDA SR Legacy，每 100 g 可食部分；实际烹饪会有差异。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (weight == null) error = "请输入 0–10000 g 范围内的正数"
                        else {
                            onAdd(
                                FoodEntry(
                                    selected.name,
                                    "${weight.number()} g",
                                    kotlin.math.round(weight * selected.kcal / 100).toInt(),
                                )
                            )
                            selectedName = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("加入清单", color = MaterialTheme.colorScheme.onPrimary)
                }
                TextButton(
                    onClick = { if (portionFoodName != null) onDismiss() else selectedName = null }
                ) {
                    Text("继续选择")
                }
            }
    }
}

@Composable
private fun catalogFoodsRemembered(): List<CatalogFood> {
    val context = LocalContext.current
    return remember(context) { catalogFoods(context) }
}

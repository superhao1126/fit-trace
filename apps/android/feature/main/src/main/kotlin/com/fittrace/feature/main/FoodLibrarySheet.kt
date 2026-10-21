package com.fittrace.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fittrace.core.model.FoodEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodLibrarySheet(
    onDismiss: () -> Unit,
    onCustom: () -> Unit,
    onAdd: (FoodEntry) -> Unit,
) {
    val context = LocalContext.current
    val foods = remember { catalogFoods(context) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    var selected by remember { mutableStateOf<CatalogFood?>(null) }
    var grams by remember { mutableStateOf("100") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.88f).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("选择食物", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCustom) { Text("自定义") }
            }
            OutlinedTextField(
                search,
                { search = it },
                Modifier.fillMaxWidth(),
                label = { Text("搜索食物") },
                singleLine = true,
            )
            if (selected == null)
                Row(Modifier.weight(1f)) {
                    LazyColumn(Modifier.width(74.dp)) {
                        items(listOf("全部") + foods.map { it.category }.distinct()) { name ->
                            FilterChip(category == name, { category = name }, { Text(name) })
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            foods.filter {
                                (category == "全部" || category == it.category) &&
                                    (it.name.contains(search, true) ||
                                        it.aliases.contains(search, true))
                            }
                        ) { food ->
                            ListItem(
                                headlineContent = { Text(food.name) },
                                supportingContent = { Text("${food.kcal.number()} kcal / 100 g") },
                                leadingContent = {
                                    AsyncImage(food.image, null, Modifier.size(44.dp))
                                },
                                modifier =
                                    Modifier.clickable {
                                        selected = food
                                        grams = "100"
                                    },
                            )
                        }
                    }
                }
            else {
                val food = selected!!
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                AsyncImage(food.image, null, Modifier.fillMaxWidth().height(110.dp))
                OutlinedTextField(
                    grams,
                    {
                        grams = it
                        error = null
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("可食份量 · g") },
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                    singleLine = true,
                )
                val weight =
                    grams.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 10000 }
                Text("${weight?.let { (it * food.kcal / 100).number() } ?: "—"} kcal")
                Text(
                    "营养参考 USDA SR Legacy，每 100 g 可食部分；实际烹饪会有差异。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (weight == null) error = "请输入 0–10000 g 范围内的正数"
                        else
                            onAdd(
                                FoodEntry(
                                    food.name,
                                    "$grams g",
                                    kotlin.math.round(weight * food.kcal / 100).toInt(),
                                )
                            )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("加入清单")
                }
                TextButton(onClick = { selected = null }) { Text("继续选择") }
            }
        }
    }
}

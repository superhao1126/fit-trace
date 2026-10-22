package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fittrace.core.designsystem.FitTraceTheme
import com.fittrace.core.model.FoodEntry
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoodLibrarySheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `food portion validates and supports repeated selections before review`() {
        var selected by mutableStateOf(emptyList<FoodEntry>())
        var review = false
        compose.setContent {
            FitTraceTheme {
                FoodLibrarySheet(
                    { review = true },
                    {},
                    selectedFoods = selected,
                    mealLabel = "10月19日 · 午餐",
                ) {
                    selected = selected + it
                }
            }
        }
        compose.onNodeWithText("米饭（熟）").assertIsDisplayed()
        capture("food-picker-390")
        compose.onNodeWithText("米饭（熟）").performClick()
        compose.onNodeWithText("150g").performClick()
        compose.onNodeWithText("当前约 195 kcal").assertIsDisplayed()
        capture("food-portion-390")
        compose.onNodeWithContentDescription("可食份量 · g").performTextReplacement("0")
        compose.onNodeWithText("加入清单").performClick()
        compose.onNodeWithText("请输入 0–10000 g 范围内的正数").assertIsDisplayed()
        assertTrue(selected.isEmpty())
        compose.onNodeWithText("100g").performClick()
        compose.onNodeWithContentDescription("增加 25 克").performClick()
        compose.onNodeWithText("加入清单").performClick()
        assertEquals("125 g", selected.single().amount)
        assertEquals(162, selected.single().calories)
        compose.onAllNodesWithText("已选 1 项").onFirst().assertExists()
        compose.onNodeWithText("米饭（熟）").performClick()
        compose.onNodeWithText("50g").performClick()
        compose.onNodeWithText("加入清单").performClick()
        assertEquals(2, selected.size)
        compose.onNodeWithText("核对这餐").performClick()
        assertTrue(review)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `narrow search has explicit empty state and custom entry`() {
        var custom = false
        compose.setContent { FitTraceTheme(true) { FoodLibrarySheet({}, { custom = true }) {} } }
        compose.onNodeWithText("搜索食物名称，例如：米饭").performTextInput("没有这种食物")
        compose.onNodeWithText("没有找到相关食物").assertIsDisplayed()
        capture("food-search-empty-dark-320")
        compose.onNodeWithText("自定义食物").performClick()
        assertTrue(custom)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow portion accepts decimals and both stepper buttons`() {
        var selected: FoodEntry? = null
        compose.setContent {
            FitTraceTheme(true) {
                FoodLibrarySheet({}, {}, portionFoodName = "米饭（熟）") { selected = it }
            }
        }
        compose.onNodeWithContentDescription("可食份量 · g").performTextReplacement("100.5")
        compose.onNodeWithContentDescription("减少 25 克").performClick()
        compose.onNodeWithText("75.5").assertExists()
        compose.onNodeWithContentDescription("增加 25 克").performClick()
        compose.onNodeWithText("100.5").assertExists()
        capture("food-portion-dark-320")
        compose.onNodeWithText("加入清单").performClick()
        assertEquals("100.5 g", selected!!.amount)
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/food-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

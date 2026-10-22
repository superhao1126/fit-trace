package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fittrace.core.designsystem.FitTraceTheme
import com.fittrace.core.model.*
import java.io.File
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MealToolsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 19)

    @Test
    fun `AI is explicitly reviewed before adding unknown calories and retains origin`() {
        var saved = false
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun recognizeMealText(
                    description: String
                ): AppResult<FoodRecognition> {
                    assertEquals("一份自制菜", description)
                    return AppResult.Success(
                        FoodRecognition(
                            "configured-model",
                            listOf(FoodEntry("自制菜", "1 份", null, true)),
                        )
                    )
                }

                override suspend fun saveMeal(input: MealInput, id: String?): AppResult<MealEntry> {
                    saved = true
                    return AppResult.Failure(AppError.Network)
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.MEAL, date, null)
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("AI 速记 · 最近 · 常用餐").performClick()
        compose.onNodeWithText("餐食描述 / 照片补充说明").performTextInput("一份自制菜")
        compose.onNodeWithText("识别餐食描述").performClick()
        compose.onNodeWithText("AI 估算 · 请逐项核对").performScrollTo().assertIsDisplayed()
        assertTrue(model.state.value.draft.foods.isEmpty())
        capture("meal-ai-review-390")
        compose.onNodeWithText("核对后加入清单").performClick()
        assertEquals(listOf(FoodEntry("自制菜", "1 份", null, true)), model.state.value.draft.foods)
        assertFalse(saved)
        assertTrue(model.state.value.draft.time.startsWith("2026-10-19"))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `mock AI is clearly labeled and cannot be added`() {
        val model =
            RecordEditorViewModel(
                object : RecordsRepositoryFake() {
                    override suspend fun recognizeMealText(description: String) =
                        AppResult.Success(
                            FoodRecognition("mock", listOf(FoodEntry("示例食物", "1份", 100, true)))
                        )
                },
                RecordKind.MEAL,
                date,
                null,
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("AI 速记 · 最近 · 常用餐").performClick()
        compose.onNodeWithText("餐食描述 / 照片补充说明").performTextInput("测试描述")
        compose.onNodeWithText("识别餐食描述").performClick()
        compose.onNodeWithText("核对后加入清单").assertIsNotEnabled()
        assertTrue(model.state.value.draft.foods.isEmpty())
        capture("meal-ai-mock-dark-320")
    }

    @Test
    fun `recent reuse retains target date and favorite failure retains form`() {
        var favoriteName: String? = null
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun meals(page: Int, from: String?, to: String?) =
                    AppResult.Success(
                        RecordPage(
                            listOf(
                                MealEntry(
                                    "old",
                                    "DINNER",
                                    "2026-10-18T18:00:00+08:00",
                                    listOf(FoodEntry("自制晚餐", "1 碗", null)),
                                    null,
                                    null,
                                    null,
                                )
                            ),
                            1,
                        )
                    )

                override suspend fun saveMealFavorite(
                    name: String,
                    foods: List<FoodEntry>,
                ): AppResult<MealFavorite> {
                    favoriteName = name
                    return AppResult.Failure(AppError.Network)
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.MEAL, date, null)
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("AI 速记 · 最近 · 常用餐").performClick()
        compose.onNodeWithText("最近", useUnmergedTree = true).performClick()
        compose.onNodeWithText("复用这餐食物").performScrollTo().performClick()
        assertEquals("自制晚餐", model.state.value.draft.foods.single().name)
        assertTrue(model.state.value.draft.time.startsWith("2026-10-19"))
        assertEquals("BREAKFAST", model.state.value.draft.type)
        compose.onNodeWithText("饮食工具").performClick()
        compose.onNodeWithText("常用餐", useUnmergedTree = true).performClick()
        compose.onNodeWithText("常用餐名称").performTextInput("我的常用")
        compose.onNodeWithText("将这餐保存为常用").performClick()
        compose.waitUntil { favoriteName != null && !model.mealTools.state.value.busy }
        compose.onNodeWithText("我的常用", useUnmergedTree = true).assertIsDisplayed()
        assertNull(model.state.value.draft.foods.single().calories)
        capture("meal-favorite-failed-390")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/meal-tools-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

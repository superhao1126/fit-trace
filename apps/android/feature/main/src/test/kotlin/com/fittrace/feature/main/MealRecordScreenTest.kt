package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
class MealRecordScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    @Before
    fun imageDecoder() {
        coil3.SingletonImageLoader.setUnsafe(
            coil3.ImageLoader.Builder(compose.activity)
                .components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }
                .build()
        )
    }

    private fun testPhoto(): String {
        val file = File(compose.activity.cacheDir, "meal-test-photo.jpg")
        compose.activity.resources.openRawResource(R.drawable.sport_walking).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return android.net.Uri.fromFile(file).toString()
    }

    private fun waitPhoto() {
        compose.waitUntil(5000) {
            compose
                .onAllNodes(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                        "照片已加载",
                    )
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private val date = LocalDate.of(2026, 10, 19)

    @Test
    fun `review adjusts actual portion keeps unknown calories and saves only explicitly`() {
        var saved: MealInput? = null
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun saveMeal(input: MealInput, id: String?): AppResult<MealEntry> {
                    saved = input
                    return AppResult.Failure(AppError.Network)
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.MEAL, date, null)
        model.edit {
            it.copy(
                type = "LUNCH",
                time = "2026-10-19T12:30:00+08:00",
                foods = listOf(FoodEntry("米饭（熟）", "100 g", 130), FoodEntry("自制菜", "1 碗", null)),
            )
        }
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("核对这餐").assertIsDisplayed()
        compose.onNodeWithText("这餐约 130 kcal").performScrollTo().assertIsDisplayed()
        assertNull(saved)
        capture("meal-review-390")
        compose.onAllNodesWithText("修改").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("150g").performClick()
        compose.onNodeWithText("加入清单").performClick()
        compose.onNodeWithText("这餐约 195 kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存这餐").performClick()
        compose.waitUntil { saved != null && model.state.value.error != null }
        assertEquals("150 g", saved!!.foods.first().amount)
        assertEquals(195, saved!!.foods.first().calories)
        assertNull(saved!!.foods.last().calories)
        assertEquals("2026-10-19T12:30:00+08:00", saved!!.recordedAt)
        compose.onNodeWithText("重新保存").assertIsDisplayed()
        assertEquals(2, model.state.value.draft.foods.size)
        capture("meal-save-failed-390")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark meal details preserve unknown calories and delete confirmation`() {
        val entry =
            MealEntry(
                "meal",
                "DINNER",
                "2026-10-19T19:10:00+08:00",
                listOf(FoodEntry("自制晚餐", "1 份", null)),
                null,
                "外食估算待补充",
                null,
            )
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun meal(id: String) = AppResult.Success(entry)
            }
        val model = RecordEditorViewModel(repo, RecordKind.MEAL, date, "meal")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        compose.onNodeWithText("这餐约 — kcal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("编辑这餐").assertIsDisplayed()
        capture("meal-detail-dark-320")
        compose.onNodeWithText("删除这条记录").performClick()
        compose.onNodeWithText("确认删除").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertFalse(model.state.value.saved)
    }

    @Test
    fun `meal detail loading error cannot be mistaken for empty meal`() {
        val model = RecordEditorViewModel(RecordsRepositoryFake(), RecordKind.MEAL, date, "meal")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { model.state.value.loadingFailed }
        compose.onNodeWithText("饮食记录读取失败").assertIsDisplayed()
        compose.onNodeWithText("这餐还没有食物").assertDoesNotExist()
        capture("meal-load-failed-390")
    }

    @Test
    fun `meal editing preserves photo until explicit removal is saved`() {
        val writes = mutableListOf<MealInput>()
        val image = testPhoto()
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun meal(id: String) =
                    AppResult.Success(
                        MealEntry(
                            id,
                            "LUNCH",
                            "2026-10-19T12:00:00+08:00",
                            listOf(FoodEntry("午餐", "1 份", null)),
                            null,
                            null,
                            image,
                        )
                    )

                override suspend fun saveMeal(input: MealInput, id: String?): AppResult<MealEntry> {
                    writes += input
                    return AppResult.Failure(AppError.Network)
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.MEAL, date, "meal")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        compose.onNodeWithText("编辑这餐").performClick()
        compose.onNodeWithText("保存这餐").performClick()
        compose.waitUntil { writes.size == 1 }
        assertEquals(image, writes.first().imageUrl)
        compose.onNodeWithText("移除餐食照片").performScrollTo()
        waitPhoto()
        capture("meal-photo-edit-390")
        compose.onNodeWithText("移除餐食照片").performClick()
        compose.onNodeWithText("保存这餐").performClick()
        compose.waitUntil { writes.size == 2 }
        assertNull(writes.last().imageUrl)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow meal photo card renders uploaded image and removal controls`() {
        val image = testPhoto()
        val model = RecordEditorViewModel(RecordsRepositoryFake(), RecordKind.MEAL, date, null)
        model.edit { it.copy(imageUrl = image) }
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.padding(24.dp)
                    ) {
                        MealPhotoCard(state, model, true) {}
                    }
                }
            }
        }
        waitPhoto()
        compose.onNodeWithText("移除餐食照片").assertIsDisplayed()
        capture("meal-photo-dark-320")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/meal-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

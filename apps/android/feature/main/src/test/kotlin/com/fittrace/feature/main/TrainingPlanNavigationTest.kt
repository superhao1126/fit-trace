package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
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
class TrainingPlanNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val arms =
        TrainingTemplate(
            "arms",
            "五日训练 · 周四手臂",
            40,
            listOf("绳索臂屈伸", "绳索下压", "哑铃颈后臂屈伸", "哑铃弯举").mapIndexed { index, name ->
                TrainingExercise(
                    listOf(
                        "cable-overhead-triceps-extension",
                        "triceps-pushdown",
                        "dumbbell-overhead-triceps-extension",
                        "dumbbell-biceps-curl",
                    )[index],
                    name,
                    "reps",
                    90,
                    List(4) { TrainingSet(12, 0.0, true) },
                )
            },
        )

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    @Before
    fun decodeBundledImages() {
        coil3.SingletonImageLoader.setUnsafe(
            coil3.ImageLoader.Builder(compose.activity)
                .components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }
                .build()
        )
    }

    @Test
    fun `tutorial renders both local poses and closes without changing plan or draft`() {
        val model = show(existingDraft = true)
        val before = model.state.value
        openArms()
        compose.onNodeWithContentDescription("查看绳索臂屈伸教程").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(
                    hasContentDescription("绳索臂屈伸示范图1") and
                        SemanticsMatcher.expectValue(
                            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                            "动作图已加载",
                        )
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("动作步骤").assertIsDisplayed()
        compose.onNodeWithText("示范图 2").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(
                    hasContentDescription("绳索臂屈伸示范图2") and
                        SemanticsMatcher.expectValue(
                            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                            "动作图已加载",
                        )
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        capture("weekly-plan-arm-tutorial-390")
        compose.onNodeWithText("动作要点").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("看完了").performClick()
        compose.onNodeWithText("周四训练计划").assertIsDisplayed()
        assertEquals(before.exercises, model.state.value.exercises)
        assertEquals(before.plan, model.state.value.plan)
        assertEquals(0, writes)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun `tutorial remains readable with reachable close action on narrow dark screen`() {
        show(dark = true)
        openArms()
        compose.onNodeWithContentDescription("查看绳索臂屈伸教程").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodes(
                    hasContentDescription("绳索臂屈伸示范图1") and
                        SemanticsMatcher.expectValue(
                            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                            "动作图已加载",
                        )
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("看完了").assertIsDisplayed()
        capture("weekly-plan-arm-tutorial-dark-320")
        compose.onNodeWithText("动作要点").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("看完了").performClick()
        compose.onNodeWithText("周四训练计划").assertIsDisplayed()
    }

    @Test
    fun `catalog media decodes offline and all planned exercises have tutorial steps`() {
        val context = compose.activity
        val exercises = catalogExercises(context)
        assertEquals(37, exercises.size)
        assertEquals(30, exercises.count { it.images.isNotEmpty() })
        exercises.forEach { exercise ->
            assertTrue(exercise.steps.isNotEmpty())
            assertTrue(exercise.tips.isNotBlank())
            exercise.images.forEach { uri ->
                context.assets.open(uri.removePrefix("file:///android_asset/")).use {
                    assertNotNull(exercise.id, android.graphics.BitmapFactory.decodeStream(it))
                }
            }
        }
        val rdl = exercises.single { it.id == "dumbbell-romanian-deadlift" }
        assertTrue(rdl.images.isEmpty())
        assertTrue(rdl.sourceUrl.endsWith("/dumbbell-romanian-deadlift"))
    }

    private var writes = 0
    private val repository =
        object : TrainingRepositoryFake() {
            override suspend fun templates() = AppResult.Success(listOf(arms))

            override suspend fun plan() =
                AppResult.Success(
                    TrainingPlan(
                        true,
                        (0..6).map { TrainingPlanDay(it, if (it == 4) arms.id else null) },
                    )
                )

            override suspend fun savePlan(
                days: List<TrainingPlanDay>
            ): AppResult<List<TrainingPlanDay>> {
                writes++
                return AppResult.Success(days)
            }
        }

    private fun show(dark: Boolean = false, existingDraft: Boolean = false): TrainingViewModel {
        val model =
            TrainingViewModel(repository, RecordsRepositoryFake(), LocalDate.of(2026, 10, 22))
        if (existingDraft)
            model.add(CatalogExercise("pushup", "俯卧撑", "胸部", "徒手", "reps", emptyList(), ""))
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(dark) { TrainingScreen(state, model, {}, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        compose.onNodeWithText("每周计划").performClick()
        return model
    }

    private fun openArms() {
        compose.onNodeWithContentDescription("查看周四训练动作").performScrollTo().performClick()
        compose.onNodeWithText("周四训练计划").assertIsDisplayed()
    }

    @Test
    fun `weekly training card opens read only actions with sets rest and returns without changing draft`() {
        val model = show(existingDraft = true)
        val draft = model.state.value.exercises
        val plan = model.state.value.plan
        openArms()
        compose.onNodeWithText("1. 绳索臂屈伸").assertIsDisplayed()
        compose.onNodeWithText("预计 40 分钟 · 4 个动作 · 16 组").assertIsDisplayed()
        compose.onAllNodesWithText("4 组 · 组间休息 90 秒").assertCountEquals(4)
        compose.onAllNodesWithText("每组 12 次 · 0 kg").assertCountEquals(4)
        compose.onNodeWithText("4. 哑铃弯举").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("按此计划开始训练").assertIsDisplayed()
        capture("weekly-plan-arm-detail-390")
        assertEquals(draft, model.state.value.exercises)
        assertEquals(0, writes)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("每周计划").assertIsDisplayed()
        assertEquals(plan, model.state.value.plan)
        assertEquals(draft, model.state.value.exercises)
        compose.onNodeWithContentDescription("查看周六训练动作").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `starting from plan requires explicit replacement of existing draft and resets completed flags`() {
        val model = show(dark = true, existingDraft = true)
        val draft = model.state.value.exercises
        openArms()
        capture("weekly-plan-arm-detail-dark-320")
        compose.onNodeWithText("按此计划开始训练").performClick()
        compose.onNodeWithText("替换当前训练清单？").assertIsDisplayed()
        assertEquals(draft, model.state.value.exercises)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("周四训练计划").assertIsDisplayed()
        assertEquals(draft, model.state.value.exercises)
        compose.onNodeWithText("按此计划开始训练").performClick()
        compose.onNodeWithText("使用模板").performClick()
        compose.onNodeWithText("力量训练").assertIsDisplayed()
        assertEquals(arms.name, model.state.value.name)
        assertEquals(arms.exercises.map { it.name }, model.state.value.exercises.map { it.name })
        assertTrue(
            model.state.value.exercises.all { e ->
                e.restSeconds == 90 &&
                    e.sets.size == 4 &&
                    e.sets.all { it.reps == 12 && !it.completed }
            }
        )
        assertEquals(0, writes)
    }

    @Test
    fun `change template is separate from preview and only persists on save plan`() {
        val model = show()
        compose.onNodeWithContentDescription("更换周四模板").performScrollTo().performClick()
        compose.onAllNodesWithText("休息").filter(hasClickAction()).onLast().performClick()
        compose.onNodeWithText("每周计划").assertIsDisplayed()
        assertNull(model.state.value.plan.first { it.weekday == 4 }.templateId)
        compose.onNodeWithContentDescription("查看周四训练动作").assertDoesNotExist()
        assertTrue(model.state.value.exercises.isEmpty())
        assertEquals(0, writes)
        compose.onNodeWithText("保存每周计划").performScrollTo().performClick()
        compose.waitUntil { writes == 1 }
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/strength-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

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
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun imageDecoder() {
        coil3.SingletonImageLoader.setUnsafe(
            coil3.ImageLoader.Builder(compose.activity)
                .components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }
                .build()
        )
    }

    private val date = LocalDate.of(2026, 10, 19)

    private fun photo(): String {
        val file = File(compose.activity.cacheDir, "test-photo.jpg")
        compose.activity.resources.openRawResource(R.drawable.sport_walking).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return android.net.Uri.fromFile(file).toString()
    }

    @Test
    fun `upload does not save record explicit save retains uploaded photo on failure`() {
        var saved: PhotoInput? = null
        val image = photo()
        val model =
            RecordEditorViewModel(
                object : RecordsRepositoryFake() {
                    override suspend fun upload(bytes: ByteArray, mimeType: String) =
                        AppResult.Success(image)

                    override suspend fun savePhoto(input: PhotoInput): AppResult<PhotoEntry> {
                        saved = input
                        return AppResult.Failure(AppError.Network)
                    }
                },
                RecordKind.PHOTO,
                date,
                null,
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("保存照片").assertIsNotEnabled()
        compose.runOnIdle { model.upload(byteArrayOf(1), "image/jpeg") }
        compose.waitUntil { model.state.value.draft.imageUrl != null }
        assertNull(saved)
        compose.onNodeWithText("侧面").performScrollTo().performClick()
        compose.onNodeWithText("备注（选填）").performScrollTo().performTextInput("阶段记录")
        capture("photo-upload-390")
        compose.onNodeWithText("保存照片").performClick()
        compose.waitUntil { saved != null && !model.state.value.saving }
        assertEquals("SIDE", saved!!.type)
        assertTrue(saved!!.recordedAt.startsWith("2026-10-19"))
        assertEquals(image, saved!!.imageUrl)
        assertEquals("阶段记录", model.state.value.draft.note)
        compose.onNodeWithText("重新保存").assertIsEnabled()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow photo archive filters angles and opens real record`() {
        val image = photo()
        val items =
            listOf(
                PhotoEntry("front", "FRONT", image, "2026-10-19T10:00:00+08:00", null).item(),
                PhotoEntry("side", "SIDE", image, "2026-10-19T10:00:00+08:00", null).item(),
            )
        var opened: String? = null
        var more = false
        compose.setContent {
            FitTraceTheme(true) {
                PhotoArchiveScreen(
                    RecordsState(items, false, page = 1, total = 3),
                    YearMonth.of(2026, 10),
                    {},
                    {},
                    { more = true },
                    { opened = it.id },
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("侧面", useUnmergedTree = true).performClick()
        compose.onNodeWithText("10月19日 · 侧面").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("10月19日 · 正面").assertDoesNotExist()
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
        capture("photo-archive-dark-320")
        compose.onNodeWithText("10月19日 · 侧面").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.OnClick
        ) {
            it()
        }
        compose.waitUntil { opened != null }
        assertEquals("side", opened)
        compose.onNodeWithText("加载更多照片").performScrollTo().performClick()
        compose.waitUntil { more }
        assertTrue(more)
    }

    @Test
    fun `photo details confirm deletion and retain detail on failure`() {
        var deleted = false
        val image = photo()
        val model =
            RecordEditorViewModel(
                object : RecordsRepositoryFake() {
                    override suspend fun photo(id: String) =
                        AppResult.Success(
                            PhotoEntry(id, "FRONT", image, "2026-10-19T10:00:00+08:00", "阶段记录")
                        )

                    override suspend fun delete(kind: RecordKind, id: String): AppResult<Unit> {
                        deleted = true
                        return AppResult.Failure(AppError.Network)
                    }
                },
                RecordKind.PHOTO,
                date,
                "photo",
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        capture("photo-detail-390")
        compose.onNodeWithText("删除照片").performClick()
        assertFalse(deleted)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("删除照片").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil { deleted && !model.state.value.saving }
        assertFalse(model.state.value.saved)
        assertEquals(image, model.state.value.draft.imageUrl)
    }

    @Test
    fun `photo archive loading failure and empty are distinct`() {
        var state by mutableStateOf(RecordsState(error = "网络错误", loading = false))
        var retried = false
        compose.setContent {
            FitTraceTheme {
                PhotoArchiveScreen(
                    state,
                    YearMonth.of(2026, 10),
                    {},
                    { retried = true },
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("照片加载失败").assertIsDisplayed()
        compose.onNodeWithText("留下第一张身材照片").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()
        assertTrue(retried)
        compose.runOnIdle { state = RecordsState(loading = false) }
        compose.onNodeWithText("留下第一张身材照片").assertIsDisplayed()
        capture("photo-empty-390")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/photo-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

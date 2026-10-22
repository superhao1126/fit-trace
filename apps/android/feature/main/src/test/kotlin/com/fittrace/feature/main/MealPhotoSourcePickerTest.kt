package com.fittrace.feature.main

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.MediaStore
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.fittrace.core.designsystem.FitTraceTheme
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
class MealPhotoSourcePickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var request = 0
    private var launched: Intent? = null
    private var selected: Uri? = null
    private var error: String? = null
    private var visible by mutableStateOf(true)
    private val registry =
        object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                request = requestCode
                launched = contract.createIntent(compose.activity, input)
            }
        }

    private fun showPicker() {
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides
                    object : ActivityResultRegistryOwner {
                        override val activityResultRegistry = registry
                    }
            ) {
                FitTraceTheme {
                    MealPhotoSourcePicker(
                        visible,
                        { visible = false },
                        { selected = it },
                        { error = it },
                    )
                }
            }
        }
    }

    @Test
    fun `camera cancellation preserves photo then successful retry returns full image through restricted provider`() {
        selected = Uri.parse("content://existing/meal.jpg")
        showPicker()
        compose.onNodeWithText("拍照").assertIsDisplayed()
        compose.onNodeWithText("从相册选择").assertIsDisplayed()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/meal-screenshots/meal-photo-source-390.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("拍照").performClick()
        compose.runOnIdle {
            assertNull(error)
            registry.dispatchResult(request, Activity.RESULT_CANCELED, null)
            assertEquals(Uri.parse("content://existing/meal.jpg"), selected)
            assertTrue(
                File(compose.activity.cacheDir, "meal-camera").listFiles().orEmpty().isEmpty()
            )
            visible = true
        }
        compose.onNodeWithText("拍照").performClick()
        compose.runOnIdle {
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, launched!!.action)
            assertNull(error)
            val output = launched!!.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)!!
            assertEquals("content", output.scheme)
            assertTrue(output.path!!.startsWith("/meal_camera/"))
            val provider =
                compose.activity.packageManager.resolveContentProvider(output.authority!!, 0)!!
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)
            compose.activity.contentResolver.openOutputStream(output)!!.use {
                it.write(byteArrayOf(1, 2, 3))
            }
            assertEquals(Uri.parse("content://existing/meal.jpg"), selected)
            registry.dispatchResult(request, Activity.RESULT_OK, null)
            assertEquals(output, selected)
            assertArrayEquals(
                byteArrayOf(1, 2, 3),
                compose.activity.contentResolver.openInputStream(selected!!)!!.use {
                    it.readBytes()
                },
            )
            assertNull(error)
        }
    }

    @Test
    fun `album uses document picker with supported formats and cancellation preserves photo`() {
        selected = Uri.parse("content://existing/meal.jpg")
        showPicker()
        compose.onNodeWithText("从相册选择").performClick()
        compose.runOnIdle {
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched!!.action)
            assertArrayEquals(
                arrayOf("image/jpeg", "image/png", "image/webp"),
                launched!!.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
            )
            registry.dispatchResult(request, Activity.RESULT_CANCELED, null)
            assertEquals(Uri.parse("content://existing/meal.jpg"), selected)
        }
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("从相册选择").performClick()
        compose.runOnIdle {
            val albumImage = Uri.parse("content://album/picture.jpg")
            registry.dispatchResult(request, Activity.RESULT_OK, Intent().setData(albumImage))
            assertEquals(albumImage, selected)
            assertNull(error)
        }
    }
}

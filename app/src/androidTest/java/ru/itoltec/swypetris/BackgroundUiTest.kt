package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundUiTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun gesturesSaveAndRestoreCropWithoutChangingGameState() {
        val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        compose.runOnIdle { model.navigation.finishLaunchIntro(); model.navigation.settings() }
        compose.waitUntil(10000) { !model.background.busy }
        compose.onNodeWithTag("chooseBackground").performScrollTo().assertIsDisplayed()
        val source = fixture()
        try {
            compose.runOnIdle { model.background.choose(compose.activity.contentResolver, Uri.fromFile(source)) }
            compose.waitUntil(10000) { model.background.draft != null && !model.background.busy }
            val initial = model.background.draftCrop
            compose.onNodeWithTag("backgroundCropGestures").performTouchInput {
                pinch(Offset(width * .4f, height * .5f), Offset(width * .2f, height * .5f),
                    Offset(width * .6f, height * .5f), Offset(width * .8f, height * .5f))
            }
            compose.runOnIdle { assertTrue(model.background.draftCrop.zoom > initial.zoom) }
            val zoomed = model.background.draftCrop
            compose.onNodeWithTag("backgroundCropGestures").performTouchInput {
                swipe(Offset(width * .5f, height * .5f), Offset(width * .6f, height * .6f))
            }
            compose.runOnIdle { assertNotEquals(zoomed, model.background.draftCrop) }
            val chosen = model.background.draftCrop
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("backgroundCropPreview").assertIsDisplayed()
            val previewBounds = compose.onNodeWithTag("gridBackground").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { assertEquals(chosen, model.background.draftCrop) }
            compose.onNodeWithTag("saveBackground").performClick()
            compose.waitUntil(10000) { model.background.draft == null && !model.background.busy }
            compose.runOnIdle {
                assertTrue(model.background.enabled)
                assertEquals(chosen, model.background.crop)
                assertEquals(GameScreen.SETTINGS, model.screen)
                assertEquals(null, model.game)
            }
            assertTrue(source.delete())
            val restored = GameViewModel(ApplicationProvider.getApplicationContext<Application>(),
                null, { 1000L }, false)
            compose.waitUntil(10000) { !restored.background.busy }
            assertNotNull(restored.background.image)
            assertEquals(chosen, restored.background.crop)
            compose.onNodeWithTag("customBackgroundEnabled").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(false, model.background.enabled) }
            compose.onNodeWithTag("customBackgroundEnabled").performClick()
            compose.runOnIdle { assertTrue(model.background.enabled); assertEquals(chosen, model.background.crop) }
            compose.waitUntil(5000) { compose.activity.window.decorView.hasWindowFocus() }
            compose.runOnIdle { model.newGame() }
            compose.onNodeWithTag("board").assertIsDisplayed()
            compose.runOnIdle { assertEquals(GameScreen.PLAYING, model.screen) }
            assertViewportMatches(previewBounds)
            compose.runOnIdle { model.background.image?.file?.delete() }
        } finally { source.delete() }
    }

    @Test fun cancelAndFailedImportKeepPreviouslySavedBackground() {
        val model = ViewModelProvider(compose.activity)[GameViewModel::class.java]
        compose.runOnIdle { model.navigation.finishLaunchIntro(); model.navigation.settings() }
        compose.waitUntil(10000) { !model.background.busy }
        val source = fixture()
        try {
            compose.runOnIdle { model.background.choose(compose.activity.contentResolver, Uri.fromFile(source)) }
            compose.waitUntil(10000) { !model.background.busy && model.background.draft != null }
            compose.runOnIdle { model.background.save() }
            compose.waitUntil(10000) { !model.background.busy && model.background.draft == null }
            val saved = model.background.image
            compose.runOnIdle { model.background.choose(compose.activity.contentResolver, Uri.fromFile(source)) }
            compose.waitUntil(10000) { !model.background.busy && model.background.draft != null }
            compose.runOnIdle { assertEquals(BackgroundCrop(), model.background.draftCrop); model.background.cancel() }
            compose.onNodeWithTag("backgroundCropPreview").assertDoesNotExist()
            compose.runOnIdle { assertEquals(saved, model.background.image) }
            source.writeText("invalid image")
            compose.runOnIdle { model.background.choose(compose.activity.contentResolver, Uri.fromFile(source)) }
            compose.waitUntil(10000) { !model.background.busy && model.background.error != null }
            compose.runOnIdle { assertEquals(saved, model.background.image); saved?.file?.delete() }
        } finally { source.delete() }
    }

    private fun assertViewportMatches(previewBounds: androidx.compose.ui.geometry.Rect) {
        val diagnostics = File(compose.activity.getExternalFilesDir(null), "background-ui-geometry.txt")
        val initialBounds = compose.onNodeWithTag("gridBackground").fetchSemanticsNode().boundsInRoot
        val viewport = compose.activity.window.decorView
        diagnostics.writeText("Preview after recreation: $previewBounds\nGame: $initialBounds\n" +
            "Activity viewport: ${viewport.width}x${viewport.height}")
        // Insets are consumed during layout: wait for the Settings -> full-bleed layout transition.
        compose.waitUntil(5000) {
            val bounds = compose.onNodeWithTag("gridBackground").fetchSemanticsNode().boundsInRoot
            bounds.width == previewBounds.width && bounds.height == previewBounds.height
        }
        val boardBounds = compose.onNodeWithTag("gridBackground").fetchSemanticsNode().boundsInRoot
        assertEquals(previewBounds.width, boardBounds.width, .01f)
        assertEquals(previewBounds.height, boardBounds.height, .01f)
    }
    private fun fixture(): File {
        val source = File(compose.activity.cacheDir, "background-ui-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.CYAN)
        source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return source
    }
}

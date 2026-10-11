package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.height
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Проверяет сборку логотипа, пропуск, тишину и прозрачность на светлом и тёмном фоне. */
class LaunchIntroTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    /** Создаёт управляемую заставку без игровых таймеров и обращения к динамику или мотору. */
    private fun model(): GameViewModel = GameViewModel(ApplicationProvider.getApplicationContext(),
        null, { 1000L }, false, showLaunchIntro = true)

    /** Касание поверх будущей кнопки только пропускает заставку; возврат не повторяет её. */
    @Test fun skipConsumesTapAndDoesNotReplay() {
        val model = model()
        compose.mainClock.autoAdvance = false
        compose.setContent { SwypetrisApp(model) {} }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("launchIntro").assertIsDisplayed().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * .28f, height * .60f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("launchIntro").assertDoesNotExist()
        compose.onNodeWithTag("newGame").assertIsDisplayed()
        compose.runOnIdle {
            assertNull(model.game)
            assertEquals(GameScreen.MENU, model.screen)
            model.navigation.settings(); model.navigation.menu(); model.onBackground(); model.onForeground()
            assertFalse(model.launchIntroPending)
            assertTrue(model.results.isEmpty())
        }
    }

    /** «Назад» завершает заставку, сохраняя открытое меню. */
    @Test fun backSkipsWithoutLeavingMenu() {
        val model = model()
        compose.mainClock.autoAdvance = false
        compose.setContent { SwypetrisApp(model) {} }
        compose.mainClock.advanceTimeByFrame()
        Espresso.pressBack()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("newGame").assertIsDisplayed()
        compose.runOnIdle { assertFalse(model.launchIntroPending); assertNull(model.game) }
    }

    /** Отключённая системная анимация сразу показывает рабочие кнопки. */
    @Test fun disabledAnimationsShowMenuImmediately() {
        val model = model()
        compose.setContent {
            CompositionLocalProvider(LocalLaunchIntroAnimations provides false) { SwypetrisApp(model) {} }
        }
        compose.onNodeWithTag("launchIntro").assertDoesNotExist()
        compose.onNodeWithTag("newGame").assertIsDisplayed()
    }

    /** Фон не расходует время; собранный PNG совпадает с меню без изменения размера или позиции. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun stagedAnimationHasNoFinalJumpAndFitsLightCompactScreen() {
        val model = model()
        model.options.setPalette("github_light")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalMenuSkyAnimations provides false) {
                Box(Modifier.size(320.dp, 480.dp)) { SwypetrisApp(model) {} }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        advanceTo(model, 500)
        compose.runOnIdle {
            val before = model.launchIntroMillis
            model.onBackground(); model.advanceLaunchIntro(60000)
            assertEquals(before, model.launchIntroMillis)
            model.onForeground()
        }
        advanceTo(model, 1450)
        advanceTo(model, 2650)
        val assembled = compose.onNodeWithTag("gameLogo").captureToImage().asAndroidBitmap()
        val assembledBounds = compose.onNodeWithTag("gameLogo").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { model.navigation.finishLaunchIntro() }
        compose.mainClock.advanceTimeByFrame()
        val menu = compose.onNodeWithTag("gameLogo").captureToImage().asAndroidBitmap()
        assertEquals(assembledBounds, compose.onNodeWithTag("gameLogo").fetchSemanticsNode().boundsInRoot)
        assertEquals(assembled.width, menu.width)
        assertEquals(assembled.height, menu.height)
        var changed = 0
        var pixels = 0
        for (y in 0 until menu.height) {
            for (x in 0 until menu.width) {
                val a = assembled.getPixel(x, y)
                val b = menu.getPixel(x, y)
                // PixelCopy и аппаратные слои могут округлять цвет по-разному, сохраняя геометрию.
                val difference = maxOf(kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)))
                // The lower part of this capture includes the first menu row after the intro.
                // Compare the actual logo region, excluding those newly revealed buttons.
                val visibleLogo = y < menu.height * .88f &&
                    minOf(android.graphics.Color.red(a), android.graphics.Color.green(a),
                        android.graphics.Color.blue(a)) < 247
                if (visibleLogo && difference > 8) changed++
                pixels++
            }
        }
        assertTrue("Logo jumped: $changed / $pixels pixels differ beyond GPU rounding",
            changed <= pixels / 1000 + 1)
        listOf("newGame", "settings", "help", "results", "contacts", "exitGame").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        compose.runOnIdle { model.options.setPalette("monokai") }
        compose.mainClock.advanceTimeByFrame()
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun darkApproachEndsOnTheSameMenuComposition() {
        val model = model()
        model.options.setPalette("synthwave_84")
        compose.mainClock.autoAdvance = false
        compose.setContent { SwypetrisApp(model) {} }
        compose.mainClock.advanceTimeByFrame()
        advanceTo(model, 1000)
        advanceTo(model, 2000)
        advanceTo(model, 2650)
        val finalFrame = capture()
        compose.runOnIdle { model.navigation.finishLaunchIntro() }
        compose.mainClock.advanceTimeByFrame()
        val menuFrame = capture()
        assertEquals(finalFrame.width, menuFrame.width)
        assertEquals(finalFrame.height, menuFrame.height)
        compose.onNodeWithTag("gameLogo").assertIsDisplayed()
        compose.onNodeWithTag("newGame").assertIsDisplayed()
    }

    /** Оба изображения имеют настоящую прозрачность, включая пространство между полосами. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun transparentAssetsAndAdaptiveIconMasks() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (resource in listOf(R.drawable.swypetris_logo, R.drawable.swypetris_mark)) {
            val bitmap = BitmapFactory.decodeResource(app.resources, resource)
            assertTrue(bitmap.hasAlpha())
            assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.count { android.graphics.Color.alpha(it) == 0 } > pixels.size * .65)
        }
        val icon = app.getDrawable(R.drawable.ic_launcher)!!
        val roundIcon = app.getDrawable(R.drawable.ic_launcher_round)!!
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
            assertTrue(roundIcon is android.graphics.drawable.AdaptiveIconDrawable)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            assertNotNull((icon as android.graphics.drawable.AdaptiveIconDrawable).monochrome)
        }
    }

    /** Перемещает часы к нужному кадру, сохраняя отключённую автоматическую прокрутку времени. */
    private fun advanceTo(model: GameViewModel, elapsed: Long) {
        compose.runOnIdle { model.advanceLaunchIntro(elapsed - model.launchIntroMillis) }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Captures the actual Compose frame for automated comparisons. */
    @androidx.annotation.RequiresApi(26)
    private fun capture(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
}

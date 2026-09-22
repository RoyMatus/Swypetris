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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

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
            model.settings(); model.menu(); model.onBackground(); model.onForeground()
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
    @Test fun stagedAnimationHasNoFinalJumpAndFitsLightCompactScreen() {
        val model = model()
        model.setPalette("github_light")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) { SwypetrisApp(model) {} }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        screenshot("intro-0000-light.png")
        advanceTo(model, 500)
        screenshot("intro-0500-light.png")
        compose.runOnIdle {
            val before = model.launchIntroMillis
            model.onBackground(); model.advanceLaunchIntro(60000)
            assertEquals(before, model.launchIntroMillis)
            model.onForeground()
        }
        advanceTo(model, 1450)
        screenshot("intro-1450-light.png")
        advanceTo(model, 2650)
        val assembled = screenshot("intro-2650-light.png")
        compose.runOnIdle { model.finishLaunchIntro() }
        compose.mainClock.advanceTimeByFrame()
        val menu = screenshot("intro-menu-light.png")
        val bounds = compose.onNodeWithTag("gameLogo").fetchSemanticsNode().boundsInRoot
        var changed = 0
        var pixels = 0
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val a = assembled.getPixel(x, y)
                val b = menu.getPixel(x, y)
                // PixelCopy и аппаратные слои могут округлять цвет по-разному, сохраняя геометрию.
                val difference = maxOf(kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)))
                if (difference > 8) changed++
                pixels++
            }
        }
        assertEquals("Logo jumped: $changed / $pixels pixels differ beyond GPU rounding", 0, changed)
        listOf("newGame", "settings", "help", "results", "contacts", "exitGame").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        compose.runOnIdle { model.setPalette("monokai") }
        compose.mainClock.advanceTimeByFrame()
        screenshot("intro-menu-dark.png")
    }

    /** Оба изображения имеют настоящую прозрачность, включая пространство между полосами. */
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
        val bitmap = Bitmap.createBitmap(768, 256, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        repeat(3) { index ->
            canvas.save()
            canvas.translate(index * 256f, 0f)
            val path = android.graphics.Path().apply {
                when (index) {
                    0 -> addCircle(128f, 128f, 116f, android.graphics.Path.Direction.CW)
                    1 -> addRoundRect(12f, 12f, 244f, 244f, 48f, 48f, android.graphics.Path.Direction.CW)
                    else -> addRect(12f, 12f, 244f, 244f, android.graphics.Path.Direction.CW)
                }
            }
            canvas.clipPath(path)
            if (icon is android.graphics.drawable.AdaptiveIconDrawable) {
                icon.background.setBounds(0, 0, 256, 256); icon.background.draw(canvas)
                icon.foreground.setBounds(0, 0, 256, 256); icon.foreground.draw(canvas)
            } else { icon.setBounds(0, 0, 256, 256); icon.draw(canvas) }
            canvas.restore()
        }
        save(bitmap, "intro-icon-masks.png")
    }

    /** Перемещает часы к нужному кадру, сохраняя отключённую автоматическую прокрутку времени. */
    private fun advanceTo(model: GameViewModel, elapsed: Long) {
        compose.runOnIdle { model.advanceLaunchIntro(elapsed - model.launchIntroMillis) }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Сохраняет реальный кадр Compose для визуального контроля. */
    private fun screenshot(name: String): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap().also { save(it, name) }

    /** Записывает снимок в доступный adb каталог тестового приложения. */
    private fun save(bitmap: Bitmap, name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

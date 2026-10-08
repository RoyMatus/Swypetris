package ru.itoltec.swypetris

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.height
import androidx.compose.ui.test.left
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.right
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import ru.itoltec.swypetris.ui.theme.SwypetrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Проверяет новый бренд, контакты, коллекцию фруктов и возврат в меню без изменения данных игрока. */
class BrandNavigationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    /** Меню соблюдает порядок; результаты не содержат действий новой игры и возврата. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun menuOrderAndResultsBack() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
        compose.setContent { SwypetrisTheme(darkTheme = true, dynamicColor = false) { SwypetrisApp(model) {} } }
        compose.onNodeWithContentDescription("SWYPETRIS").assertIsDisplayed()
        compose.onNodeWithText("Рекорд:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Прежние правила:", substring = true).assertDoesNotExist()
        val expected = listOf("newGame", "settings", "help", "results", "contacts", "exitGame", "versionCheck")
        val observed = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .map { it.config.getOrElse(SemanticsProperties.TestTag) { "" } }.filter { it.isNotEmpty() }
        assertEquals(expected, observed)
        expected.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        screenshot("menu-brand.png")
        compose.runOnIdle { model.newGame(); model.navigation.menu() }
        compose.onNodeWithTag("resumeGame").assertIsDisplayed()
        val resume = compose.onNodeWithTag("resumeGame").fetchSemanticsNode().boundsInRoot
        val newGame = compose.onNodeWithTag("newGame").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot
        val exit = compose.onNodeWithTag("exitGame").fetchSemanticsNode().boundsInRoot
        assertEquals(resume.top, newGame.top)
        assertTrue(newGame.left < resume.left)
        assertTrue(kotlin.math.abs(resume.width - newGame.width) <= 1f)
        assertTrue(exit.width <= settings.width * 1.2f)
        compose.runOnIdle { model.navigation.showResults() }
        compose.onNodeWithTag("newGame").assertDoesNotExist()
        compose.onNodeWithTag("toMenu").assertDoesNotExist()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(GameScreen.MENU, model.screen) }
        compose.onNodeWithTag("resumeGame").assertIsDisplayed()
    }

    /** Все семь кнопок целиком видны без прокрутки на экране 320 × 480 dp при двойном шрифте. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun compactMenuFitsSmallScreenWithLargeFont() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), null, { 1000L }, false)
        model.newGame(); model.navigation.menu(); model.navigation.finishLaunchIntro()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SwypetrisTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.size(320.dp, 480.dp).testTag("viewport")) { SwypetrisApp(model) {} }
                }
            }
        }
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithTag("gameLogo").fetchSemanticsNode().boundsInRoot
        assertTrue(logo.top >= viewport.top)
        assertTrue("Logo too small on compact screen: $logo", logo.width >= viewport.width * .4f)
        for (tag in listOf("newGame", "resumeGame", "settings", "help", "results", "contacts", "exitGame")) {
            val node = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(node.top >= viewport.top && node.bottom <= viewport.bottom)
            assertTrue(node.left >= viewport.left && node.right <= viewport.right)
            assertTrue(node.width > node.height)
        }
        val version = compose.onNodeWithTag("versionCheck").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(version.left >= viewport.left && version.right <= viewport.right)
        assertTrue(version.top >= viewport.top && version.bottom <= viewport.bottom)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .assertCountEquals(0)
        screenshot("menu-compact-large-font.png")
    }

    /** Все фрукты и контакты помещаются при ширине 320 dp и двойном размере шрифта. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun fruitsAndContactsAtLargeFont() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = GameViewModel(application, null, { 1000L }, false)
        var fontScale by mutableFloatStateOf(2f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SwypetrisTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
                }
            }
        }
        compose.runOnIdle { model.navigation.help() }
        // Indexed lazy-list actions execute scrolling on the UI thread in Compose 1.7.
        for ((item, control) in listOf(
            2 to "Двигать фигуру",
            3 to "Поворачивать",
            4 to "Тап — на клетку вниз",
            5 to "Свайп вниз — бросок",
            6 to "Удержать и вверх — Hold",
            7 to "Заполняйте горизонтальные строки",
            8 to "«Запас» слева сверху",
            9 to "Следующая фигура показана спокойным контуром",
            10 to "Каждые ${GameRules.FRUIT_STEP} очков вы получаете следующий фрукт"
        )) {
            compose.onNodeWithTag("helpPage").performScrollToIndex(item)
            compose.onNodeWithText(control, substring = true).assertIsDisplayed()
        }
        compose.runOnIdle { fontScale = 1f }
        compose.onNodeWithTag("helpPage").performScrollToIndex(0)
        screenshot("help-controls.png")
        compose.runOnIdle { fontScale = 2f; model.navigation.contacts() }
        for ((index, contact) in DeveloperContact.entries.withIndex()) {
            compose.onNodeWithTag("contactsPage").performScrollToIndex(index + 1)
            compose.onNodeWithText(contact.address).assertIsDisplayed()
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(0)
        compose.runOnIdle { fontScale = 1f }
        compose.onNodeWithTag("contactsPage").performScrollToIndex(0)
        screenshot("contacts-brand.png")
        compose.runOnIdle { model.options.setPalette("github_light") }
        screenshot("contacts-light.png")
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(GameScreen.MENU, model.screen) }
    }

    /** Контакты создают правильные действия, а отсутствие почтового приложения не вызывает сбой. */
    @Test fun contactIntentsAndUnavailableHandler() {
        assertEquals(Intent.ACTION_SENDTO, DeveloperContact.EMAIL.intent().action)
        assertEquals("mailto:piligrim18@gmail.com", DeveloperContact.EMAIL.intent().dataString)
        assertEquals("https://t.me/RoyMatus", DeveloperContact.TELEGRAM.intent().dataString)
        assertEquals("https://itoltec.ru/", DeveloperContact.WEBSITE.intent().dataString)
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            /** Имитирует устройство без приложения, способного открыть ссылку. */
            override fun startActivity(intent: Intent?) { throw ActivityNotFoundException() }
        }
        assertFalse(openContact(context, DeveloperContact.EMAIL))
    }

    /** Сохраняет снимок для визуального контроля нового оформления. */
    @androidx.annotation.RequiresApi(26)
    private fun screenshot(name: String) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(application.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.bottom
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.left
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.top
import androidx.compose.ui.test.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Проверяет прослушивание, миграцию выбора и компактную колонку наград без пользовательских данных. */
class MusicSettingsFruitTest {
    @get:Rule(order=0) val storage = IsolatedStorageRule()
    @get:Rule(order=1) val compose = createComposeRule()

    /** Запоминает музыкальные команды без обращения к динамику. */
    private class Recorder : MusicPlayback {
        val selections = mutableListOf<MusicSelection>()
        val modes = mutableListOf<MusicMode>()
        /** Сохраняет запрос выбора, чтобы обнаружить лишний перезапуск. */
        override fun select(selection: MusicSelection) { selections += selection }
        /** Переводит разрешение в запрошенный режим. */
        override fun setPlaying(enabled: Boolean) = setMode(if (enabled) MusicMode.GAME else MusicMode.SILENT)
        /** Сохраняет режим без реального звука. */
        override fun setMode(next: MusicMode) { modes += next }
        /** Тестовый проигрыватель не владеет устройствами. */
        override fun release() = Unit
    }

    /** Выбор сразу звучит, одинаковый выбор не перезапускает, фон глушит звук, а меню включает свою тему. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun previewLifecyclePersistenceAndMigration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GameStorage.preferences(app).edit().putBoolean("music",false).commit()
        val recorder = Recorder()
        val initial = GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=10000)
        val model = GameViewModel(app,initial,{1000L},false,musicPlayback=recorder)
        assertEquals(MusicSelection.Off,model.musicSelection)
        model.settings()
        compose.setContent { SwypetrisApp(model) {} }
        compose.onNodeWithTag("musicPicker").performScrollTo().performClick()
        compose.onNodeWithTag("music_trepak").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(MusicSelection.Track(Song.TREPAK),model.musicSelection)
            assertEquals(MusicMode.MENU,recorder.modes.last())
            val count=recorder.selections.size
            model.chooseMusic(model.musicSelection)
            assertEquals(count,recorder.selections.size)
            model.onBackground(); assertEquals(MusicMode.SILENT,recorder.modes.last())
            model.onForeground(); assertEquals(MusicMode.MENU,recorder.modes.last())
            model.menu(); assertEquals(MusicMode.MENU,recorder.modes.last())
            model.resume(); assertEquals(MusicMode.GAME,recorder.modes.last())
            assertEquals(initial,model.game)
            assertEquals(model.musicSelection,GameViewModel(app,null,{1000L},false).musicSelection)
            model.settings(); model.chooseMusic(MusicSelection.ShuffleAll)
            assertEquals(MusicMode.MENU,recorder.modes.last())
            model.chooseMusic(MusicSelection.Off)
            assertEquals(MusicMode.SILENT,recorder.modes.last())
            assertFalse(GameViewModel(app,null,{1000L},false).musicEnabled)
            assertTrue(model.results.isEmpty())
        }
        screenshot("music-settings-off.png")
    }

    /** Музыка и сетка тем доступны при 320 dp и двойном шрифте. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun musicPickerLargeFontInLightAndDarkThemes() {
        val model=GameViewModel(ApplicationProvider.getApplicationContext(),null,{1000L},false)
        model.settings(); model.setPalette("github_light")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
            }
        }
        for (theme in listOf("github_light","classic")) {
            compose.runOnIdle { model.setPalette(theme) }
            compose.onNodeWithTag("musicPicker").performScrollTo().performClick()
            compose.onNodeWithTag("music_sugar_plum").performScrollTo().performClick()
            compose.onNodeWithTag("musicPicker").assertIsDisplayed()
            screenshot("music-large-$theme.png")
            compose.onNodeWithTag("paletteGrid").performScrollTo().assertIsDisplayed()
        }
    }

    /** Earned fruits stay below the right-aligned score and away from the spawn/preview area. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun earnedFruitsHaveNoPlaceholdersAndAvoidPreview() {
        var count by mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.size(320.dp,480.dp)) {
                val state=GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=count*10000)
                Board(state); GameHud(state)
            }
        }
        for (n in listOf(0,1,7,8,9,0)) {
            compose.runOnIdle { count=n }
            if (n==0) compose.onNodeWithTag("earnedFruits").assertDoesNotExist()
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            var previous: androidx.compose.ui.geometry.Rect? = null
            Fruit.entries.forEachIndexed { index,fruit ->
                val node=compose.onNodeWithTag("earnedFruit_${fruit.name}")
                if (index<n) {
                    node.assertIsDisplayed()
                    val bounds=node.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.left >= root.left)
                    assertTrue(bounds.left > root.left + root.width * .6f)
                    previous?.let { assertTrue(bounds.top >= it.bottom || bounds.left >= it.right) }
                    previous = bounds
                } else node.assertDoesNotExist()
            }
            val hold = compose.onNodeWithTag("holdPreview").fetchSemanticsNode().boundsInRoot
            if (n == 9) {
                compose.onNodeWithTag("earnedFruitCount_CHERRY").assertTextEquals("2 ×")
                Fruit.entries.drop(1).forEach { fruit ->
                    compose.onNodeWithTag("earnedFruitCount_${fruit.name}").assertDoesNotExist()
                }
            }
            if (n>0) {
                val fruits = compose.onNodeWithTag("earnedFruits").fetchSemanticsNode().boundsInRoot
                val score = compose.onNodeWithTag("score").fetchSemanticsNode().boundsInRoot
                assertTrue(fruits.top >= score.bottom)
                assertEquals(score.left, fruits.left, 1f)
                assertEquals(score.right, fruits.right, 1f)
                assertTrue(hold.right <= score.left + 1f)
                screenshot("fruits-row-$n.png")
            }
        }
    }

    /** Свайп у правого края зоны ввода по-прежнему передвигает фигуру. */
    @Test fun rightEdgeGestureAreaRemainsUsable() {
        val model=GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=10000),{1000L},false)
        compose.setContent { SwypetrisApp(model) {} }
        val initialX=model.game!!.active.x
        val fruit=compose.onNodeWithTag("earnedFruit_CHERRY").fetchSemanticsNode().boundsInRoot
        val area=compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("gameArea").performTouchInput {
            val from=Offset(area.width*.95f,fruit.center.y-area.top)
            swipe(from,from-Offset(area.width*.22f,0f),200)
        }
        compose.runOnIdle { assertTrue(model.game!!.active.x<initialX) }
    }

    /** Сохраняет снимок только в каталоге приложения тестового эмулятора. */
    @androidx.annotation.RequiresApi(26)
    private fun screenshot(name:String) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        java.io.File(app.getExternalFilesDir(null),name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}

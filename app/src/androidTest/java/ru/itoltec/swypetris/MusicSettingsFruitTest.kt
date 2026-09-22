package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
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

    /** Выбор сразу звучит, одинаковый выбор не перезапускает, фон и меню замораживают музыку. */
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
            assertEquals(MusicMode.GAME,recorder.modes.last())
            val count=recorder.selections.size
            model.chooseMusic(model.musicSelection)
            assertEquals(count,recorder.selections.size)
            model.onBackground(); assertEquals(MusicMode.SILENT,recorder.modes.last())
            model.onForeground(); assertEquals(MusicMode.GAME,recorder.modes.last())
            model.menu(); assertEquals(MusicMode.SILENT,recorder.modes.last())
            model.resume(); assertEquals(MusicMode.GAME,recorder.modes.last())
            assertEquals(initial,model.game)
            assertEquals(model.musicSelection,GameViewModel(app,null,{1000L},false).musicSelection)
            model.settings(); model.chooseMusic(MusicSelection.ShuffleAll)
            assertEquals(MusicMode.GAME,recorder.modes.last())
            model.chooseMusic(MusicSelection.Off)
            assertEquals(MusicMode.SILENT,recorder.modes.last())
            assertFalse(GameViewModel(app,null,{1000L},false).musicEnabled)
            assertTrue(model.results.isEmpty())
        }
        screenshot("music-settings-off.png")
    }

    /** Длинные названия в обоих выпадающих списках доступны при 320 dp и двойном шрифте. */
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
            compose.onNodeWithTag("palettePicker").performScrollTo().assertIsDisplayed()
        }
    }

    /** Только заработанные награды расположены сверху вниз, исчезая при новом круге. */
    @Test fun earnedFruitsHaveNoPlaceholdersAndUseRightEdge() {
        var count by mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.size(320.dp,480.dp)) {
                val state=GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=count*10000)
                Board(state); GameHud(state)
            }
        }
        for (n in listOf(0,1,7,8,0)) {
            compose.runOnIdle { count=n }
            if (n==0) compose.onNodeWithTag("earnedFruits").assertDoesNotExist()
            var previousBottom=-1f
            var right=-1f
            Fruit.entries.forEachIndexed { index,fruit ->
                val node=compose.onNodeWithTag("earnedFruit_${fruit.name}")
                if (index<n) {
                    node.assertIsDisplayed()
                    val bounds=node.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.top>previousBottom)
                    if (right>=0) assertEquals(right,bounds.right,.5f)
                    right=bounds.right; previousBottom=bounds.bottom
                } else node.assertDoesNotExist()
            }
            if (n>0) {
                val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
                val density=ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
                assertEquals(2*density,root.right-right,1f)
                screenshot("fruits-column-$n.png")
            }
        }
    }

    /** Свайп поверх значка награды передаётся полю и передвигает фигуру. */
    @Test fun fruitDoesNotConsumeGesture() {
        val model=GameViewModel(ApplicationProvider.getApplicationContext(),
            GameState(active=Piece(Tetromino.T),next=Tetromino.O,score=10000),{1000L},false)
        compose.setContent { SwypetrisApp(model) {} }
        val initialX=model.game!!.active.x
        val fruit=compose.onNodeWithTag("earnedFruit_CHERRY").fetchSemanticsNode().boundsInRoot
        val area=compose.onNodeWithTag("gameArea").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("gameArea").performTouchInput {
            val from=fruit.center-area.topLeft
            swipe(from,from-Offset(area.width*.22f,0f),200)
        }
        compose.runOnIdle { assertTrue(model.game!!.active.x<initialX) }
    }

    /** Сохраняет снимок только в каталоге приложения тестового эмулятора. */
    private fun screenshot(name:String) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        java.io.File(app.getExternalFilesDir(null),name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}

package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.bottom
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
import java.io.File
import android.graphics.Bitmap

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
        model.navigation.settings()
        compose.setContent { SwypetrisApp(model) {} }
        compose.onNodeWithTag("musicPicker").performScrollTo().performClick()
        compose.onNodeWithTag("music_trepak").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(MusicSelection.Track(Song.TREPAK),model.musicSelection)
            assertEquals(MusicMode.MENU,recorder.modes.last())
            val count=recorder.selections.size
            model.options.chooseMusic(model.musicSelection)
            assertEquals(count,recorder.selections.size)
            model.onBackground(); assertEquals(MusicMode.SILENT,recorder.modes.last())
            model.onForeground(); assertEquals(MusicMode.MENU,recorder.modes.last())
            model.navigation.menu(); assertEquals(MusicMode.MENU,recorder.modes.last())
            model.resume(); assertEquals(MusicMode.GAME,recorder.modes.last())
            assertEquals(initial,model.game)
            assertEquals(model.musicSelection,GameViewModel(app,null,{1000L},false).musicSelection)
            model.navigation.settings(); model.options.chooseMusic(MusicSelection.ShuffleAll)
            assertEquals(MusicMode.MENU,recorder.modes.last())
            model.options.chooseMusic(MusicSelection.Off)
            assertEquals(MusicMode.SILENT,recorder.modes.last())
            assertFalse(GameViewModel(app,null,{1000L},false).musicEnabled)
            assertTrue(model.results.isEmpty())
        }
    }

    /** Музыка и сетка тем доступны при 320 dp и двойном шрифте. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun musicPickerLargeFontInLightAndDarkThemes() {
        val model=GameViewModel(ApplicationProvider.getApplicationContext(),null,{1000L},false)
        model.navigation.settings(); model.options.setPalette("github_light")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { SwypetrisApp(model) {} }
            }
        }
        for (theme in listOf("github_light","classic")) {
            compose.runOnIdle { model.options.setPalette(theme) }
            compose.onNodeWithTag("musicPicker").performScrollTo().performClick()
            compose.onNodeWithTag("music_sugar_plum").performScrollTo().performClick()
            compose.onNodeWithTag("musicPicker").assertIsDisplayed()
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
                compose.onNodeWithTag("earnedFruitCount_CHERRY").assertTextEquals("2")
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
            }
        }
    }

    /** Odd rows reserve the right cell; badge growth cannot move or resize artwork. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun fruitColumnsAndBadgesStayStableAcrossCountsAndPortraitSizes() {
        var score by mutableIntStateOf(0)
        var width by mutableIntStateOf(240)
        var fontScale by mutableFloatStateOf(1f)
        var theme by mutableStateOf("classic")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalGamePalette provides GamePalettes.find(theme)) {
                Box(Modifier.size(width.dp, 640.dp)) {
                    GameHud(GameState(active = Piece(Tetromino.T), next = Tetromino.O, score = score))
                }
            }
        }
        for (w in listOf(240, 320)) for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { width = w; fontScale = scale }
            for (count in 0..8) {
                compose.runOnIdle { score = count * GameRules.FRUIT_STEP }
                if (count == 0) {
                    compose.onNodeWithTag("earnedFruits").assertDoesNotExist()
                    continue
                }
                val first = compose.onNodeWithTag("earnedFruitIcon_CHERRY").fetchSemanticsNode().boundsInRoot
                for (index in 0 until count) {
                    val fruit = Fruit.entries[index]
                    val icon = compose.onNodeWithTag("earnedFruitIcon_${fruit.name}")
                        .fetchSemanticsNode().boundsInRoot
                    if (index % 2 == 0) assertEquals(first.left, icon.left, 1f)
                    else assertTrue(icon.left > first.right)
                }
            }
            for (palette in listOf("classic", "solarized_light", "github_light")) {
                compose.runOnIdle { theme = palette; score = GameRules.FRUIT_STEP }
                var previousIcon: androidx.compose.ui.geometry.Rect? = null
                var previousDigits = 0
                for (quantity in listOf(1, 2, 3, 4, 9, 10, 13)) {
                    compose.runOnIdle { score = ((quantity - 1) * 8 + 1) * GameRules.FRUIT_STEP }
                    val icon = compose.onNodeWithTag("earnedFruitIcon_CHERRY")
                        .fetchSemanticsNode().boundsInRoot
                    // Score digit growth can widen the existing score lane; badge growth cannot.
                    val digits = score.toString().length
                    if (digits == previousDigits) assertEquals(previousIcon, icon)
                    previousIcon = icon
                    previousDigits = digits
                    val badge = compose.onNodeWithTag("earnedFruitCount_CHERRY")
                    if (quantity == 1) badge.assertDoesNotExist()
                    else {
                        badge.assertTextEquals(quantity.toString())
                            .assertContentDescriptionEquals("${Fruit.CHERRY.title}: $quantity")
                        val cell = compose.onNodeWithTag("earnedFruit_CHERRY").fetchSemanticsNode().boundsInRoot
                        val bounds = badge.fetchSemanticsNode().boundsInRoot
                        assertTrue(bounds.left >= cell.left && bounds.right <= cell.right + 1f)
                        assertTrue(bounds.top >= cell.top && bounds.bottom <= cell.bottom + 1f)
                    }
                }
            }
        }
    }

    /** Captures the real portrait renderer for both approved mockup scenarios. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun approvedFruitHudScenariosExportPortraitEvidence() {
        var state by mutableStateOf(GameState(active = Piece(Tetromino.S, y = 8), next = Tetromino.T,
            held = Tetromino.J, score = 54430))
        val model = GameViewModel(ApplicationProvider.getApplicationContext(), state, { 1000L }, false)
        compose.setContent { GameContent(model, state, 0.dp) }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val directory = File(app.getExternalFilesDir(null), "fruit-hud-259").apply { mkdirs() }
        for (value in listOf(54430, 254430)) {
            compose.runOnIdle { state = state.copy(score = value) }
            compose.onNodeWithTag("score").assertTextEquals(value.toString())
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(directory, "$value.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
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

}

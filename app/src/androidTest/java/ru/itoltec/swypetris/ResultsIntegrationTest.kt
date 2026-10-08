package ru.itoltec.swypetris

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ru.itoltec.swypetris.ui.theme.SwypetrisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет завершение партии, отдельную страницу, имя и сохранение, не изменяя данные игрока. */
@RunWith(AndroidJUnit4::class)
class ResultsIntegrationTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun savedNamePrefillsNextRecordAndSkippingKeepsIt() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = GameStorage.preferences(application)
        val board = List(BoardGeometry.TOTAL_ROWS) { MutableList<Tetromino?>(10) { null } }.also { it[BoardGeometry.row(0)][4] = Tetromino.Z }
        fun recordState(score: Int) = GameState(board = board, active = Piece(Tetromino.O, x = 0, y = 18),
            next = Tetromino.O, score = score)
        var model by mutableStateOf(GameViewModel(application, recordState(10000), { 1000L }, false))
        lateinit var autofillTree: AutofillTree
        compose.setContent {
            autofillTree = LocalAutofillTree.current
            SwypetrisApp(model) {}
        }
        compose.runOnIdle { model.command(GameCommand.HARD_DROP) }
        compose.runOnIdle {
            val nameNode = autofillTree.children.values.single { AutofillType.PersonFullName in it.autofillTypes }
            nameNode.onFill?.invoke("Мария")
        }
        compose.onNodeWithTag("recordName").assertTextContains("Мария")
        compose.onNodeWithTag("recordPage").performScrollToNode(hasTestTag("saveRecord"))
        compose.onNodeWithTag("saveRecord").performClick()
        compose.runOnIdle { assertEquals("Мария", preferences.getString("player_name", null)) }

        compose.runOnIdle {
            model = GameViewModel(application, recordState(20000), { 2000L }, false)
            model.command(GameCommand.HARD_DROP)
        }
        compose.onNodeWithTag("recordName").assertTextContains("Мария")
        compose.onNodeWithTag("recordPage").performScrollToNode(hasTestTag("skipRecord"))
        compose.onNodeWithTag("skipRecord").performClick()
        compose.runOnIdle {
            assertEquals("Мария", model.playerName)
            assertEquals("Мария", preferences.getString("player_name", null))
            assertEquals("Игрок", model.latestResult?.name)
        }
    }

    /** Результат сохраняется один раз, пауза исключена из времени, рекорд может получить имя. */
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    @Test fun gameOverStoresOneResultAndRecordName() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = GameStorage.preferences(application)
        val backup = preferences.all.toMap()
        try {
            preferences.edit().remove("rules_4_migrated").remove("record_v4").remove("record_v2").putInt("record", 4321).remove("results_v2").commit()
            val board = List(BoardGeometry.TOTAL_ROWS) { MutableList<Tetromino?>(10) { null } }
            board[BoardGeometry.row(0)][4] = Tetromino.Z
            var now = 1000L
            val state = GameState(board = board, active = Piece(Tetromino.O, x = 0, y = 18),
                next = Tetromino.O, score = 10000)
            val model = GameViewModel(application, state, { now }, false)
            var testDensity by mutableStateOf<Density?>(null)
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides (testDensity ?: LocalDensity.current)) {
                    SwypetrisTheme(darkTheme = true, dynamicColor = false) { SwypetrisApp(model) {} }
                }
            }
            compose.runOnIdle {
                assertEquals(4321, model.legacyRecord)
                assertEquals(0, model.record)
                now += 100
                model.pause()
                now += 5000
                model.resume()
                now += 200
                model.command(GameCommand.HARD_DROP)
                assertEquals(GameScreen.RECORD, model.screen)
                assertEquals(1, model.results.size)
                assertEquals(300L, model.results.single().durationMillis)
                assertTrue(model.requestRecordName)
                model.command(GameCommand.HARD_DROP)
                assertEquals(1, model.results.size)
            }
            compose.onNodeWithTag("recordPage").assertIsDisplayed()
            compose.onNodeWithTag("resultsPage").assertDoesNotExist()
            compose.onNodeWithTag("board").assertDoesNotExist()
            saveScreenshot("record.png")
            compose.runOnIdle { model.setPalette("github_light") }
            saveScreenshot("record-light.png")
            compose.runOnIdle { model.setPalette("classic") }
            compose.onNodeWithTag("recordName").performTextReplacement("  Алексей  ")
            compose.onNodeWithTag("recordPage").performScrollToNode(hasTestTag("saveRecord"))
            compose.onNodeWithText("Сохранить").performClick()
            compose.onNodeWithTag("resultsPage").assertIsDisplayed()
            compose.onNodeWithTag("recordName").assertDoesNotExist()
            compose.onNodeWithTag("newGame").assertDoesNotExist()
            compose.onNodeWithTag("toMenu").assertDoesNotExist()
            saveScreenshot("results.png")
            compose.runOnIdle {
                assertFalse(model.requestRecordName)
                assertEquals(GameScreen.GAME_OVER, model.screen)
                val restored = ResultStore(preferences).read().single()
                assertEquals("Алексей", restored.name)
                assertEquals(10000, restored.score)
                assertEquals(1, fruitCount(restored.score))
                val fresh = GameViewModel(application, null, { now }, false)
                assertEquals(10000, fresh.record)
                assertEquals(model.results, fresh.results)
                assertEquals(4321, fresh.legacyRecord)
            }
            // Узкий экран с крупным шрифтом и планшетная логическая ширина на том же устройстве.
            for ((density, label) in listOf(Density(3.375f, 1.5f) to "narrow", Density(1.35f, 1.2f) to "tablet")) {
                compose.runOnIdle { testDensity = density }
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
                compose.onNodeWithTag("resultsPage").performScrollToNode(hasText("Алексей"))
                val bounds = compose.onNodeWithText("Алексей").fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= root.left && bounds.right <= root.right)
                saveScreenshot("results-$label.png")
            }
            compose.runOnIdle { testDensity = null; model.setPalette("github_light") }
            compose.onNodeWithTag("resultsPage").performScrollToIndex(0)
            saveScreenshot("results-light.png")
            compose.runOnIdle {
                testDensity = null
                model.newGame()
                repeat(30) { model.command(GameCommand.HARD_DROP) }
                assertEquals(GameScreen.GAME_OVER, model.screen)
                assertFalse(model.requestRecordName)
                assertEquals(1, model.results.size)
                assertNotNull(model.latestResult)
            }
            compose.onNodeWithTag("recordPage").assertDoesNotExist()
            compose.onNodeWithTag("resultsPage").assertIsDisplayed()
            compose.onNodeWithTag("recordName").assertDoesNotExist()
            androidx.test.espresso.Espresso.pressBack()
            compose.runOnIdle { assertEquals(GameScreen.MENU, model.screen) }
        } finally {
            val editor = preferences.edit().clear()
            backup.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
            } }
            editor.commit()
        }
    }

    /** Сохраняет реальный рендер экрана для визуальной проверки после инструментальных тестов. */
    @androidx.annotation.RequiresApi(26)
    private fun saveScreenshot(name: String) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val file = java.io.File(application.getExternalFilesDir(null), name)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

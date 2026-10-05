package ru.itoltec.swypetris

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.itoltec.swypetris.ui.theme.SwypetrisTheme

class StartingLevelTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun selectionIsAccessibleAtLargeFontAndOnlyAffectsNewGames() {
        val model = GameViewModel(app, null, { 1000L }, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SwypetrisTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { SettingsScreen(model) }
                }
            }
        }
        compose.onNodeWithTag("startingLevel").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.runOnIdle {
            assertEquals(5, model.startingLevel)
            assertEquals(Difficulty.MEDIUM, model.difficulty)
            model.newGame()
            assertEquals(5, model.game!!.level)
            assertEquals(0, model.game!!.lines)
            model.pause()
            model.chooseStartingLevel(15)
            model.chooseDifficulty(Difficulty.HARD)
            model.resume()
            assertEquals(5, model.game!!.startingLevel)
            assertEquals(Difficulty.MEDIUM, model.game!!.difficulty)
            model.pause()
            val restored = GameViewModel(app, null, { 1000L }, false)
            assertEquals(15, restored.startingLevel)
            assertEquals(5, restored.game!!.level)
            assertEquals(5, restored.game!!.startingLevel)
            restored.newGame()
            assertEquals(15, restored.game!!.level)
            assertEquals(Difficulty.HARD, restored.game!!.difficulty)
            restored.pause()
        }
    }

    @Test fun sessionsRetainStartAndOldVersionFiveSessionsDefaultToOne() {
        val state = GameEngine().newGame(startingLevel = 5).copy(lines = 49)
        val session = GameSession("start-five", state, emptyList(), 100, 0, state.gravityMillis, 0)
        val encoded = SessionStore.encode(session)
        assertEquals(session, SessionStore.decode(encoded))
        val old = JSONObject(encoded).apply { remove("startingLevel") }
        val migrated = SessionStore.decode(old.toString())
        assertEquals(1, migrated.state.startingLevel)
        assertEquals(5, migrated.state.level)
        for (invalid in listOf(0, 16)) {
            try {
                SessionStore.decode(JSONObject(encoded).put("startingLevel", invalid).toString())
                fail("Invalid starting level accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

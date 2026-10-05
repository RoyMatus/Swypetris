package ru.itoltec.swypetris

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class AutomaticUpdatesTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val updatePreferences = object : ExternalResource() {
        private val keys = listOf("automatic", "automatic_choice", "last_check")
        private var saved: Map<String, Any?> = emptyMap()
        private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        override fun before() {
            val preferences = preferences()
            saved = preferences.all.filterKeys { it in keys }
            preferences.edit().remove("automatic").remove("automatic_choice")
                .putLong("last_check", System.currentTimeMillis()).commit()
        }
        override fun after() {
            val editor = preferences().edit()
            keys.forEach(editor::remove)
            saved.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            editor.commit()
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun automaticUpdatesRequireConsentAndSurviveActivityRecreation() {
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[GameViewModel::class.java].apply { finishLaunchIntro(); settings() }
        }
        compose.onNodeWithTag("automaticUpdates").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("manualUpdatesOnly").assertIsDisplayed().performClick()
        compose.onNodeWithTag("automaticUpdates").assertIsOff().performClick()
        compose.onNodeWithTag("allowAutomaticUpdates").assertIsDisplayed().performClick()
        compose.onNodeWithTag("automaticUpdates").assertIsOn()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("automaticUpdates").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("automaticUpdates").assertIsOff()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("automaticUpdates").performScrollTo().assertIsOff()
    }
}

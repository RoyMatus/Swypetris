package ru.itoltec.swypetris

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class AutomaticUpdatesTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    @get:Rule(order = 1) val legacyPreferences = object : ExternalResource() {
        private val keys = listOf("automatic", "automatic_choice", "last_check")
        private var saved: Map<String, Any?> = emptyMap()
        override fun before() {
            saved = preferences().all.filterKeys { it in keys }
            preferences().edit().putBoolean("automatic", true).putBoolean("automatic_choice", true)
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

    @Test fun removedSettingsDoNotReuseOldAutomaticInstallConsent() {
        compose.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[GameViewModel::class.java].apply {
                navigation.finishLaunchIntro()
                navigation.settings()
            }
        }
        compose.onNodeWithTag("automaticUpdates").assertDoesNotExist()
        compose.onNodeWithTag("checkUpdates").assertDoesNotExist()
        assertFalse(preferences().contains("automatic"))
        assertFalse(preferences().contains("automatic_choice"))
        compose.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[GameViewModel::class.java].navigation.menu()
        }
        compose.onNodeWithTag("versionCheck").assertIsDisplayed()
    }

    @Test fun availableUpdateStillWaitsForExplicitChoice() {
        compose.activityRule.scenario.onActivity { activity ->
            val updates = AppUpdates(activity)
            try {
                val update = AvailableUpdate(Long.MAX_VALUE, "9.9.9", "https://example.invalid/Swypetris.apk",
                    false, "0".repeat(64), 1)
                updates.showAvailable(update, manual = false)
                assertTrue(updates.notice is UpdateNotice.Available)
                assertNull(updates.delivery.notice)
                assertFalse(preferences().contains("automatic"))
            } finally { updates.close() }
        }
    }
}

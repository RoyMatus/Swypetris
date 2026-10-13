package ru.itoltec.swypetris

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class UpdateCheckTest {
    @get:Rule(order = 0) val storage = IsolatedStorageRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences by lazy { app.getSharedPreferences("update_policy_${java.util.UUID.randomUUID()}", 0) }
    private var now = 1_700_000_000_000L
    private var elapsed = now
    private val current = AvailableUpdate(BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME, null, false)

    private fun create(fetch: () -> AvailableUpdate?): AppUpdates =
        AppUpdates(compose.activity, preferences, { now }, { elapsed }, fetch)

    private fun waitForCheck(updates: AppUpdates, calls: AtomicInteger, count: Int) {
        compose.waitUntil(5000) { calls.get() == count && !checking(updates) }
    }

    private fun checking(updates: AppUpdates): Boolean {
        var value = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            value = AppUpdates::class.java.getDeclaredField("checking")
                .apply { isAccessible = true }.getBoolean(updates)
        }
        return value
    }

    @Test fun freshLaunchWithCurrentVersionDoesNotRequireUpdate() = assertFreshLaunchRemainsPlayable { current }

    @Test fun freshLaunchWithOlderPublishedVersionDoesNotRequireUpdate() =
        assertFreshLaunchRemainsPlayable { current.copy(versionCode = current.versionCode - 1) }

    @Test fun freshLaunchOfflineDoesNotRequireUpdate() =
        assertFreshLaunchRemainsPlayable { throw IOException("Controlled offline first launch") }

    @Test fun freshLaunchWithNewerVersionCanPostponeAndPlayWithoutDownload() =
        assertFreshLaunchRemainsPlayable(offered = true) {
            AvailableUpdate(current.versionCode + 1, "9.9.9", "https://example.invalid/Swypetris.apk",
                false, "0".repeat(64), 1)
        }

    private fun assertFreshLaunchRemainsPlayable(offered: Boolean = false, fetch: () -> AvailableUpdate?) {
        val calls = AtomicInteger()
        lateinit var updates: AppUpdates
        lateinit var model: GameViewModel
        compose.runOnIdle {
            model = GameViewModel(app, null, { now }, false)
            updates = create { calls.incrementAndGet(); fetch() }
        }
        try {
            compose.setContent { SwypetrisApp(model, updates) {} }
            compose.runOnIdle {
                updates.check(false)
            }
            waitForCheck(updates, calls, 1)
            if (offered) compose.onNodeWithTag("laterUpdate").assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertNull(updates.notice)
                assertNull(updates.delivery.notice)
            }
            compose.onNodeWithTag("newGame").assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(GameScreen.PLAYING, model.screen)
                assertNull(updates.delivery.notice)
            }
        } finally {
            compose.runOnIdle { model.pause(); updates.close(); preferences.edit().clear().commit() }
        }
    }

    @Test fun failedAutomaticAttemptIsPersistedAcrossRecreationButManualCanBypassInterval() {
        val calls = AtomicInteger()
        val fetch = { calls.incrementAndGet(); throw IOException("Controlled failure") }
        lateinit var updates: AppUpdates
        compose.runOnIdle { updates = create(fetch); updates.check(false) }
        try {
            waitForCheck(updates, calls, 1)
            compose.runOnIdle {
                assertNull(updates.notice)
                assertEquals(now, preferences.getLong("last_check", -1))
                repeat(3) { updates.check(false) }
                assertEquals(1, calls.get())
                updates.close()
            }
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                updates = create(fetch)
                updates.check(false)
                assertEquals(1, calls.get())
                updates.check(true)
            }
            waitForCheck(updates, calls, 2)
            compose.runOnIdle {
                assertEquals(UpdateNotice.Failed, updates.notice)
                updates.dismiss()
                now += AUTO_CHECK_INTERVAL_MS
                elapsed += AUTO_CHECK_INTERVAL_MS
                updates.check(false)
            }
            waitForCheck(updates, calls, 3)
            compose.runOnIdle {
                assertNull(updates.notice)
                now -= 24 * 60 * 60 * 1000L
                elapsed++
                updates.check(false)
            }
            compose.waitUntil(5000) { !checking(updates) }
            compose.runOnIdle {
                assertEquals(now + 24 * 60 * 60 * 1000L, preferences.getLong("last_check", -1))
                assertEquals(3, calls.get())
                now = preferences.getLong("last_check", -1) + AUTO_CHECK_INTERVAL_MS - 1
                elapsed += AUTO_CHECK_INTERVAL_MS - 2
                updates.check(false)
            }
            compose.waitUntil(5000) { !checking(updates) }
            compose.runOnIdle {
                assertEquals(3, calls.get())
                now++
                elapsed++
                updates.check(false)
            }
            waitForCheck(updates, calls, 4)
        } finally { compose.runOnIdle { updates.close(); preferences.edit().clear().commit() } }
    }

    @Test fun persistedCooldownShowsRetryTimeWithoutRequestingAndDoesNotBlockGameplay() {
        val calls = AtomicInteger()
        val retryAt = now + 120_000
        var restricted = true
        val fetch = {
            calls.incrementAndGet()
            if (restricted) throw UpdateRateLimitException(retryAt)
            current
        }
        lateinit var updates: AppUpdates
        lateinit var model: GameViewModel
        compose.runOnIdle { updates = create(fetch); updates.check(false) }
        try {
            waitForCheck(updates, calls, 1)
            compose.runOnIdle {
                assertNull(updates.notice)
                assertEquals(retryAt, preferences.getLong("retry_at", -1))
                updates.close()
            }
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                updates = create(fetch)
                updates.check(true)
                model = GameViewModel(app, null, { now }, false)
            }
            compose.waitUntil(5000) { updates.notice == UpdateNotice.RateLimited(retryAt) }
            assertEquals(1, calls.get())
            compose.setContent { SwypetrisApp(model, updates) {} }
            compose.onNodeWithText("Повторить можно после", substring = true).assertIsDisplayed()
            compose.onNodeWithText("время устройства", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Понятно").performClick()
            compose.onNodeWithTag("newGame").performClick()
            compose.runOnIdle {
                assertEquals(GameScreen.PLAYING, model.screen)
                model.navigation.menu()
                now = retryAt - 1
                elapsed = retryAt - 1
                updates.check(true)
                assertEquals(1, calls.get())
                now++
                elapsed++
                restricted = false
                updates.check(true)
            }
            waitForCheck(updates, calls, 2)
            compose.runOnIdle {
                assertEquals(UpdateNotice.Current, updates.notice)
                assertFalse(preferences.contains("retry_at"))
                updates.dismiss()
                updates.check(true)
            }
            waitForCheck(updates, calls, 3)
        } finally { compose.runOnIdle { updates.close(); preferences.edit().clear().commit() } }
    }

    @Test fun manualInterestJoinsAnInFlightAutomaticCheck() {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        lateinit var updates: AppUpdates
        compose.runOnIdle {
            updates = create {
                calls.incrementAndGet()
                entered.countDown()
                check(released.await(30, TimeUnit.SECONDS)) { "Controlled request was not released" }
                current
            }
            updates.check(false)
        }
        try {
            compose.waitUntil(5000) { entered.count == 0L }
            compose.runOnIdle { updates.check(true); updates.check(false) }
            assertEquals(1, calls.get())
            released.countDown()
            waitForCheck(updates, calls, 1)
            compose.runOnIdle {
                assertEquals(UpdateNotice.Current, updates.notice)
                assertFalse(preferences.contains("manual_attempts"))
            }
        } finally {
            released.countDown()
            compose.runOnIdle { updates.close(); preferences.edit().clear().commit() }
        }
    }

    @Test fun ordinaryForbiddenFailureDoesNotShowRateLimitNotice() {
        lateinit var updates: AppUpdates
        val calls = AtomicInteger()
        compose.runOnIdle {
            updates = create { calls.incrementAndGet(); throw IOException("HTTP 403 without rate-limit evidence") }
            updates.check(true)
        }
        try {
            waitForCheck(updates, calls, 1)
            compose.runOnIdle { assertEquals(UpdateNotice.Failed, updates.notice) }
        } finally { compose.runOnIdle { updates.close(); preferences.edit().clear().commit() } }
    }

    @Test fun failedManualAttemptUsesTheLastSlotAndLimitSurvivesRecreation() {
        val calls = AtomicInteger()
        val fetch = { calls.incrementAndGet(); throw IOException("Controlled failure") }
        val earlier = now - 1_000
        preferences.edit().putString("manual_attempts", List(MANUAL_CHECK_LIMIT - 1) { earlier }
            .joinToString(",")).commit()
        lateinit var updates: AppUpdates
        compose.runOnIdle { updates = create(fetch); updates.check(true) }
        try {
            waitForCheck(updates, calls, 1)
            compose.runOnIdle {
                assertEquals(MANUAL_CHECK_LIMIT,
                    parseManualAttempts(preferences.getString("manual_attempts", null))!!.size)
                updates.close()
            }
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                updates = create(fetch)
                updates.check(true)
            }
            compose.waitUntil(5000) { !checking(updates) }
            compose.runOnIdle {
                assertEquals(1, calls.get())
                assertEquals(UpdateNotice.ManualLimited(MANUAL_CHECK_WINDOW_MS - 1_000), updates.notice)
                now = earlier + MANUAL_CHECK_WINDOW_MS
                elapsed = now
                updates.check(true)
            }
            waitForCheck(updates, calls, 2)
        } finally { compose.runOnIdle { updates.close(); preferences.edit().clear().commit() } }
    }

    @Test fun repeatedSecondaryLimitsBackOffWithoutAnImmediateRetry() {
        val calls = AtomicInteger()
        val fetch = {
            calls.incrementAndGet()
            throw UpdateRateLimitException(now + 60_000, secondaryWithoutDeadline = true)
        }
        lateinit var updates: AppUpdates
        compose.runOnIdle { updates = create(fetch); updates.check(true) }
        try {
            waitForCheck(updates, calls, 1)
            compose.runOnIdle {
                assertEquals(now + 60_000, preferences.getLong("retry_at", -1))
                updates.check(true)
                assertEquals(1, calls.get())
                now += 60_000
                elapsed += 60_000
                updates.check(true)
            }
            waitForCheck(updates, calls, 2)
            compose.runOnIdle {
                assertEquals(now + 120_000, preferences.getLong("retry_at", -1))
                updates.check(true)
                assertEquals(2, calls.get())
            }
        } finally { compose.runOnIdle { updates.close(); preferences.edit().clear().commit() } }
    }
}

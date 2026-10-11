package ru.itoltec.swypetris

import android.app.Application
import android.content.SharedPreferences
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Durable update policy writes must never block the UI or lose joined manual interest. */
class UpdatePersistenceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val actual by lazy { app.getSharedPreferences("update_durability_${java.util.UUID.randomUUID()}", 0) }
    private val now = 1_700_000_000_000L
    private val current = AvailableUpdate(BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME, null, false)

    private class ControlledPreferences(private val actual: SharedPreferences, private val key: String,
        private val succeeds: Boolean = true, private val updateBackingOnFailure: Boolean = true)
        : SharedPreferences by actual {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val events = CopyOnWriteArrayList<String>()
        override fun edit(): SharedPreferences.Editor {
            val editor = actual.edit()
            return object : SharedPreferences.Editor by editor {
                private var selected = false
                override fun putLong(name: String?, value: Long): SharedPreferences.Editor {
                    selected = selected || name == key
                    editor.putLong(name, value)
                    return this
                }
                override fun remove(name: String?): SharedPreferences.Editor {
                    editor.remove(name)
                    return this
                }
                override fun commit(): Boolean {
                    if (!selected) return editor.commit()
                    check(Looper.myLooper() != Looper.getMainLooper()) { "Policy commit ran on main" }
                    events += "commit entered"
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Controlled commit was not released" }
                    // A false result must not be inferred successful from updated in-memory preferences.
                    val saved = if (succeeds || updateBackingOnFailure) editor.commit() else false
                    events += "commit returned"
                    return saved && succeeds
                }
            }
        }
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }
    }

    private fun checking(updates: AppUpdates): Boolean {
        var value = false
        onMain {
            value = AppUpdates::class.java.getDeclaredField("checking")
                .apply { isAccessible = true }.getBoolean(updates)
        }
        return value
    }

    private fun awaitCommit(preferences: ControlledPreferences) {
        compose.waitUntil(5000) { preferences.entered.count == 0L }
    }

    private fun awaitFinished(updates: AppUpdates) {
        compose.waitUntil(5000) { !checking(updates) }
    }

    private fun finish(updates: AppUpdates, preferences: ControlledPreferences) {
        preferences.release.countDown()
        awaitFinished(updates)
        onMain { updates.close() }
        actual.edit().clear().commit()
    }

    @Test fun automaticAttemptCommitsBeforeHttpAndManualJoinsWithoutBlockingMain() {
        val preferences = ControlledPreferences(actual, "last_check")
        val calls = AtomicInteger()
        lateinit var updates: AppUpdates
        onMain {
            updates = AppUpdates(compose.activity, preferences, { now }) {
                assertEquals(now, actual.getLong("last_check", -1))
                preferences.events += "fetch"
                calls.incrementAndGet()
                current
            }
            updates.check(false)
        }
        try {
            awaitCommit(preferences)
            onMain { updates.check(true); updates.check(false); assertNull(updates.notice) }
            assertEquals(0, calls.get())
            preferences.release.countDown()
            awaitFinished(updates)
            assertEquals(listOf("commit entered", "commit returned", "fetch"), preferences.events)
            assertEquals(1, calls.get())
            onMain { assertEquals(UpdateNotice.Current, updates.notice) }
        } finally { finish(updates, preferences) }
    }

    @Test fun failedAttemptCommitSuppressesHttpDespiteChangedPreferencesAndManualInterest() {
        for (updateBacking in listOf(false, true)) {
            verifyFailedPreflight(rebase = false, updateBacking = updateBacking)
        }
    }

    @Test fun failedRollbackCommitSuppressesHttpDespiteManualInterest() {
        verifyFailedPreflight(rebase = true)
    }

    private fun verifyFailedPreflight(rebase: Boolean, updateBacking: Boolean = true) {
        if (rebase) actual.edit().putLong("last_check", now + AUTO_CHECK_INTERVAL_MS).commit()
        val preferences = ControlledPreferences(actual, "last_check", succeeds = false,
            updateBackingOnFailure = updateBacking)
        val calls = AtomicInteger()
        lateinit var updates: AppUpdates
        onMain {
            updates = AppUpdates(compose.activity, preferences, { now }) { calls.incrementAndGet(); current }
            updates.check(false)
        }
        try {
            awaitCommit(preferences)
            onMain { updates.check(true) }
            preferences.release.countDown()
            awaitFinished(updates)
            val expected = if (updateBacking) now else if (rebase) now + AUTO_CHECK_INTERVAL_MS else -1L
            assertEquals(expected, actual.getLong("last_check", -1))
            assertEquals(0, calls.get())
            onMain { assertEquals(UpdateNotice.Failed, updates.notice) }
        } finally { finish(updates, preferences) }
    }

    @Test fun manualJoinedDuringRollbackBypassesAutomaticIntervalAfterSuccessfulRebase() {
        actual.edit().putLong("last_check", now + AUTO_CHECK_INTERVAL_MS).commit()
        val preferences = ControlledPreferences(actual, "last_check")
        val calls = AtomicInteger()
        lateinit var updates: AppUpdates
        onMain {
            updates = AppUpdates(compose.activity, preferences, { now }) { calls.incrementAndGet(); current }
            updates.check(false)
        }
        try {
            awaitCommit(preferences)
            onMain { updates.check(true) }
            preferences.release.countDown()
            awaitFinished(updates)
            assertEquals(1, calls.get())
            onMain { assertEquals(UpdateNotice.Current, updates.notice) }
        } finally { finish(updates, preferences) }
    }

    @Test fun manualJoinedDuringCooldownCommitGetsDurableRateLimitWithoutSecondRequest() {
        verifyCooldownCommit(succeeds = true)
    }

    @Test fun failedCooldownCommitShowsFailureEvenThoughPreferencesContainRetryTime() {
        verifyCooldownCommit(succeeds = false)
    }

    private fun verifyCooldownCommit(succeeds: Boolean) {
        val retryAt = now + 120_000
        val preferences = ControlledPreferences(actual, "retry_at", succeeds)
        val calls = AtomicInteger()
        lateinit var updates: AppUpdates
        onMain {
            updates = AppUpdates(compose.activity, preferences, { now }) {
                calls.incrementAndGet()
                throw UpdateRateLimitException(retryAt)
            }
            updates.check(false)
        }
        try {
            awaitCommit(preferences)
            assertTrue(checking(updates))
            onMain { updates.check(true); assertNull(updates.notice) }
            preferences.release.countDown()
            awaitFinished(updates)
            assertEquals(1, calls.get())
            assertEquals(retryAt, actual.getLong("retry_at", -1))
            onMain {
                assertEquals(if (succeeds) UpdateNotice.RateLimited(retryAt) else UpdateNotice.Failed, updates.notice)
            }
        } finally { finish(updates, preferences) }
    }

    @Test fun destructionDuringRollbackCommitDoesNotFetchOrResumeJoinedManualRequest() {
        actual.edit().putLong("last_check", now + AUTO_CHECK_INTERVAL_MS).commit()
        val preferences = ControlledPreferences(actual, "last_check")
        val calls = AtomicInteger()
        lateinit var updates: AppUpdates
        onMain {
            updates = AppUpdates(compose.activity, preferences, { now }) { calls.incrementAndGet(); current }
            updates.check(false)
        }
        try {
            awaitCommit(preferences)
            onMain { updates.check(true) }
            compose.activityRule.scenario.recreate()
            preferences.release.countDown()
            awaitFinished(updates)
            assertFalse(checking(updates))
            assertEquals(now, actual.getLong("last_check", -1))
            assertEquals(0, calls.get())
            onMain { assertNull(updates.notice) }
        } finally { finish(updates, preferences) }
    }
}

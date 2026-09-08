package com.tunnellight.stop_scroll.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tunnellight.stop_scroll.data.model.AppCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val trackedPackages: Set<String> = AppCatalog.defaultPackages,
    val dailyGoalMinutes: Int = 60,
    val limitAlertsEnabled: Boolean = true,
    val bingeAlertsEnabled: Boolean = true,
    val bingeMinutes: Int = 20,
    val retentionDays: Int = 365,
    val setupDismissed: Boolean = false,
    val dynamicColor: Boolean = false,
) {
    val dailyGoalMs: Long get() = dailyGoalMinutes * 60_000L
    val bingeMs: Long get() = bingeMinutes * 60_000L
}

/**
 * Preferences, exposed as [settings] so the accessibility service and the UI observe the same
 * source. Backed by [SharedPreferences] rather than DataStore because the accessibility
 * service needs a synchronous read on every scroll event, and a blocking read there would
 * sit on the main thread.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("stopscroll_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Synchronous snapshot, for the accessibility event hot path. */
    fun current(): Settings = _settings.value

    private fun read() = Settings(
        trackedPackages = prefs.getStringSet(KEY_TRACKED, null) ?: AppCatalog.defaultPackages,
        dailyGoalMinutes = prefs.getInt(KEY_GOAL, 60),
        limitAlertsEnabled = prefs.getBoolean(KEY_LIMIT_ALERTS, true),
        bingeAlertsEnabled = prefs.getBoolean(KEY_BINGE_ALERTS, true),
        bingeMinutes = prefs.getInt(KEY_BINGE_MINUTES, 20),
        retentionDays = prefs.getInt(KEY_RETENTION, 365),
        setupDismissed = prefs.getBoolean(KEY_SETUP_DISMISSED, false),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
    )

    fun setTracked(packageName: String, tracked: Boolean) {
        val next = _settings.value.trackedPackages.toMutableSet()
        if (tracked) next += packageName else next -= packageName
        prefs.edit { putStringSet(KEY_TRACKED, next) }
    }

    fun setDailyGoalMinutes(minutes: Int) =
        prefs.edit { putInt(KEY_GOAL, minutes.coerceIn(5, 12 * 60)) }

    fun setLimitAlerts(enabled: Boolean) = prefs.edit { putBoolean(KEY_LIMIT_ALERTS, enabled) }

    fun setBingeAlerts(enabled: Boolean) = prefs.edit { putBoolean(KEY_BINGE_ALERTS, enabled) }

    fun setBingeMinutes(minutes: Int) =
        prefs.edit { putInt(KEY_BINGE_MINUTES, minutes.coerceIn(5, 120)) }

    fun setRetentionDays(days: Int) = prefs.edit { putInt(KEY_RETENTION, days) }

    fun setSetupDismissed(dismissed: Boolean) =
        prefs.edit { putBoolean(KEY_SETUP_DISMISSED, dismissed) }

    fun setDynamicColor(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }

    // --- colour slots -------------------------------------------------------------------
    //
    // A package keeps the same categorical slot for as long as it is tracked, so filtering a
    // chart never repaints the series that survive. Slots are handed out on first sight and
    // freed when an app is untracked.

    fun colorSlotFor(packageName: String, activePackages: Set<String>): Int {
        val key = KEY_SLOT_PREFIX + packageName
        val existing = prefs.getInt(key, -1)
        if (existing >= 0) return existing
        val taken = activePackages
            .filter { it != packageName }
            .map { prefs.getInt(KEY_SLOT_PREFIX + it, -1) }
            .filter { it >= 0 }
            .toSet()
        val slot = (0 until SLOT_COUNT).firstOrNull { it !in taken } ?: OVERFLOW_SLOT
        if (slot != OVERFLOW_SLOT) prefs.edit { putInt(key, slot) }
        return slot
    }

    fun releaseColorSlot(packageName: String) =
        prefs.edit { remove(KEY_SLOT_PREFIX + packageName) }

    // --- one-shot alert bookkeeping ----------------------------------------------------

    fun limitAlertSentFor(dayKey: Int): Boolean = prefs.getInt(KEY_LAST_LIMIT_ALERT_DAY, 0) == dayKey

    fun markLimitAlertSent(dayKey: Int) =
        prefs.edit { putInt(KEY_LAST_LIMIT_ALERT_DAY, dayKey) }

    fun lastBingeAlertAt(): Long = prefs.getLong(KEY_LAST_BINGE_ALERT, 0L)

    fun markBingeAlertSent(at: Long) = prefs.edit { putLong(KEY_LAST_BINGE_ALERT, at) }

    private companion object {
        const val KEY_TRACKED = "tracked_packages"
        const val KEY_GOAL = "daily_goal_minutes"
        const val KEY_LIMIT_ALERTS = "limit_alerts"
        const val KEY_BINGE_ALERTS = "binge_alerts"
        const val KEY_BINGE_MINUTES = "binge_minutes"
        const val KEY_RETENTION = "retention_days"
        const val KEY_SETUP_DISMISSED = "setup_dismissed"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_SLOT_PREFIX = "color_slot_"
        const val KEY_LAST_LIMIT_ALERT_DAY = "last_limit_alert_day"
        const val KEY_LAST_BINGE_ALERT = "last_binge_alert"

        const val SLOT_COUNT = 8
        const val OVERFLOW_SLOT = -1
    }
}

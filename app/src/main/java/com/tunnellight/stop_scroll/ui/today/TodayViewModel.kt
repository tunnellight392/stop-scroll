package com.tunnellight.stop_scroll.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tunnellight.stop_scroll.AppContainer
import com.tunnellight.stop_scroll.analytics.Aggregator
import com.tunnellight.stop_scroll.analytics.PeriodMath
import com.tunnellight.stop_scroll.data.model.Comparison
import com.tunnellight.stop_scroll.data.model.ScrollSession
import com.tunnellight.stop_scroll.data.prefs.Settings
import com.tunnellight.stop_scroll.ui.clockFlow
import com.tunnellight.stop_scroll.ui.model.AppUsageRow
import com.tunnellight.stop_scroll.ui.model.SurfaceSlice
import com.tunnellight.stop_scroll.ui.toUsageRows
import com.tunnellight.stop_scroll.util.Permissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.ZoneId

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val trackingEnabled: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val totalMs: Long = 0L,
    val goalMs: Long = 60 * 60_000L,
    val vsYesterday: Comparison = Comparison(0L, 0L, "yesterday", true),
    val vsUsual: Comparison = Comparison(0L, 0L, "usual by now", true),
    val hourly: List<Long> = List(24) { 0L },
    val apps: List<AppUsageRow> = emptyList(),
    val sessionCount: Int = 0,
    val longestSessionMs: Long = 0L,
    val scrollPx: Long = 0L,
    val surfaces: List<SurfaceSlice> = emptyList(),
) {
    val goalFraction: Float get() = if (goalMs <= 0L) 0f else totalMs.toFloat() / goalMs
    val overGoal: Boolean get() = totalMs > goalMs
    val peakHour: Int? get() = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
}

private data class Inputs(val now: Long, val settings: Settings, val refreshKey: Int)

class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val refreshKey = MutableStateFlow(0)

    /** Called when the screen resumes, so a permission granted in Settings shows up at once. */
    fun refresh() {
        refreshKey.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> =
        combine(clockFlow(), container.settings.settings, refreshKey) { now, settings, key ->
            Inputs(now, settings, key)
        }
            .flatMapLatest { inputs ->
                val today = LocalDate.now(zone)
                // Eight days of sessions covers today plus the seven-day baseline.
                val from = PeriodMath.startOfDayMs(today.minusDays(7), zone)
                val to = PeriodMath.startOfDayMs(today.plusDays(1), zone)
                container.repository.observe(from, to).map { sessions ->
                    buildState(sessions, today, inputs)
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun buildState(
        sessions: List<ScrollSession>,
        today: LocalDate,
        inputs: Inputs,
    ): TodayUiState {
        val now = inputs.now
        val todayWindow = PeriodMath.day(today, zone)
        val elapsed = (now - todayWindow.startMs).coerceIn(0L, todayWindow.endMs - todayWindow.startMs)

        val stats = Aggregator.stats(
            sessions = sessions,
            from = todayWindow.startMs,
            to = todayWindow.endMs,
            dayEdges = PeriodMath.dayEdges(todayWindow, zone),
        )

        val yesterdayWindow = PeriodMath.day(today.minusDays(1), zone)
        val (currentRange, previousRange) =
            PeriodMath.comparisonWindows(todayWindow, yesterdayWindow, now)
        val todaySoFar = Aggregator.totalMs(sessions, currentRange.first, currentRange.last + 1)
        val yesterdaySoFar =
            Aggregator.totalMs(sessions, previousRange.first, previousRange.last + 1)

        // "Usual by now" averages the same slice of each of the previous seven days, so the
        // baseline is a like-for-like time of day rather than seven complete days.
        val baseline = (1..7).map { back ->
            val dayStart = PeriodMath.startOfDayMs(today.minusDays(back.toLong()), zone)
            Aggregator.totalMs(sessions, dayStart, dayStart + elapsed)
        }
        val usualMs = if (baseline.isEmpty()) 0L else baseline.sum() / baseline.size

        val foreground = runCatching {
            container.usageStats.foregroundMsByPackage(todayWindow.startMs, now)
        }.getOrDefault(emptyMap())

        val topApps = Aggregator.topAppsWithOther(stats.byApp)

        return TodayUiState(
            loading = false,
            date = today,
            trackingEnabled = Permissions.isTrackingServiceEnabled(container.appContext),
            hasUsageAccess = container.usageStats.hasPermission(),
            totalMs = stats.totalMs,
            goalMs = inputs.settings.dailyGoalMs,
            vsYesterday = Comparison(todaySoFar, yesterdaySoFar, "yesterday by now", true),
            vsUsual = Comparison(todaySoFar, usualMs, "your usual by now", true),
            hourly = Aggregator.spread(sessions, PeriodMath.hourEdges(today, zone)).toList(),
            apps = container.toUsageRows(topApps, stats.totalMs, foreground),
            sessionCount = stats.sessionCount,
            longestSessionMs = stats.longestSessionMs,
            scrollPx = stats.scrollPx,
            surfaces = stats.bySurface
                .map { SurfaceSlice(it.key, it.value) }
                .sortedByDescending { it.totalMs },
        )
    }
}

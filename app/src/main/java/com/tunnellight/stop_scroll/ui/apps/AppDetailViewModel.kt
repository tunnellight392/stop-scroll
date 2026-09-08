package com.tunnellight.stop_scroll.ui.apps

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tunnellight.stop_scroll.AppContainer
import com.tunnellight.stop_scroll.analytics.Aggregator
import com.tunnellight.stop_scroll.analytics.PeriodMath
import com.tunnellight.stop_scroll.data.model.Comparison
import com.tunnellight.stop_scroll.ui.clockFlow
import com.tunnellight.stop_scroll.ui.insights.DayValue
import com.tunnellight.stop_scroll.ui.model.SurfaceSlice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class AppDetailUiState(
    val loading: Boolean = true,
    val packageName: String = "",
    val label: String = "",
    val icon: ImageBitmap? = null,
    val colorSlot: Int = 0,
    val todayMs: Long = 0L,
    val weekMs: Long = 0L,
    val monthMs: Long = 0L,
    val foregroundTodayMs: Long = 0L,
    val daily: List<DayValue> = emptyList(),
    val sessionCount: Int = 0,
    val averageSessionMs: Long = 0L,
    val longestSessionMs: Long = 0L,
    val scrollPx: Long = 0L,
    val surfaces: List<SurfaceSlice> = emptyList(),
    val vsLastWeek: Comparison = Comparison(0L, 0L, "last week", true),
)

class AppDetailViewModel(
    private val container: AppContainer,
    private val packageName: String,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AppDetailUiState> = clockFlow(60_000L)
        .flatMapLatest { now ->
            val today = LocalDate.now(zone)
            val from = PeriodMath.startOfDayMs(today.minusDays(DAYS_SHOWN - 1L), zone)
                .coerceAtMost(PeriodMath.week(today.minusWeeks(1), zone).startMs)
            val to = PeriodMath.startOfDayMs(today.plusDays(1), zone)
            container.repository.observe(from, to).map { all ->
                build(all.filter { it.packageName == packageName }, today, now)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    private fun build(
        sessions: List<com.tunnellight.stop_scroll.data.model.ScrollSession>,
        today: LocalDate,
        now: Long,
    ): AppDetailUiState {
        val info = container.appInfo.info(packageName)
        val dayWindow = PeriodMath.day(today, zone)
        val week = PeriodMath.week(today, zone)
        val previousWeek = PeriodMath.week(today.minusWeeks(1), zone)
        val month = PeriodMath.month(today, zone)

        val start = today.minusDays(DAYS_SHOWN - 1L)
        val edges = PeriodMath.dayEdges(start, today, zone)
        val daily = Aggregator.spread(sessions, edges).toList().mapIndexed { index, ms ->
            DayValue(start.plusDays(index.toLong()), ms)
        }

        val (currentRange, previousRange) =
            PeriodMath.comparisonWindows(week, previousWeek, now)

        val stats = Aggregator.stats(sessions, month.startMs, month.endMs, PeriodMath.dayEdges(month, zone))

        return AppDetailUiState(
            loading = false,
            packageName = packageName,
            label = info.label,
            icon = info.icon,
            colorSlot = container.settings.colorSlotFor(
                packageName,
                container.settings.current().trackedPackages,
            ),
            todayMs = Aggregator.totalMs(sessions, dayWindow.startMs, dayWindow.endMs),
            weekMs = Aggregator.totalMs(sessions, week.startMs, week.endMs),
            monthMs = Aggregator.totalMs(sessions, month.startMs, month.endMs),
            foregroundTodayMs = runCatching {
                container.usageStats.foregroundMsByPackage(dayWindow.startMs, now)[packageName] ?: 0L
            }.getOrDefault(0L),
            daily = daily,
            sessionCount = stats.sessionCount,
            averageSessionMs = stats.averageSessionMs,
            longestSessionMs = stats.longestSessionMs,
            scrollPx = stats.scrollPx,
            surfaces = stats.bySurface
                .map { SurfaceSlice(it.key, it.value) }
                .sortedByDescending { it.totalMs },
            vsLastWeek = Comparison(
                currentMs = Aggregator.totalMs(sessions, currentRange.first, currentRange.last + 1),
                previousMs = Aggregator.totalMs(sessions, previousRange.first, previousRange.last + 1),
                previousLabel = "last week",
                partial = PeriodMath.isPartial(week, now),
            ),
        )
    }

    private companion object {
        const val DAYS_SHOWN = 30
    }
}

package com.tunnellight.stop_scroll.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tunnellight.stop_scroll.AppContainer
import com.tunnellight.stop_scroll.analytics.Aggregator
import com.tunnellight.stop_scroll.analytics.DateWindow
import com.tunnellight.stop_scroll.analytics.PeriodMath
import com.tunnellight.stop_scroll.data.model.Comparison
import com.tunnellight.stop_scroll.data.model.PeriodStats
import com.tunnellight.stop_scroll.data.model.ScrollSession
import com.tunnellight.stop_scroll.ui.clockFlow
import com.tunnellight.stop_scroll.ui.model.AppUsageRow
import com.tunnellight.stop_scroll.ui.model.SurfaceSlice
import com.tunnellight.stop_scroll.ui.toUsageRows
import com.tunnellight.stop_scroll.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class InsightsTab(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    TRENDS("Trends"),
}

data class DayValue(val date: LocalDate, val totalMs: Long)

/** One app's movement between two periods, used for the "what changed" list. */
data class AppDelta(val row: AppUsageRow, val previousMs: Long) {
    val deltaMs: Long get() = row.totalMs - previousMs
    val deltaFraction: Float?
        get() = if (previousMs <= 0L) null else deltaMs.toFloat() / previousMs
}

data class PeriodBreakdown(
    val rangeLabel: String,
    val comparisonLabel: String,
    val current: List<Long>,
    val previous: List<Long>,
    val labels: List<String>,
    val days: List<DayValue>,
    val comparison: Comparison,
    val stats: PeriodStats,
    val apps: List<AppUsageRow>,
    val appDeltas: List<AppDelta>,
    val surfaces: List<SurfaceSlice>,
    val partial: Boolean,
) {
    val dailyAverageMs: Long
        get() = if (stats.activeDays == 0) 0L else stats.totalMs / stats.activeDays
}

data class TrendBreakdown(
    val weekLabels: List<String>,
    val weekValues: List<Long>,
    val currentWeekIndex: Int,
    val monthLabels: List<String>,
    val monthValues: List<Long>,
    val currentMonthIndex: Int,
    val averageWeekMs: Long,
    val averageMonthMs: Long,
    val quietestWeekLabel: String?,
    val busiestWeekLabel: String?,
)

data class InsightsUiState(
    val loading: Boolean = true,
    val tab: InsightsTab = InsightsTab.WEEK,
    val breakdown: PeriodBreakdown? = null,
    val trends: TrendBreakdown? = null,
)

class InsightsViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val tab = MutableStateFlow(InsightsTab.WEEK)

    fun selectTab(next: InsightsTab) {
        tab.value = next
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsUiState> =
        combine(clockFlow(60_000L), tab) { now, selected -> now to selected }
            .flatMapLatest { (now, selected) ->
                val today = LocalDate.now(zone)
                val from = when (selected) {
                    InsightsTab.WEEK -> PeriodMath.week(today.minusWeeks(1), zone).startMs
                    InsightsTab.MONTH -> PeriodMath.month(today.minusMonths(1), zone).startMs
                    InsightsTab.TRENDS -> minOf(
                        PeriodMath.weekEdges(today, TREND_WEEKS, zone).first(),
                        PeriodMath.monthEdges(today, TREND_MONTHS, zone).first(),
                    )
                }
                val to = PeriodMath.startOfDayMs(today.plusDays(1), zone)
                container.repository.observe(from, to).map { sessions ->
                    when (selected) {
                        InsightsTab.WEEK -> InsightsUiState(
                            loading = false,
                            tab = selected,
                            breakdown = weekBreakdown(sessions, today, now),
                        )
                        InsightsTab.MONTH -> InsightsUiState(
                            loading = false,
                            tab = selected,
                            breakdown = monthBreakdown(sessions, today, now),
                        )
                        InsightsTab.TRENDS -> InsightsUiState(
                            loading = false,
                            tab = selected,
                            trends = trendBreakdown(sessions, today),
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    // --- week -------------------------------------------------------------------------------

    private fun weekBreakdown(
        sessions: List<ScrollSession>,
        today: LocalDate,
        now: Long,
    ): PeriodBreakdown {
        val current = PeriodMath.week(today, zone)
        val previous = PeriodMath.week(today.minusWeeks(1), zone)
        val labels = (0..6).map { Format.weekdayInitial(current.start.plusDays(it.toLong())) }
        return breakdown(
            sessions = sessions,
            current = current,
            previous = previous,
            now = now,
            labels = labels,
            rangeLabel = Format.dayRange(current.start, current.endInclusive),
            comparisonLabel = "last week",
        )
    }

    // --- month ------------------------------------------------------------------------------

    private fun monthBreakdown(
        sessions: List<ScrollSession>,
        today: LocalDate,
        now: Long,
    ): PeriodBreakdown {
        val current = PeriodMath.month(today, zone)
        val previous = PeriodMath.month(today.minusMonths(1), zone)
        val labels = (0 until current.dayCount).map { index ->
            val day = current.start.plusDays(index.toLong()).dayOfMonth
            if (day == 1 || day % 5 == 0) day.toString() else ""
        }
        return breakdown(
            sessions = sessions,
            current = current,
            previous = previous,
            now = now,
            labels = labels,
            rangeLabel = "${YearMonth.from(current.start).month.let { Format.monthShort(current.start) }} ${current.start.year}",
            comparisonLabel = "last month",
        )
    }

    private fun breakdown(
        sessions: List<ScrollSession>,
        current: DateWindow,
        previous: DateWindow,
        now: Long,
        labels: List<String>,
        rangeLabel: String,
        comparisonLabel: String,
    ): PeriodBreakdown {
        val currentEdges = PeriodMath.dayEdges(current, zone)
        val previousEdges = PeriodMath.dayEdges(previous, zone)
        val currentDaily = Aggregator.spread(sessions, currentEdges).toList()
        val previousDaily = Aggregator.spread(sessions, previousEdges).toList()

        val (currentRange, previousRange) = PeriodMath.comparisonWindows(current, previous, now)
        val currentSoFar = Aggregator.totalMs(sessions, currentRange.first, currentRange.last + 1)
        val previousSoFar = Aggregator.totalMs(sessions, previousRange.first, previousRange.last + 1)

        val stats = Aggregator.stats(sessions, current.startMs, current.endMs, currentEdges)
        val previousByApp = Aggregator
            .byApp(sessions, previousRange.first, previousRange.last + 1)
            .associate { it.packageName to it.totalMs }

        val rows = container.toUsageRows(Aggregator.topAppsWithOther(stats.byApp), stats.totalMs)
        val deltaRows = container
            .toUsageRows(
                Aggregator.byApp(sessions, currentRange.first, currentRange.last + 1),
                currentSoFar,
            )
            .map { AppDelta(it, previousByApp[it.packageName] ?: 0L) }
            .sortedByDescending { kotlin.math.abs(it.deltaMs) }
            .take(5)

        return PeriodBreakdown(
            rangeLabel = rangeLabel,
            comparisonLabel = comparisonLabel,
            current = currentDaily,
            previous = previousDaily.take(currentDaily.size).let { list ->
                // A 30-day month compared against a 31-day one needs padding so the two
                // series stay aligned slot for slot.
                list + List((currentDaily.size - list.size).coerceAtLeast(0)) { 0L }
            },
            labels = labels,
            days = currentDaily.mapIndexed { index, ms ->
                DayValue(current.start.plusDays(index.toLong()), ms)
            },
            comparison = Comparison(
                currentMs = currentSoFar,
                previousMs = previousSoFar,
                previousLabel = comparisonLabel,
                partial = PeriodMath.isPartial(current, now),
            ),
            stats = stats,
            apps = rows,
            appDeltas = deltaRows,
            surfaces = stats.bySurface
                .map { SurfaceSlice(it.key, it.value) }
                .sortedByDescending { it.totalMs },
            partial = PeriodMath.isPartial(current, now),
        )
    }

    // --- trends -----------------------------------------------------------------------------

    private fun trendBreakdown(sessions: List<ScrollSession>, today: LocalDate): TrendBreakdown {
        val weekEdges = PeriodMath.weekEdges(today, TREND_WEEKS, zone)
        val weekValues = Aggregator.spread(sessions, weekEdges).toList()
        val weekStart = PeriodMath.weekStart(today).minusWeeks((TREND_WEEKS - 1).toLong())
        val weekLabels = (0 until TREND_WEEKS).map {
            Format.shortDate(weekStart.plusWeeks(it.toLong()))
        }

        val monthEdges = PeriodMath.monthEdges(today, TREND_MONTHS, zone)
        val monthValues = Aggregator.spread(sessions, monthEdges).toList()
        val firstMonth = YearMonth.from(today).minusMonths((TREND_MONTHS - 1).toLong())
        val monthLabels = (0 until TREND_MONTHS).map {
            Format.monthShort(firstMonth.plusMonths(it.toLong()).atDay(1))
        }

        val completedWeeks = weekValues.dropLast(1)
        return TrendBreakdown(
            weekLabels = weekLabels,
            weekValues = weekValues,
            currentWeekIndex = TREND_WEEKS - 1,
            monthLabels = monthLabels,
            monthValues = monthValues,
            currentMonthIndex = TREND_MONTHS - 1,
            averageWeekMs = completedWeeks.filter { it > 0L }.average0(),
            averageMonthMs = monthValues.dropLast(1).filter { it > 0L }.average0(),
            quietestWeekLabel = completedWeeks
                .withIndex()
                .filter { it.value > 0L }
                .minByOrNull { it.value }
                ?.let { weekLabels[it.index] },
            busiestWeekLabel = completedWeeks
                .withIndex()
                .maxByOrNull { it.value }
                ?.takeIf { it.value > 0L }
                ?.let { weekLabels[it.index] },
        )
    }

    private fun List<Long>.average0(): Long = if (isEmpty()) 0L else sum() / size

    private companion object {
        const val TREND_WEEKS = 12
        const val TREND_MONTHS = 6
    }
}

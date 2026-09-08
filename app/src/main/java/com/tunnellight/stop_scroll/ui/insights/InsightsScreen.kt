package com.tunnellight.stop_scroll.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunnellight.stop_scroll.analytics.PeriodMath
import com.tunnellight.stop_scroll.ui.components.AppUsageRowItem
import com.tunnellight.stop_scroll.ui.components.ChartLegend
import com.tunnellight.stop_scroll.ui.components.ChartSeries
import com.tunnellight.stop_scroll.ui.components.ColumnChart
import com.tunnellight.stop_scroll.ui.components.DeltaChip
import com.tunnellight.stop_scroll.ui.components.EmptyState
import com.tunnellight.stop_scroll.ui.components.HeatCell
import com.tunnellight.stop_scroll.ui.components.LegendItem
import com.tunnellight.stop_scroll.ui.components.MonthHeatmap
import com.tunnellight.stop_scroll.ui.components.SectionCard
import com.tunnellight.stop_scroll.ui.components.ShareBar
import com.tunnellight.stop_scroll.ui.components.ShareSegment
import com.tunnellight.stop_scroll.ui.components.StatTile
import com.tunnellight.stop_scroll.ui.rememberContainerViewModel
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors
import com.tunnellight.stop_scroll.util.Format
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    contentPadding: PaddingValues,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberContainerViewModel { InsightsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chart = LocalChartColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Insights", style = MaterialTheme.typography.displaySmall)
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                InsightsTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = InsightsTab.entries.size,
                        ),
                    ) {
                        Text(tab.label)
                    }
                }
            }
        }

        when (state.tab) {
            InsightsTab.WEEK, InsightsTab.MONTH -> {
                val breakdown = state.breakdown
                if (breakdown == null) {
                    item { LoadingOrEmpty(state.loading) }
                } else {
                    periodSection(
                        breakdown = breakdown,
                        isMonth = state.tab == InsightsTab.MONTH,
                        onOpenApp = onOpenApp,
                    )
                }
            }

            InsightsTab.TRENDS -> {
                val trends = state.trends
                if (trends == null) {
                    item { LoadingOrEmpty(state.loading) }
                } else {
                    trendSection(trends)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.periodSection(
    breakdown: PeriodBreakdown,
    isMonth: Boolean,
    onOpenApp: (String) -> Unit,
) {
    item {
        SectionCard {
            Text(
                text = breakdown.rangeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Format.duration(breakdown.stats.totalMs),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            DeltaChip(breakdown.comparison)
            if (breakdown.partial) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Compared against the same stretch of ${breakdown.comparisonLabel}, " +
                        "so a part-finished ${if (isMonth) "month" else "week"} is not flattered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Daily average",
                value = Format.duration(breakdown.dailyAverageMs),
                modifier = Modifier.weight(1f),
                footnote = "${breakdown.stats.activeDays} active days",
            )
            StatTile(
                label = "Longest bout",
                value = Format.duration(breakdown.stats.longestSessionMs),
                modifier = Modifier.weight(1f),
                footnote = "${Format.count(breakdown.stats.sessionCount)} bouts",
            )
        }
    }

    item {
        val chart = LocalChartColors.current
        SectionCard(
            title = if (isMonth) "Day by day" else "This week vs last",
            subtitle = "Tap a bar for the exact figure",
        ) {
            // A month has 28-31 slots, which is already tight on a phone; doubling them up
            // for a comparison series would leave bars too thin to read. The month-over-month
            // figure is carried by the comparison card above instead.
            val series = if (isMonth) {
                listOf(ChartSeries("This month", chart.emphasis, breakdown.current))
            } else {
                listOf(
                    ChartSeries("This week", chart.emphasis, breakdown.current),
                    ChartSeries("Last week", chart.deEmphasis, breakdown.previous),
                )
            }
            ColumnChart(labels = breakdown.labels, series = series)
            if (series.size > 1) {
                Spacer(Modifier.height(12.dp))
                ChartLegend(
                    items = listOf(
                        LegendItem("This week", chart.emphasis),
                        LegendItem("Last week", chart.deEmphasis),
                    ),
                )
            }
        }
    }

    if (isMonth) {
        item {
            SectionCard(
                title = "Every day this month",
                subtitle = "Darker means more scrolling",
            ) {
                val firstDayOfWeek = PeriodMath.firstDayOfWeek()
                val first = breakdown.days.firstOrNull()?.date ?: LocalDate.now()
                val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
                val cells = List(lead) { HeatCell(null, 0L) } +
                    breakdown.days.map { HeatCell(it.date, it.totalMs) }
                val initials = (0..6).map { offset ->
                    firstDayOfWeek.plus(offset.toLong())
                        .getDisplayName(TextStyle.NARROW, Locale.getDefault())
                }
                var selected by remember { mutableStateOf<LocalDate?>(null) }
                MonthHeatmap(
                    cells = cells,
                    weekdayInitials = initials,
                    selected = selected,
                    onSelect = { selected = if (selected == it) null else it },
                )
                val chosen = selected
                if (chosen != null) {
                    Spacer(Modifier.height(12.dp))
                    val value = breakdown.days.firstOrNull { it.date == chosen }?.totalMs ?: 0L
                    Text(
                        text = "${Format.longDate(chosen)} — ${Format.duration(value)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    if (breakdown.appDeltas.isNotEmpty()) {
        item {
            SectionCard(
                title = "What changed",
                subtitle = "Biggest movers against ${breakdown.comparisonLabel}",
            ) {
                breakdown.appDeltas.forEach { delta ->
                    AppDeltaRow(delta)
                }
            }
        }
    }

    item {
        val chart = LocalChartColors.current
        SectionCard(title = "By app", subtitle = "Share of the period") {
            if (breakdown.apps.isEmpty()) {
                EmptyState(
                    title = "No scrolling recorded",
                    body = "Nothing was tracked in this period.",
                )
            } else {
                ShareBar(
                    segments = breakdown.apps.map { row ->
                        ShareSegment(row.label, row.totalMs, chart.forSlot(row.colorSlot))
                    },
                )
                Spacer(Modifier.height(14.dp))
                val maxMs = breakdown.apps.maxOf { it.totalMs }
                breakdown.apps.forEach { row ->
                    AppUsageRowItem(
                        row = row,
                        maxMs = maxMs,
                        onClick = { onOpenApp(row.packageName) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trendSection(trends: TrendBreakdown) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Average week",
                value = Format.duration(trends.averageWeekMs),
                modifier = Modifier.weight(1f),
                footnote = "completed weeks",
            )
            StatTile(
                label = "Average month",
                value = Format.duration(trends.averageMonthMs),
                modifier = Modifier.weight(1f),
                footnote = "completed months",
            )
        }
    }
    item {
        val chart = LocalChartColors.current
        SectionCard(
            title = "Last 12 weeks",
            subtitle = "This week in colour, earlier weeks for context",
        ) {
            ColumnChart(
                labels = trends.weekLabels,
                series = listOf(ChartSeries("Week", chart.emphasis, trends.weekValues)),
                emphasisIndex = trends.currentWeekIndex,
                labelEvery = 3,
                accessibleLabel = { index -> "week of ${trends.weekLabels[index]}" },
            )
            if (trends.busiestWeekLabel != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Busiest week began ${trends.busiestWeekLabel}" +
                        (trends.quietestWeekLabel?.let { ", quietest began $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item {
        val chart = LocalChartColors.current
        SectionCard(title = "Last 6 months", subtitle = "This month in colour") {
            ColumnChart(
                labels = trends.monthLabels,
                series = listOf(ChartSeries("Month", chart.emphasis, trends.monthValues)),
                emphasisIndex = trends.currentMonthIndex,
                accessibleLabel = { index -> trends.monthLabels[index] },
            )
        }
    }
}

@Composable
private fun AppDeltaRow(delta: AppDelta) {
    val chart = LocalChartColors.current
    val up = delta.deltaMs > 0
    val color = if (up) chart.critical else chart.good
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = delta.row.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (up) "▲" else "▼",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = Format.signedDuration(delta.deltaMs),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingOrEmpty(loading: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        EmptyState(
            title = if (loading) "Crunching the numbers" else "Nothing to show yet",
            body = if (loading) {
                "One moment."
            } else {
                "Scroll data will appear here as it is collected."
            },
        )
    }
}

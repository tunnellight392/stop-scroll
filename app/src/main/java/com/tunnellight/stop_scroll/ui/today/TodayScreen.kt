package com.tunnellight.stop_scroll.ui.today

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunnellight.stop_scroll.ui.components.AppUsageRowItem
import com.tunnellight.stop_scroll.ui.components.ChartSeries
import com.tunnellight.stop_scroll.ui.components.ColumnChart
import com.tunnellight.stop_scroll.ui.components.DeltaChip
import com.tunnellight.stop_scroll.ui.components.EmptyState
import com.tunnellight.stop_scroll.ui.components.GoalMeter
import com.tunnellight.stop_scroll.ui.components.SectionCard
import com.tunnellight.stop_scroll.ui.components.ShareBar
import com.tunnellight.stop_scroll.ui.components.ShareSegment
import com.tunnellight.stop_scroll.ui.components.StatTile
import com.tunnellight.stop_scroll.ui.rememberContainerViewModel
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors
import com.tunnellight.stop_scroll.util.Format
import com.tunnellight.stop_scroll.util.Permissions

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberContainerViewModel { TodayViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chart = LocalChartColors.current
    val yDpi = context.resources.displayMetrics.ydpi

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

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
            Column {
                Text("Today", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = Format.longDate(state.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.trackingEnabled) {
            item {
                TrackingOffCard(onEnable = { Permissions.openAccessibilitySettings(context) })
            }
        }

        item {
            SectionCard {
                // The hero figure: exactly one per screen, in the same sans as everything else.
                Text(
                    text = Format.duration(state.totalMs),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.apps.isEmpty()) {
                        "spent scrolling so far"
                    } else {
                        "spent scrolling across ${state.apps.size} " +
                            if (state.apps.size == 1) "app" else "apps"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))

                val goalColor = when {
                    state.goalFraction >= 1f -> chart.critical
                    state.goalFraction >= 0.75f -> chart.warning
                    else -> chart.good
                }
                GoalMeter(fraction = state.goalFraction, color = goalColor)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (state.overGoal) {
                            "Over your ${Format.duration(state.goalMs)} goal by " +
                                Format.duration(state.totalMs - state.goalMs)
                        } else {
                            "${Format.duration(state.goalMs - state.totalMs)} left of your " +
                                "${Format.duration(state.goalMs)} goal"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                // On a day with nothing recorded either side, both chips say the same thing,
                // so only one is shown. Once there is something to compare, each chip earns
                // its place: one is against yesterday, the other against the week's habit.
                val comparisons = listOf(state.vsYesterday, state.vsUsual)
                    .filter { it.hasAnythingToCompare }
                    .ifEmpty { listOf(state.vsYesterday) }
                comparisons.forEachIndexed { index, comparison ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    DeltaChip(comparison)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Scroll bouts",
                    value = Format.count(state.sessionCount),
                    modifier = Modifier.weight(1f),
                    footnote = "separate sittings",
                )
                StatTile(
                    label = "Longest bout",
                    value = Format.duration(state.longestSessionMs),
                    modifier = Modifier.weight(1f),
                    footnote = "without a break",
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Distance scrolled",
                    value = Format.distance(state.scrollPx, yDpi),
                    modifier = Modifier.weight(1f),
                    footnote = "approx. thumb travel",
                )
                StatTile(
                    label = "Peak hour",
                    value = state.peakHour?.let { Format.hourLabel(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                    footnote = state.peakHour?.let {
                        Format.durationCompact(state.hourly[it])
                    } ?: "nothing yet",
                )
            }
        }

        if (state.surfaces.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.surfaces.take(2).forEach { slice ->
                        StatTile(
                            label = slice.label,
                            value = Format.duration(slice.totalMs),
                            modifier = Modifier.weight(1f),
                            footnote = if (state.totalMs > 0) {
                                "${Format.percent(slice.totalMs.toFloat() / state.totalMs)} of today"
                            } else {
                                null
                            },
                        )
                    }
                    if (state.surfaces.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        item {
            SectionCard(
                title = "Where it went",
                subtitle = "Share of today's scroll time, largest first",
            ) {
                if (state.apps.isEmpty()) {
                    EmptyState(
                        title = "Nothing recorded yet",
                        body = "Once you scroll in a tracked app, it shows up here.",
                    )
                } else {
                    ShareBar(
                        segments = state.apps.map { row ->
                            ShareSegment(row.label, row.totalMs, chart.forSlot(row.colorSlot))
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    val maxMs = state.apps.maxOf { it.totalMs }
                    state.apps.forEach { row ->
                        AppUsageRowItem(
                            row = row,
                            maxMs = maxMs,
                            onClick = { onOpenApp(row.packageName) },
                        )
                    }
                    if (!state.hasUsageAccess) {
                        Spacer(Modifier.height(6.dp))
                        UsageAccessHint(onGrant = { Permissions.openUsageAccessSettings(context) })
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "When you scroll",
                subtitle = "Scroll time by hour of the day",
            ) {
                ColumnChart(
                    labels = (0..23).map { Format.hourLabel(it) },
                    series = listOf(
                        ChartSeries("Scroll time", chart.emphasis, state.hourly),
                    ),
                    labelEvery = 4,
                    accessibleLabel = { index -> "${Format.hourLabel(index)}–${Format.hourLabel((index + 1) % 24)}" },
                )
            }
        }
    }
}

@Composable
private fun TrackingOffCard(onEnable: () -> Unit) {
    val chart = LocalChartColors.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠", style = MaterialTheme.typography.titleMedium, color = chart.warning)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tracking is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "StopScroll needs its accessibility service switched on to notice " +
                    "scrolling. Nothing on your screen is read or stored.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onEnable) { Text("Turn on tracking") }
        }
    }
}

@Composable
private fun UsageAccessHint(onGrant: () -> Unit) {
    Column {
        Text(
            text = "Grant usage access to see what share of your time in each app was " +
                "spent scrolling.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGrant) { Text("Grant usage access") }
    }
}

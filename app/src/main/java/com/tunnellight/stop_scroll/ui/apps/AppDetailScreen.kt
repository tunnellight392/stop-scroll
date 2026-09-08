package com.tunnellight.stop_scroll.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.remember
import com.tunnellight.stop_scroll.R
import com.tunnellight.stop_scroll.appContainer
import com.tunnellight.stop_scroll.ui.components.AppIcon
import com.tunnellight.stop_scroll.ui.components.ChartSeries
import com.tunnellight.stop_scroll.ui.components.ColumnChart
import com.tunnellight.stop_scroll.ui.components.DeltaChip
import com.tunnellight.stop_scroll.ui.components.SectionCard
import com.tunnellight.stop_scroll.ui.components.StatTile
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors
import com.tunnellight.stop_scroll.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val factory = remember(container, packageName) {
        viewModelFactory { initializer { AppDetailViewModel(container, packageName) } }
    }
    val viewModel: AppDetailViewModel = viewModel(factory = factory, key = packageName)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chart = LocalChartColors.current
    val yDpi = LocalContext.current.resources.displayMetrics.ydpi

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        AppIcon(state.icon, state.label, chart.forSlot(state.colorSlot), 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = Format.duration(state.todayMs),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                text = "scrolled today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.foregroundTodayMs > 0L) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "You had the app open for " +
                                "${Format.duration(state.foregroundTodayMs)} today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    DeltaChip(state.vsLastWeek)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "This week",
                        value = Format.duration(state.weekMs),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "This month",
                        value = Format.duration(state.monthMs),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Average bout",
                        value = Format.duration(state.averageSessionMs),
                        modifier = Modifier.weight(1f),
                        footnote = "${Format.count(state.sessionCount)} this month",
                    )
                    StatTile(
                        label = "Distance",
                        value = Format.distance(state.scrollPx, yDpi),
                        modifier = Modifier.weight(1f),
                        footnote = "this month",
                    )
                }
            }

            if (state.surfaces.isNotEmpty()) {
                item {
                    SectionCard(title = "Where in the app", subtitle = "This month") {
                        state.surfaces.forEach { slice ->
                            Row(
                                Modifier.height(34.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Text(slice.label, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = Format.duration(slice.totalMs),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Last 30 days", subtitle = "Tap a bar for the exact figure") {
                    ColumnChart(
                        labels = state.daily.map {
                            if (it.date.dayOfMonth % 5 == 0) it.date.dayOfMonth.toString() else ""
                        },
                        series = listOf(
                            ChartSeries(
                                state.label.ifEmpty { "Scroll time" },
                                chart.forSlot(state.colorSlot),
                                state.daily.map { it.totalMs },
                            ),
                        ),
                        accessibleLabel = { index ->
                            state.daily.getOrNull(index)?.date?.let { Format.shortDate(it) } ?: ""
                        },
                    )
                }
            }
        }
    }
}

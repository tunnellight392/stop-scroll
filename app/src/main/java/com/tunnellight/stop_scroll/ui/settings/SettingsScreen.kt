package com.tunnellight.stop_scroll.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunnellight.stop_scroll.ui.components.SectionCard
import com.tunnellight.stop_scroll.ui.rememberContainerViewModel
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors
import com.tunnellight.stop_scroll.util.Format
import com.tunnellight.stop_scroll.util.Permissions
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val viewModel = rememberContainerViewModel { SettingsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chart = LocalChartColors.current
    var confirmClear by remember { mutableStateOf(false) }

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
        item { Text("Settings", style = MaterialTheme.typography.displaySmall) }

        item {
            SectionCard(title = "Permissions") {
                PermissionRow(
                    title = "Scroll tracking",
                    granted = state.trackingEnabled,
                    grantedText = "On — measuring ${state.trackedCount} apps",
                    deniedText = "Off — StopScroll cannot measure anything",
                    actionLabel = if (state.trackingEnabled) "Manage" else "Turn on",
                    onAction = { Permissions.openAccessibilitySettings(context) },
                    goodColor = chart.good,
                    badColor = chart.critical,
                )
                Spacer(Modifier.height(16.dp))
                PermissionRow(
                    title = "Usage access",
                    granted = state.usageAccessGranted,
                    grantedText = "On — scroll time is shown against total app time",
                    deniedText = "Optional — enables the \"share of app time\" figures",
                    actionLabel = if (state.usageAccessGranted) "Manage" else "Grant",
                    onAction = { Permissions.openUsageAccessSettings(context) },
                    goodColor = chart.good,
                    badColor = chart.warning,
                )
                Spacer(Modifier.height(16.dp))
                PermissionRow(
                    title = "Notifications",
                    granted = true,
                    grantedText = "Used only for the nudges you switch on below",
                    deniedText = "",
                    actionLabel = "Manage",
                    onAction = { Permissions.openNotificationSettings(context) },
                    goodColor = chart.good,
                    badColor = chart.warning,
                )
            }
        }

        item {
            SectionCard(
                title = "Daily goal",
                subtitle = "What counts as a good day",
            ) {
                Text(
                    text = Format.duration(state.settings.dailyGoalMs),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Slider(
                    value = state.settings.dailyGoalMinutes.toFloat(),
                    onValueChange = { viewModel.setDailyGoalMinutes(it.roundToInt()) },
                    valueRange = 10f..300f,
                )
                Text(
                    text = "10 minutes to 5 hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Nudges") {
                SwitchRow(
                    title = "Daily goal alert",
                    subtitle = "One notification when you pass the goal",
                    checked = state.settings.limitAlertsEnabled,
                    onChange = viewModel::setLimitAlerts,
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    title = "Long session alert",
                    subtitle = "When a single bout runs past " +
                        Format.duration(state.settings.bingeMs),
                    checked = state.settings.bingeAlertsEnabled,
                    onChange = viewModel::setBingeAlerts,
                )
                if (state.settings.bingeAlertsEnabled) {
                    Slider(
                        value = state.settings.bingeMinutes.toFloat(),
                        onValueChange = { viewModel.setBingeMinutes(it.roundToInt()) },
                        valueRange = 5f..90f,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Appearance") {
                SwitchRow(
                    title = "Match my wallpaper",
                    subtitle = "Use Material You colours for the interface. Charts keep their " +
                        "own palette, which is tuned for colour-blind readability.",
                    checked = state.settings.dynamicColor,
                    onChange = viewModel::setDynamicColor,
                )
            }
        }

        item {
            SectionCard(
                title = "Data",
                subtitle = state.firstRecordedDay?.let {
                    "Recording since ${Format.shortDate(it)}"
                } ?: "Nothing recorded yet",
            ) {
                Text("Keep history for", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30 days", 90 to "90 days", 365 to "1 year", 0 to "Forever")
                        .forEach { (days, label) ->
                            FilterChip(
                                selected = state.settings.retentionDays == days,
                                onClick = { viewModel.setRetentionDays(days) },
                                label = { Text(label) },
                            )
                        }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { confirmClear = true }) {
                    Text("Delete all scroll history")
                }
            }
        }

        item {
            SectionCard(title = "How this works") {
                Text(
                    text = "StopScroll watches the apps you pick and times how long each bout " +
                        "in a feed lasts. It never reads, stores or sends what is on your " +
                        "screen — only which app was in front, which kind of feed was open, " +
                        "and how far a list scrolled. Everything stays on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ordinary feeds report their scrolling, so they are timed by the " +
                        "scrolling itself. Short-video feeds — Shorts, Reels, TikTok — report " +
                        "none: their players are not exposed as scrollable to Android, so no " +
                        "swipe is ever announced. There, time is counted while the feed is on " +
                        "screen instead, which is why those bouts show no scroll distance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all scroll history?") },
            text = { Text("Every recorded session is removed from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        confirmClear = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    actionLabel: String,
    onAction: () -> Unit,
    goodColor: androidx.compose.ui.graphics.Color,
    badColor: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (granted) "●" else "○",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) goodColor else badColor,
                )
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = if (granted) grantedText else deniedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        } else {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

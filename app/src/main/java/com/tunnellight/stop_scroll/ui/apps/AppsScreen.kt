package com.tunnellight.stop_scroll.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tunnellight.stop_scroll.ui.components.AppIcon
import com.tunnellight.stop_scroll.ui.components.EmptyState
import com.tunnellight.stop_scroll.ui.components.SectionCard
import com.tunnellight.stop_scroll.ui.rememberContainerViewModel
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors
import com.tunnellight.stop_scroll.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    contentPadding: PaddingValues,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberContainerViewModel { AppsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                Text("Apps", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = "Only the apps switched on here are ever observed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Tracked", subtitle = "Tap an app for its own breakdown") {
                if (state.tracked.isEmpty()) {
                    EmptyState(
                        title = "Nothing is being tracked",
                        body = "Add an app below to start measuring.",
                    )
                } else {
                    state.tracked.forEach { app ->
                        AppToggleRow(
                            app = app,
                            onToggle = { viewModel.setTracked(app.packageName, it) },
                            onClick = { onOpenApp(app.packageName) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.openPicker()
                        pickerOpen = true
                    },
                ) {
                    Text("Add another app")
                }
            }
        }

        if (state.suggested.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Suggested",
                    subtitle = "Social apps on this phone that are not being tracked",
                ) {
                    state.suggested.forEach { app ->
                        AppToggleRow(
                            app = app,
                            onToggle = { viewModel.setTracked(app.packageName, it) },
                            onClick = null,
                        )
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Add an app", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = picker.query,
                    onValueChange = viewModel::setPickerQuery,
                    label = { Text("Search installed apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (picker.loading) {
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(picker.visible, key = { it.packageName }) { app ->
                            AppToggleRow(
                                app = app.copy(
                                    tracked = app.packageName in state.tracked.map { it.packageName },
                                ),
                                onToggle = { viewModel.setTracked(app.packageName, it) },
                                onClick = null,
                                showTimes = false,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { pickerOpen = false }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun AppToggleRow(
    app: AppListItem,
    onToggle: (Boolean) -> Unit,
    onClick: (() -> Unit)?,
    showTimes: Boolean = true,
) {
    val chart = LocalChartColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, app.label, chart.forSlot(app.colorSlot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = if (!showTimes) {
                app.packageName
            } else {
                buildString {
                    when {
                        app.todayMs > 0L -> append(
                            "${Format.duration(app.todayMs)} today · " +
                                "${Format.duration(app.weekMs)} this week",
                        )
                        app.weekMs > 0L -> append("${Format.duration(app.weekMs)} this week")
                        else -> append("No scrolling recorded yet")
                    }
                    // An uninstalled app keeps its history, so the figures still matter — the
                    // note goes after them rather than replacing them.
                    if (!app.installed) append(" · not installed")
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = app.tracked, onCheckedChange = onToggle)
    }
}

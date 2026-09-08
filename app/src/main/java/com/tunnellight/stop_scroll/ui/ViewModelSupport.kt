package com.tunnellight.stop_scroll.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tunnellight.stop_scroll.AppContainer
import com.tunnellight.stop_scroll.analytics.Aggregator
import com.tunnellight.stop_scroll.appContainer
import com.tunnellight.stop_scroll.data.model.AppTotal
import com.tunnellight.stop_scroll.ui.model.AppUsageRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Builds a view model that needs the app container, without dragging in a DI framework. */
@Composable
inline fun <reified VM : ViewModel> rememberContainerViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.appContainer
    val factory = remember(container) {
        viewModelFactory { initializer { create(container) } }
    }
    return viewModel(factory = factory)
}

/**
 * Re-emits on an interval so that "now"-relative figures (today so far, this week so far)
 * stay honest while the screen is open.
 */
fun clockFlow(intervalMs: Long = 30_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(intervalMs)
    }
}

/**
 * Turns per-app totals into rows the UI can render, resolving labels, icons and the stable
 * palette slot each package was assigned.
 */
fun AppContainer.toUsageRows(
    totals: List<AppTotal>,
    totalMs: Long,
    foreground: Map<String, Long> = emptyMap(),
): List<AppUsageRow> {
    val tracked = settings.current().trackedPackages
    return totals.map { total ->
        val isOther = total.packageName == Aggregator.OTHER_PACKAGE
        val info = appInfo.info(total.packageName)
        AppUsageRow(
            packageName = total.packageName,
            label = info.label,
            icon = info.icon,
            totalMs = total.totalMs,
            share = total.shareOf(totalMs),
            colorSlot = if (isOther) -1 else settings.colorSlotFor(total.packageName, tracked),
            sessions = total.sessions,
            foregroundMs = if (isOther) 0L else foreground[total.packageName] ?: 0L,
        )
    }
}

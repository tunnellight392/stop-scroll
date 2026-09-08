package com.tunnellight.stop_scroll.ui.apps

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tunnellight.stop_scroll.AppContainer
import com.tunnellight.stop_scroll.analytics.Aggregator
import com.tunnellight.stop_scroll.analytics.PeriodMath
import com.tunnellight.stop_scroll.data.model.AppCatalog
import com.tunnellight.stop_scroll.ui.clockFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class AppListItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val tracked: Boolean,
    val installed: Boolean,
    val todayMs: Long,
    val weekMs: Long,
    val colorSlot: Int,
    val isKnownSocialApp: Boolean,
)

data class AppsUiState(
    val loading: Boolean = true,
    val tracked: List<AppListItem> = emptyList(),
    val suggested: List<AppListItem> = emptyList(),
)

data class PickerState(
    val loading: Boolean = false,
    val query: String = "",
    val apps: List<AppListItem> = emptyList(),
) {
    val visible: List<AppListItem>
        get() = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
}

class AppsViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _picker = MutableStateFlow(PickerState())
    val picker: StateFlow<PickerState> = _picker.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AppsUiState> =
        combine(clockFlow(60_000L), container.settings.settings) { now, settings ->
            now to settings.trackedPackages
        }
            .flatMapLatest { (_, tracked) ->
                val today = LocalDate.now(zone)
                val week = PeriodMath.week(today, zone)
                val from = minOf(week.startMs, PeriodMath.startOfDayMs(today, zone))
                val to = PeriodMath.startOfDayMs(today.plusDays(1), zone)
                container.repository.observe(from, to).map { sessions ->
                    val dayWindow = PeriodMath.day(today, zone)
                    val todayTotals = Aggregator
                        .byApp(sessions, dayWindow.startMs, dayWindow.endMs)
                        .associate { it.packageName to it.totalMs }
                    val weekTotals = Aggregator
                        .byApp(sessions, week.startMs, week.endMs)
                        .associate { it.packageName to it.totalMs }

                    // An app that has since been uninstalled stays tracked (and keeps its
                    // history) but would only be clutter in this list.
                    val trackedItems = tracked
                        .filter {
                            container.appInfo.isInstalled(it) ||
                                todayTotals.containsKey(it) ||
                                weekTotals.containsKey(it)
                        }
                        .map { pkg -> item(pkg, tracked, todayTotals, weekTotals) }
                        .sortedWith(
                            compareByDescending<AppListItem> { it.todayMs }
                                .thenByDescending { it.weekMs }
                                .thenBy { it.label.lowercase() },
                        )

                    val suggestions = AppCatalog.apps
                        .map { it.packageName }
                        .distinct()
                        .filter { it !in tracked && container.appInfo.isInstalled(it) }
                        .map { pkg -> item(pkg, tracked, todayTotals, weekTotals) }

                    AppsUiState(loading = false, tracked = trackedItems, suggested = suggestions)
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private fun item(
        packageName: String,
        tracked: Set<String>,
        todayTotals: Map<String, Long>,
        weekTotals: Map<String, Long>,
    ): AppListItem {
        val info = container.appInfo.info(packageName)
        return AppListItem(
            packageName = packageName,
            label = info.label,
            icon = info.icon,
            tracked = packageName in tracked,
            installed = info.installed,
            todayMs = todayTotals[packageName] ?: 0L,
            weekMs = weekTotals[packageName] ?: 0L,
            colorSlot = container.settings.colorSlotFor(packageName, tracked),
            isKnownSocialApp = AppCatalog.find(packageName) != null,
        )
    }

    fun setTracked(packageName: String, tracked: Boolean) {
        container.settings.setTracked(packageName, tracked)
        // Freeing the palette slot lets a later app take it, rather than the slot list
        // silently filling up with apps that are no longer being measured.
        if (!tracked) container.settings.releaseColorSlot(packageName)
    }

    fun openPicker() {
        if (_picker.value.apps.isNotEmpty()) return
        _picker.value = _picker.value.copy(loading = true)
        viewModelScope.launch(Dispatchers.Default) {
            val tracked = container.settings.current().trackedPackages
            val apps = container.appInfo.launchableApps().map { info ->
                AppListItem(
                    packageName = info.packageName,
                    label = info.label,
                    icon = info.icon,
                    tracked = info.packageName in tracked,
                    installed = true,
                    todayMs = 0L,
                    weekMs = 0L,
                    colorSlot = -1,
                    isKnownSocialApp = AppCatalog.find(info.packageName) != null,
                )
            }
            _picker.value = PickerState(loading = false, query = "", apps = apps)
        }
    }

    fun setPickerQuery(query: String) {
        _picker.value = _picker.value.copy(query = query)
    }
}

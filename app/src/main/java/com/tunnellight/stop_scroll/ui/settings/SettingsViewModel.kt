package com.tunnellight.stop_scroll.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tunnellight.stop_scroll.AppContainer
import com.tunnellight.stop_scroll.data.prefs.Settings
import com.tunnellight.stop_scroll.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val settings: Settings = Settings(),
    val trackingEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val trackedCount: Int = 0,
    val firstRecordedDay: LocalDate? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val refreshKey = MutableStateFlow(0)
    private val firstDay = MutableStateFlow<LocalDate?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            firstDay.value = container.repository.earliestDay()
        }
    }

    fun refresh() {
        refreshKey.value += 1
    }

    val state: StateFlow<SettingsUiState> =
        combine(container.settings.settings, refreshKey, firstDay) { settings, _, first ->
            SettingsUiState(
                settings = settings,
                trackingEnabled = Permissions.isTrackingServiceEnabled(container.appContext),
                usageAccessGranted = container.usageStats.hasPermission(),
                trackedCount = settings.trackedPackages.size,
                firstRecordedDay = first,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDailyGoalMinutes(minutes: Int) = container.settings.setDailyGoalMinutes(minutes)

    fun setLimitAlerts(enabled: Boolean) = container.settings.setLimitAlerts(enabled)

    fun setBingeAlerts(enabled: Boolean) = container.settings.setBingeAlerts(enabled)

    fun setBingeMinutes(minutes: Int) = container.settings.setBingeMinutes(minutes)

    fun setRetentionDays(days: Int) = container.settings.setRetentionDays(days)

    fun setDynamicColor(enabled: Boolean) = container.settings.setDynamicColor(enabled)

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            container.repository.clearAll()
            firstDay.value = null
        }
    }
}

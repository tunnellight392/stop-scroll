package com.tunnellight.stop_scroll

import android.app.Application
import android.content.Context
import com.tunnellight.stop_scroll.data.db.StopScrollDatabase
import com.tunnellight.stop_scroll.data.prefs.SettingsStore
import com.tunnellight.stop_scroll.data.repo.ScrollRepository
import com.tunnellight.stop_scroll.data.usage.UsageStatsCollector
import com.tunnellight.stop_scroll.service.ScrollNotifier
import com.tunnellight.stop_scroll.util.AppInfoProvider

/**
 * Hand-rolled dependency container. The graph is small and has exactly one consumer set
 * (a handful of view models plus the accessibility service), so a DI framework would cost
 * more in build machinery than it saves.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: StopScrollDatabase by lazy { StopScrollDatabase.build(appContext) }
    val repository: ScrollRepository by lazy { ScrollRepository(database.sessions()) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val usageStats: UsageStatsCollector by lazy { UsageStatsCollector(appContext) }
    val appInfo: AppInfoProvider by lazy { AppInfoProvider(appContext) }
    val notifier: ScrollNotifier by lazy { ScrollNotifier(appContext) }
}

class StopScrollApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannels()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as StopScrollApplication).container

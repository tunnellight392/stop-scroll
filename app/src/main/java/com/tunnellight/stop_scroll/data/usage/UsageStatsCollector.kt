package com.tunnellight.stop_scroll.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Foreground time per app, read from the platform usage-stats service.
 *
 * This is the denominator for the headline number: StopScroll measures time spent *scrolling*,
 * and comparing it against total time in the app is what turns "42 minutes on Instagram" into
 * "31 of your 42 Instagram minutes were spent scrolling". The permission is optional, so every
 * caller has to cope with an empty map.
 */
class UsageStatsCollector(private val context: Context) {

    private val usageStats: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Usage access is an app-op rather than a runtime permission, and inspecting the op is
     * still the only way to ask whether it has been granted — the platform offers no
     * replacement, so the deprecation is suppressed rather than worked around. An empty
     * result from [queryEvents] would be ambiguous: it also means "nothing happened".
     */
    @Suppress("DEPRECATION")
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Milliseconds each package spent resumed in the window. Built from the event stream
     * rather than [UsageStatsManager.queryUsageStats] because the bucketed stats snap to
     * daily boundaries and cannot answer "since 9am".
     */
    fun foregroundMsByPackage(from: Long, to: Long): Map<String, Long> {
        val manager = usageStats ?: return emptyMap()
        if (!hasPermission() || to <= from) return emptyMap()

        val totals = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val events = try {
            manager.queryEvents(from, to)
        } catch (_: SecurityException) {
            return emptyMap()
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    val start = resumedAt.remove(pkg) ?: continue
                    val slice = (event.timeStamp - maxOf(start, from)).coerceAtLeast(0L)
                    totals[pkg] = (totals[pkg] ?: 0L) + slice
                }
            }
        }
        // Anything still resumed when the window ends is credited up to the window edge.
        for ((pkg, start) in resumedAt) {
            val slice = (to - maxOf(start, from)).coerceAtLeast(0L)
            totals[pkg] = (totals[pkg] ?: 0L) + slice
        }
        return totals
    }
}

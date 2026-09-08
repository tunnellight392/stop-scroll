package com.tunnellight.stop_scroll.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.tunnellight.stop_scroll.StopScrollApplication
import com.tunnellight.stop_scroll.analytics.PeriodMath
import com.tunnellight.stop_scroll.data.model.AppCatalog
import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.ScrollSession
import com.tunnellight.stop_scroll.data.model.dayKeyOf
import com.tunnellight.stop_scroll.data.prefs.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The measuring instrument.
 *
 * Listens for scroll events in the apps the user chose to track and hands them to
 * [SessionTracker]. Nothing about *what* was on screen is read or stored — only that a
 * scrollable container moved, which app it belonged to, and how far it travelled.
 *
 * Two things keep this cheap enough to sit in the path of every fling:
 *  - the service subscribes only to the tracked packages, so untracked apps never reach it;
 *  - the surface classification, which is the only call that touches a node, is throttled and
 *    cached per package.
 */
class ScrollAccessibilityService : AccessibilityService() {

    /**
     * Recreated on connect. The platform may unbind and rebind the *same* service instance —
     * when the user toggles the service off and on, for instance — and a scope cancelled on
     * unbind would leave the rebound service silently unable to write anything ever again.
     */
    private var scope: CoroutineScope = newScope()
    private val handler = Handler(Looper.getMainLooper())
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private lateinit var app: StopScrollApplication
    private lateinit var tracker: SessionTracker
    private var receiverRegistered = false

    @Volatile
    private var settings: Settings = Settings()

    private var cachedSurfacePackage: String? = null
    private var cachedSurface: FeedSurface = FeedSurface.FEED
    private var lastClassifiedAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            tracker.tick(System.currentTimeMillis())
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                tracker.onScreenOff(System.currentTimeMillis())
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = application as StopScrollApplication
        tracker = SessionTracker(onUpdate = ::onSessionUpdate)
        if (!scope.isActive) scope = newScope()

        if (!receiverRegistered) {
            registerNotExported(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            receiverRegistered = true
        }
        handler.removeCallbacks(ticker)

        scope.launch {
            app.container.settings.settings.collectLatest { next ->
                settings = next
                handler.post { applyTrackedPackages(next.trackedPackages) }
            }
        }
        scope.launch {
            app.container.repository.prune(settings.retentionDays, System.currentTimeMillis())
        }

        handler.postDelayed(ticker, TICK_INTERVAL_MS)
    }

    /**
     * Narrows the event stream to the tracked packages. Everything else is filtered by the
     * platform before it reaches this process, so untracked apps are never observed at all.
     */
    private fun applyTrackedPackages(tracked: Set<String>) {
        val info = serviceInfo ?: return
        info.packageNames = if (tracked.isEmpty()) arrayOf(packageName) else tracked.toTypedArray()
        runCatching { serviceInfo = info }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in settings.trackedPackages) return

        val now = System.currentTimeMillis()
        tracker.onScroll(pkg, surfaceFor(event, pkg, now), now, scrollDeltaOf(event))
        maybeNotifyLongSession(pkg, now)
    }

    override fun onInterrupt() = Unit

    /**
     * Stops measuring and writes out whatever bout was in progress, but leaves the service
     * able to start again: the platform can rebind this instance without destroying it.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(ticker)
        if (::tracker.isInitialized) tracker.flush(System.currentTimeMillis())
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::tracker.isInitialized) tracker.flush(System.currentTimeMillis())
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            receiverRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    // --- event decoding -------------------------------------------------------------------

    private fun surfaceFor(event: AccessibilityEvent, pkg: String, now: Long): FeedSurface {
        if (AppCatalog.find(pkg)?.alwaysShortVideo == true) return FeedSurface.SHORT_VIDEO
        if (pkg == cachedSurfacePackage && now - lastClassifiedAt < CLASSIFY_INTERVAL_MS) {
            return cachedSurface
        }
        val node = runCatching { event.source }.getOrNull()
        val viewId = node?.viewIdResourceName
        if (node != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            runCatching { node.recycle() }
        }
        val surface = SurfaceClassifier.classify(pkg, viewId, event.className?.toString())
        cachedSurfacePackage = pkg
        cachedSurface = surface
        lastClassifiedAt = now
        return surface
    }

    /**
     * Pixels travelled by this event. Apps that do not report a delta simply contribute no
     * distance rather than a guessed one, so the "distance scrolled" figure is only ever made
     * of numbers the apps actually reported.
     */
    private fun scrollDeltaOf(event: AccessibilityEvent): Int {
        val vertical = abs(event.scrollDeltaY)
        val horizontal = abs(event.scrollDeltaX)
        val delta = if (vertical > 0) vertical else horizontal
        return if (delta in 1..MAX_PLAUSIBLE_DELTA_PX) delta else 0
    }

    // --- persistence and nudges -----------------------------------------------------------

    private fun onSessionUpdate(session: ScrollSession, isFinal: Boolean) {
        scope.launch {
            app.container.repository.persist(session, zone)
            if (isFinal) checkDailyGoal()
        }
    }

    private suspend fun checkDailyGoal() {
        val config = settings
        if (!config.limitAlertsEnabled) return
        val today = LocalDate.now(zone)
        val dayKey = dayKeyOf(today)
        val store = app.container.settings
        if (store.limitAlertSentFor(dayKey)) return

        val window = PeriodMath.day(today, zone)
        val total = app.container.repository.totalBetween(window.startMs, window.endMs)
        if (total >= config.dailyGoalMs) {
            store.markLimitAlertSent(dayKey)
            app.container.notifier.notifyDailyGoalPassed(total, config.dailyGoalMs)
        }
    }

    private fun maybeNotifyLongSession(pkg: String, now: Long) {
        val config = settings
        if (!config.bingeAlertsEnabled) return
        val boutMs = tracker.activeBoutMs(now)
        if (boutMs < config.bingeMs) return
        val store = app.container.settings
        if (now - store.lastBingeAlertAt() < config.bingeMs) return
        store.markBingeAlertSent(now)
        scope.launch {
            val label = app.container.appInfo.info(pkg).label
            app.container.notifier.notifyLongSession(label, boutMs)
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 5_000L

        /** Re-reading the scrolling node on every frame of a fling would be wasteful. */
        const val CLASSIFY_INTERVAL_MS = 1_500L

        /** Guards against nonsense deltas from apps that misreport the field. */
        const val MAX_PLAUSIBLE_DELTA_PX = 20_000
    }
}

private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** [Context.registerReceiver] with the export flag Android 13+ requires. */
private fun Context.registerNotExported(receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
}

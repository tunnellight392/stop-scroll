package com.tunnellight.stop_scroll.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
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
 * Watches the apps the user chose to track and hands what it sees to [SessionTracker]. Nothing
 * about *what* was on screen is read or stored — only which app was in front, which kind of
 * feed was open, and how far a container moved.
 *
 * Two signals are needed because two kinds of feed exist:
 *  - **List-shaped feeds** (Reddit, the YouTube home feed, most apps) report scrolling, so
 *    `TYPE_VIEW_SCROLLED` carries both the timing and the distance.
 *  - **Full-screen video feeds** (Shorts, Reels, TikTok) report nothing at all: their pagers
 *    are not exposed as accessibility-scrollable containers, so no amount of swiping produces
 *    an event. For those, [ShortVideoDetector] confirms the feed is on screen and the bout is
 *    timed by presence instead. See that class for the evidence.
 *
 * Cost is kept low enough to sit in the path of every fling: the service only ever receives
 * events from tracked packages, the surface classification is throttled and cached, and the
 * window probe runs at most once every [PROBE_INTERVAL_MS].
 *
 * ## On the accessibility-policy warning
 *
 * Lint flags every [AccessibilityService] on principle, because the API is a common vector for
 * abuse. There is no code change that clears it — it is a prompt to justify the use, so:
 * measuring the user's own feed time is the app's entire and only stated function; it is
 * disclosed on the first screen before the service is ever offered, and again in Settings; the
 * user chooses which apps are observed and the service is told to ignore the rest; nothing is
 * read beyond which app is in front, which container is on screen, and how far a list moved;
 * no setting is changed, no privacy control is bypassed, and the app holds no internet
 * permission, so nothing can leave the device.
 *
 * Suppressing the warning does not satisfy Google Play: shipping this still requires an
 * accessibility-use declaration in the Play Console.
 */
@SuppressLint("AccessibilityPolicy")
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
    private var lastProbeAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            // A short-video bout has no scroll events keeping it alive, so confirm the user is
            // still on the surface before the idle window would otherwise close it.
            refreshShortVideoBout(now)
            tracker.tick(now)
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
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
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
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in settings.trackedPackages) return

        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            tracker.onScroll(pkg, surfaceFor(event, pkg, now), now, scrollDeltaOf(event))
        } else {
            // Window and content changes are the only heartbeat a short-video feed gives: its
            // own player keeps updating while a clip plays, even though swiping is silent.
            probeShortVideo(pkg, now)
        }
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

    // --- short-video presence ---------------------------------------------------------------

    /** Keeps an open short-video bout alive while the user is still on the feed. */
    private fun refreshShortVideoBout(now: Long) {
        val pkg = tracker.activePackage ?: return
        if (!tracker.isOpenOn(pkg, FeedSurface.SHORT_VIDEO, now)) return
        if (pkg !in settings.trackedPackages) return
        probeShortVideo(pkg, now)
    }

    /**
     * Looks at whether a short-video feed is on screen and, if so, records that the user was
     * there. Throttled to one look every [PROBE_INTERVAL_MS], only for tracked apps, and only
     * at view ids — never at text or content.
     */
    private fun probeShortVideo(pkg: String, now: Long): Boolean {
        if (now - lastProbeAt < PROBE_INTERVAL_MS) return false
        val known = AppCatalog.find(pkg) ?: return false
        lastProbeAt = now

        if (known.alwaysShortVideo) {
            tracker.onDwell(pkg, FeedSurface.SHORT_VIDEO, now)
            return true
        }
        if (known.shortVideoMarkers.isEmpty()) return false

        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val onSurface = try {
            root.packageName?.toString() == pkg &&
                isFullScreen(root) &&
                ShortVideoDetector.isOnShortVideoSurface(root, known.shortVideoMarkers)
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                runCatching { root.recycle() }
            }
        }

        if (onSurface) tracker.onDwell(pkg, FeedSurface.SHORT_VIDEO, now)
        return onSurface
    }

    /**
     * Rejects a feed that is not actually the thing being looked at.
     *
     * A Short carries on playing in a picture-in-picture window after you leave YouTube, and
     * that window keeps emitting the content changes the probe rides on — so without this the
     * app cheerfully bills you for scrolling you are not doing. Measured: 2 minutes of real
     * Shorts became 5 while the phone sat on another app.
     */
    private fun isFullScreen(root: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val inPip = runCatching { root.window?.isInPictureInPictureMode }.getOrNull()
            if (inPip == true) return false
        }
        // Also holds on older versions, and catches any other small overlay: a feed you are
        // reading fills the screen.
        val bounds = android.graphics.Rect()
        root.getBoundsInScreen(bounds)
        val metrics = resources.displayMetrics
        return bounds.height() >= metrics.heightPixels * MIN_SCREEN_FRACTION &&
            bounds.width() >= metrics.widthPixels * MIN_SCREEN_FRACTION
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
     * of numbers the apps actually reported. Short-video feeds report none, which is why that
     * figure stays small even after a long session on Shorts.
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

        /** Short-video feeds emit content changes constantly; one look every 2s is plenty. */
        const val PROBE_INTERVAL_MS = 2_000L

        /** Guards against nonsense deltas from apps that misreport the field. */
        const val MAX_PLAUSIBLE_DELTA_PX = 20_000

        /** Below this share of the display a "feed" is a picture-in-picture window or overlay. */
        const val MIN_SCREEN_FRACTION = 0.6f
    }
}

private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

package com.tunnellight.stop_scroll.service

import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.ScrollSession
import kotlin.math.abs

/**
 * Timing rules for turning raw scroll events into bouts.
 *
 * Short-video feeds get a much longer idle window and more dwell credit than text feeds: one
 * swipe on TikTok buys twenty seconds of watching, whereas one flick on Reddit buys a couple
 * of seconds of reading. Using a single threshold for both either shreds TikTok sessions into
 * fragments or credits a Reddit glance as a minute of scrolling.
 */
data class TrackerConfig(
    /** No scroll for this long ends a text/image feed bout. */
    val feedIdleGapMs: Long = 45_000L,
    /** The same, for full-screen video feeds where a single swipe holds attention far longer. */
    val shortVideoIdleGapMs: Long = 120_000L,
    /** Time credited after the final scroll of a text/image bout. */
    val feedDwellMs: Long = 3_000L,
    /** Time credited after the final swipe of a short-video bout. */
    val shortVideoDwellMs: Long = 8_000L,
    /** A bout needs at least this many scrolls to count; one flick is not doom scrolling. */
    val minScrollCount: Int = 2,
    /** How often an in-progress bout is written to the database. */
    val checkpointIntervalMs: Long = 60_000L,
)

/**
 * Folds a stream of scroll events into [ScrollSession]s. Deliberately free of Android types
 * so the timing rules can be unit tested against a fake clock.
 *
 * A bout's end time is always derived from its last scroll plus the dwell credit, never from
 * the moment the tracker happened to notice it had ended. Noticing late therefore costs
 * nothing in accuracy — which is why a foreground app change does not close a bout: the idle
 * window handles it, without splitting a session in two every time a toast or the
 * notification shade steals the window for a moment.
 */
class SessionTracker(
    private val config: TrackerConfig = TrackerConfig(),
    private val onUpdate: (session: ScrollSession, isFinal: Boolean) -> Unit,
) {

    private class Bout(
        val packageName: String,
        val surface: FeedSurface,
        val startTime: Long,
    ) {
        var lastScrollAt: Long = startTime
        var scrollCount: Int = 0
        var scrollPx: Long = 0L
        var lastCheckpointAt: Long = startTime
    }

    private var bout: Bout? = null

    val activePackage: String? get() = bout?.packageName

    /** How long the bout in progress has been running, or 0 when nothing is open. */
    fun activeBoutMs(nowMs: Long): Long {
        val current = bout ?: return 0L
        if (nowMs - current.lastScrollAt > idleGap(current.surface)) return 0L
        return (nowMs - current.startTime).coerceAtLeast(0L)
    }

    fun onScroll(packageName: String, surface: FeedSurface, nowMs: Long, deltaPx: Int) {
        val current = bout
        val continues = current != null &&
            current.packageName == packageName &&
            current.surface == surface &&
            nowMs - current.lastScrollAt <= idleGap(current.surface)

        if (!continues) {
            if (current != null) close(current, nowMs)
            bout = Bout(packageName, surface, nowMs)
        }

        val active = bout ?: return
        active.lastScrollAt = nowMs
        active.scrollCount += 1
        active.scrollPx += abs(deltaPx).toLong()
    }

    /** Ends the bout in progress: the screen went off, or the service is shutting down. */
    fun onScreenOff(nowMs: Long) = flush(nowMs)

    /** Closes an idle bout and check-points a long-running one. Call on a timer. */
    fun tick(nowMs: Long) {
        val current = bout ?: return
        if (nowMs - current.lastScrollAt > idleGap(current.surface)) {
            close(current, nowMs)
            return
        }
        if (nowMs - current.lastCheckpointAt >= config.checkpointIntervalMs &&
            current.scrollCount >= config.minScrollCount
        ) {
            current.lastCheckpointAt = nowMs
            onUpdate(current.toSession(endTime = nowMs), false)
        }
    }

    fun flush(nowMs: Long) {
        val current = bout ?: return
        close(current, nowMs)
    }

    private fun close(current: Bout, closeAt: Long) {
        bout = null
        if (current.scrollCount < config.minScrollCount) return
        val credited = current.lastScrollAt + dwell(current.surface)
        val endTime = maxOf(current.lastScrollAt, minOf(credited, maxOf(closeAt, current.lastScrollAt)))
        onUpdate(current.toSession(endTime), true)
    }

    private fun Bout.toSession(endTime: Long) = ScrollSession(
        startTime = startTime,
        endTime = endTime,
        packageName = packageName,
        surface = surface,
        scrollCount = scrollCount,
        scrollPx = scrollPx,
    )

    private fun idleGap(surface: FeedSurface): Long =
        if (surface == FeedSurface.SHORT_VIDEO) config.shortVideoIdleGapMs else config.feedIdleGapMs

    private fun dwell(surface: FeedSurface): Long =
        if (surface == FeedSurface.SHORT_VIDEO) config.shortVideoDwellMs else config.feedDwellMs
}

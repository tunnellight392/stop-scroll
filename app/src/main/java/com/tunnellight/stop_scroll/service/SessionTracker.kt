package com.tunnellight.stop_scroll.service

import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.ScrollSession
import kotlin.math.abs

/**
 * Timing rules for turning raw signals into bouts.
 *
 * Short-video feeds get a much longer idle window and more dwell credit than text feeds: one
 * swipe on TikTok buys twenty seconds of watching, whereas one flick on Reddit buys a couple
 * of seconds of reading. Using a single threshold for both either shreds TikTok sessions into
 * fragments or credits a Reddit glance as a minute of scrolling.
 */
data class TrackerConfig(
    /** No activity for this long ends a text/image feed bout. */
    val feedIdleGapMs: Long = 45_000L,
    /** The same, for full-screen video feeds where a single swipe holds attention far longer. */
    val shortVideoIdleGapMs: Long = 120_000L,
    /** Time credited after the final scroll of a text/image bout. */
    val feedDwellMs: Long = 3_000L,
    /** Time credited after the last sighting of a short-video bout. */
    val shortVideoDwellMs: Long = 8_000L,
    /** A scroll-driven bout needs this many scrolls to count; one flick is not doom scrolling. */
    val minScrollCount: Int = 2,
    /** A presence-driven bout needs this many sightings, which is the same idea in time. */
    val minDwellTicks: Int = 2,
    /** How often an in-progress bout is written to the database. */
    val checkpointIntervalMs: Long = 15_000L,
)

/**
 * Folds a stream of signals into [ScrollSession]s. Deliberately free of Android types so the
 * timing rules can be unit tested against a fake clock.
 *
 * Two kinds of signal feed it, because two kinds of feed exist:
 *  - [onScroll], for containers that report scrolling to the accessibility layer — Reddit, the
 *    YouTube home feed, most list-shaped apps;
 *  - [onDwell], for full-screen video pagers that report no scrolling at all. YouTube Shorts
 *    exposes no accessibility-scrollable node, so nothing is ever emitted when you swipe; the
 *    only honest measure there is that you were on the surface at all. For those feeds the
 *    number means "time in the feed" rather than "time with your thumb moving", which is also
 *    the number a viewer of Shorts actually cares about.
 *
 * A bout's end time is always derived from its last signal plus the dwell credit, never from
 * the moment the tracker happened to notice it had ended. Noticing late therefore costs
 * nothing in accuracy.
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
        var lastActivityAt: Long = startTime
        var scrollCount: Int = 0
        var dwellTicks: Int = 0
        var scrollPx: Long = 0L
        var lastCheckpointAt: Long = startTime
    }

    private var bout: Bout? = null

    val activePackage: String? get() = bout?.packageName

    /** How long the bout in progress has been running, or 0 when nothing is open. */
    fun activeBoutMs(nowMs: Long): Long {
        val current = bout ?: return 0L
        if (nowMs - current.lastActivityAt > idleGap(current.surface)) return 0L
        return (nowMs - current.startTime).coerceAtLeast(0L)
    }

    /** True while a bout on [surface] is open for [packageName]. */
    fun isOpenOn(packageName: String, surface: FeedSurface, nowMs: Long): Boolean {
        val current = bout ?: return false
        return current.packageName == packageName &&
            current.surface == surface &&
            nowMs - current.lastActivityAt <= idleGap(current.surface)
    }

    fun onScroll(packageName: String, surface: FeedSurface, nowMs: Long, deltaPx: Int) {
        extend(packageName, surface, nowMs) {
            scrollCount += 1
            scrollPx += abs(deltaPx).toLong()
        }
    }

    /** Records that the user was seen on [surface] at [nowMs], without any scroll to go on. */
    fun onDwell(packageName: String, surface: FeedSurface, nowMs: Long) {
        extend(packageName, surface, nowMs) { dwellTicks += 1 }
    }

    /** Ends the bout in progress: the screen went off, or the service is shutting down. */
    fun onScreenOff(nowMs: Long) = flush(nowMs)

    /** Closes an idle bout and check-points a long-running one. Call on a timer. */
    fun tick(nowMs: Long) {
        val current = bout ?: return
        if (nowMs - current.lastActivityAt > idleGap(current.surface)) {
            close(current, nowMs)
            return
        }
        if (nowMs - current.lastCheckpointAt >= config.checkpointIntervalMs &&
            current.isSubstantial()
        ) {
            current.lastCheckpointAt = nowMs
            onUpdate(current.toSession(endTime = nowMs), false)
        }
    }

    fun flush(nowMs: Long) {
        val current = bout ?: return
        close(current, nowMs)
    }

    private fun extend(
        packageName: String,
        surface: FeedSurface,
        nowMs: Long,
        record: Bout.() -> Unit,
    ) {
        val current = bout
        val continues = current != null &&
            current.packageName == packageName &&
            current.surface == surface &&
            nowMs - current.lastActivityAt <= idleGap(current.surface)

        if (!continues) {
            if (current != null) close(current, nowMs)
            bout = Bout(packageName, surface, nowMs)
        }

        val active = bout ?: return
        active.lastActivityAt = nowMs
        active.record()
    }

    private fun close(current: Bout, closeAt: Long) {
        bout = null
        if (!current.isSubstantial()) return
        val credited = current.lastActivityAt + dwell(current.surface)
        val endTime = maxOf(
            current.lastActivityAt,
            minOf(credited, maxOf(closeAt, current.lastActivityAt)),
        )
        onUpdate(current.toSession(endTime), true)
    }

    /** A bout counts once it has enough evidence of either kind behind it. */
    private fun Bout.isSubstantial(): Boolean =
        scrollCount >= config.minScrollCount || dwellTicks >= config.minDwellTicks

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

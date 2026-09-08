package com.tunnellight.stop_scroll.analytics

import com.tunnellight.stop_scroll.data.model.AppTotal
import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.PeriodStats
import com.tunnellight.stop_scroll.data.model.ScrollSession
import java.util.Arrays

/**
 * Pure aggregation over scroll sessions. Everything here clips sessions to the window being
 * asked about, so a bout that runs across midnight (or across an hour, week or month
 * boundary) is split between the buckets it actually occupied instead of landing wholly in
 * the bucket it started in.
 */
object Aggregator {

    const val OTHER_PACKAGE = "__other__"

    /** Milliseconds of [session] that fall inside the half-open window from..to. */
    fun clippedMs(session: ScrollSession, from: Long, to: Long): Long =
        (minOf(session.endTime, to) - maxOf(session.startTime, from)).coerceAtLeast(0L)

    fun totalMs(sessions: List<ScrollSession>, from: Long, to: Long): Long =
        sessions.sumOf { clippedMs(it, from, to) }

    /**
     * Spreads every session across the buckets described by [edges], an ascending array of
     * n + 1 boundaries producing n buckets. Used for hourly, daily, weekly and monthly
     * charts alike.
     */
    fun spread(sessions: List<ScrollSession>, edges: LongArray): LongArray {
        val bucketCount = edges.size - 1
        val out = LongArray(maxOf(bucketCount, 0))
        if (bucketCount <= 0) return out
        for (session in sessions) {
            if (session.endTime <= edges[0] || session.startTime >= edges[bucketCount]) continue
            var index = indexOfBucket(edges, session.startTime).coerceIn(0, bucketCount - 1)
            while (index < bucketCount && edges[index] < session.endTime) {
                val overlap = minOf(session.endTime, edges[index + 1]) -
                    maxOf(session.startTime, edges[index])
                if (overlap > 0L) out[index] += overlap
                index++
            }
        }
        return out
    }

    private fun indexOfBucket(edges: LongArray, value: Long): Int {
        val found = Arrays.binarySearch(edges, value)
        return if (found >= 0) found else -found - 2
    }

    fun byApp(sessions: List<ScrollSession>, from: Long, to: Long): List<AppTotal> =
        sessions
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                var total = 0L
                var count = 0
                var px = 0L
                for (session in list) {
                    val clipped = clippedMs(session, from, to)
                    if (clipped <= 0L) continue
                    total += clipped
                    count++
                    // Distance is split in the same proportion as time when a session
                    // straddles the edge of the window.
                    px += if (session.durationMs <= 0L) {
                        session.scrollPx
                    } else {
                        (session.scrollPx * clipped) / session.durationMs
                    }
                }
                AppTotal(packageName = pkg, totalMs = total, sessions = count, scrollPx = px)
            }
            .filter { it.totalMs > 0L }
            .sortedByDescending { it.totalMs }

    fun bySurface(sessions: List<ScrollSession>, from: Long, to: Long): Map<FeedSurface, Long> {
        val out = linkedMapOf<FeedSurface, Long>()
        for (session in sessions) {
            val clipped = clippedMs(session, from, to)
            if (clipped <= 0L) continue
            out[session.surface] = (out[session.surface] ?: 0L) + clipped
        }
        return out
    }

    /**
     * Rolls a window up into everything the analytics screens need. [dayEdges] is the daily
     * boundary array for the same window, used to count the days that saw any scrolling at
     * all, which is what the averages are divided by.
     */
    fun stats(
        sessions: List<ScrollSession>,
        from: Long,
        to: Long,
        dayEdges: LongArray,
    ): PeriodStats {
        val inWindow = sessions.filter { clippedMs(it, from, to) > 0L }
        if (inWindow.isEmpty()) return PeriodStats.EMPTY
        val daily = spread(inWindow, dayEdges)
        return PeriodStats(
            totalMs = totalMs(inWindow, from, to),
            sessionCount = inWindow.size,
            longestSessionMs = inWindow.maxOf { it.durationMs },
            scrollPx = inWindow.sumOf { it.scrollPx },
            activeDays = daily.count { it > 0L },
            byApp = byApp(inWindow, from, to),
            bySurface = bySurface(inWindow, from, to),
        )
    }

    /**
     * Keeps the [limit] largest apps and folds the rest into a single bucket. The categorical
     * palette tops out at eight slots, and a generated ninth hue is indistinguishable from
     * one already on screen, so the tail becomes "Other" rather than a new colour.
     */
    fun topAppsWithOther(totals: List<AppTotal>, limit: Int = 6): List<AppTotal> {
        if (totals.size <= limit) return totals
        val head = totals.take(limit)
        val tail = totals.drop(limit)
        return head + AppTotal(
            packageName = OTHER_PACKAGE,
            totalMs = tail.sumOf { it.totalMs },
            sessions = tail.sumOf { it.sessions },
            scrollPx = tail.sumOf { it.scrollPx },
        )
    }
}

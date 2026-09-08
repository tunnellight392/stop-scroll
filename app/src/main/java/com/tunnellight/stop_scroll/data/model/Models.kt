package com.tunnellight.stop_scroll.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The kind of surface a scroll happened on. Short-video feeds behave very differently
 * from text/image feeds: a single swipe there buys a much longer dwell, so the two get
 * different idle windows and dwell credit in [com.tunnellight.stop_scroll.service.SessionTracker].
 */
enum class FeedSurface {
    /** Vertical full-screen video feeds: Shorts, Reels, TikTok, Spotlight. */
    SHORT_VIDEO,

    /** Classic timeline/feed scrolling: Reddit, Facebook, X, LinkedIn, Pinterest. */
    FEED,

    /** Scrolling inside the app but not in a recognised feed (settings, DMs, search). */
    OTHER;

    companion object {
        fun fromKey(key: String): FeedSurface = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

/**
 * One bout of continuous scrolling in one app. Sessions are keyed by [startTime]: only one
 * app can be in the foreground at a time, so two sessions can never begin in the same
 * millisecond, which lets the tracker check-point an in-progress session without carrying
 * a row id around.
 */
data class ScrollSession(
    val startTime: Long,
    val endTime: Long,
    val packageName: String,
    val surface: FeedSurface,
    val scrollCount: Int,
    val scrollPx: Long,
) {
    val durationMs: Long get() = (endTime - startTime).coerceAtLeast(0L)

    fun dayKey(zone: ZoneId): Int = dayKeyOf(Instant.ofEpochMilli(startTime).atZone(zone).toLocalDate())
}

fun dayKeyOf(date: LocalDate): Int = date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth

/** Total scroll time attributed to one package over some range. */
data class AppTotal(
    val packageName: String,
    val totalMs: Long,
    val sessions: Int,
    val scrollPx: Long,
) {
    fun shareOf(totalMs: Long): Float = if (totalMs <= 0L) 0f else this.totalMs.toFloat() / totalMs
}

/** A single labelled bucket in a bar chart (an hour, a day, a week, a month). */
data class Bucket(
    val label: String,
    val valueMs: Long,
    val date: LocalDate? = null,
    val isCurrent: Boolean = false,
)

/** Everything the app knows about one stretch of time. */
data class PeriodStats(
    val totalMs: Long,
    val sessionCount: Int,
    val longestSessionMs: Long,
    val scrollPx: Long,
    val activeDays: Int,
    val byApp: List<AppTotal>,
    val bySurface: Map<FeedSurface, Long>,
) {
    val averagePerActiveDayMs: Long get() = if (activeDays == 0) 0L else totalMs / activeDays
    val averageSessionMs: Long get() = if (sessionCount == 0) 0L else totalMs / sessionCount

    companion object {
        val EMPTY = PeriodStats(0L, 0, 0L, 0L, 0, emptyList(), emptyMap())
    }
}

/**
 * A like-for-like comparison of two periods. When the current period is still running the
 * previous one is clipped to the same elapsed fraction, so "this week" is never compared
 * against a full week it hasn't had the chance to fill yet.
 */
data class Comparison(
    val currentMs: Long,
    val previousMs: Long,
    val previousLabel: String,
    val partial: Boolean,
) {
    val deltaMs: Long get() = currentMs - previousMs

    /** Null when the earlier period was zero: a percentage of nothing has no meaning. */
    val deltaFraction: Float?
        get() = if (previousMs <= 0L) null else deltaMs.toFloat() / previousMs.toFloat()

    /**
     * False only when both periods are empty. A previous period of zero is still worth
     * comparing against — going from no scrolling to some is exactly the change a user
     * wants to see — it just has to be reported as an amount rather than a percentage.
     */
    val hasAnythingToCompare: Boolean get() = currentMs > 0L || previousMs > 0L
}

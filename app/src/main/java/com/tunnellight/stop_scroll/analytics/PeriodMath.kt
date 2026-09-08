package com.tunnellight.stop_scroll.analytics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/** A closed range of local dates plus the millisecond window it maps to. */
data class DateWindow(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val startMs: Long,
    val endMs: Long,
) {
    val dayCount: Int get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()
}

/**
 * Calendar arithmetic for the analytics screens.
 *
 * [comparisonWindows] deliberately clips the earlier period to the same elapsed length as
 * the current one. Comparing a Tuesday-morning "this week" against a complete previous week
 * would always show a flattering drop, which is the easiest way for a stats screen to
 * mislead the person reading it.
 */
object PeriodMath {

    fun startOfDayMs(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun window(start: LocalDate, endInclusive: LocalDate, zone: ZoneId): DateWindow = DateWindow(
        start = start,
        endInclusive = endInclusive,
        startMs = startOfDayMs(start, zone),
        endMs = startOfDayMs(endInclusive.plusDays(1), zone),
    )

    fun day(date: LocalDate, zone: ZoneId): DateWindow = window(date, date, zone)

    fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek =
        WeekFields.of(locale).firstDayOfWeek

    fun weekStart(date: LocalDate, locale: Locale = Locale.getDefault()): LocalDate {
        val first = firstDayOfWeek(locale)
        val shift = (date.dayOfWeek.value - first.value + 7) % 7
        return date.minusDays(shift.toLong())
    }

    fun week(date: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): DateWindow {
        val start = weekStart(date, locale)
        return window(start, start.plusDays(6), zone)
    }

    fun month(date: LocalDate, zone: ZoneId): DateWindow {
        val ym = YearMonth.from(date)
        return window(ym.atDay(1), ym.atEndOfMonth(), zone)
    }

    /**
     * Hourly boundaries for one local day: 25 edges, 24 buckets.
     *
     * Built from local wall-clock hours rather than by adding 24 real hours to midnight, so
     * the last edge is always the following midnight. On a day that loses an hour to daylight
     * saving, adding real hours would run the final bucket an hour into the next day and
     * double-count it; here the hour that does not exist simply gets a zero-width bucket.
     */
    fun hourEdges(date: LocalDate, zone: ZoneId): LongArray {
        val edges = LongArray(25)
        for (hour in 0..23) {
            edges[hour] = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        }
        edges[24] = startOfDayMs(date.plusDays(1), zone)
        return edges
    }

    /** Daily boundaries covering [window]: dayCount + 1 edges. */
    fun dayEdges(window: DateWindow, zone: ZoneId): LongArray =
        LongArray(window.dayCount + 1) { startOfDayMs(window.start.plusDays(it.toLong()), zone) }

    fun dayEdges(start: LocalDate, endInclusive: LocalDate, zone: ZoneId): LongArray =
        dayEdges(window(start, endInclusive, zone), zone)

    /** Weekly boundaries for the [count] most recent weeks, oldest first. */
    fun weekEdges(
        today: LocalDate,
        count: Int,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): LongArray {
        val firstStart = weekStart(today, locale).minusWeeks((count - 1).toLong())
        return LongArray(count + 1) { startOfDayMs(firstStart.plusWeeks(it.toLong()), zone) }
    }

    /** Monthly boundaries for the [count] most recent months, oldest first. */
    fun monthEdges(today: LocalDate, count: Int, zone: ZoneId): LongArray {
        val firstMonth = YearMonth.from(today).minusMonths((count - 1).toLong())
        return LongArray(count + 1) {
            startOfDayMs(firstMonth.plusMonths(it.toLong()).atDay(1), zone)
        }
    }

    /**
     * Builds a pair of like-for-like windows.
     *
     * While the current period is still running, the earlier one is clipped to the same
     * elapsed span — otherwise a Tuesday-morning "this week" would be measured against a
     * complete previous week and always look like an improvement. Once the current period has
     * finished, both are taken whole, which is what week-over-week and month-over-month
     * ordinarily mean. The clipped window is never stretched past the earlier period's own
     * end, so a 31-day month in progress stops at the end of a 28-day February.
     */
    fun comparisonWindows(
        current: DateWindow,
        previous: DateWindow,
        nowMs: Long,
    ): Pair<LongRange, LongRange> {
        val stillRunning = nowMs < current.endMs
        val currentEnd =
            if (stillRunning) nowMs.coerceAtLeast(current.startMs) else current.endMs
        val previousEnd = if (stillRunning) {
            minOf(previous.endMs, previous.startMs + (currentEnd - current.startMs))
        } else {
            previous.endMs
        }
        return (current.startMs until currentEnd) to (previous.startMs until previousEnd)
    }

    /** True while [nowMs] falls inside [window], meaning the period is still filling up. */
    fun isPartial(window: DateWindow, nowMs: Long): Boolean =
        nowMs in window.startMs until window.endMs
}

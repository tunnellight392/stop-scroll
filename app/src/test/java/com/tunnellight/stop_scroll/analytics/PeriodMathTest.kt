package com.tunnellight.stop_scroll.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class PeriodMathTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val monday = Locale.UK

    @Test
    fun `a week starts on the locale first day`() {
        val wednesday = LocalDate.of(2026, 3, 11)
        assertEquals(LocalDate.of(2026, 3, 9), PeriodMath.weekStart(wednesday, monday))
    }

    @Test
    fun `a day that gains an hour to DST still produces 24 hourly buckets`() {
        // Clocks go forward in the UK on 29 March 2026.
        val edges = PeriodMath.hourEdges(LocalDate.of(2026, 3, 29), zone)
        assertEquals(25, edges.size)
        // 23 real hours in the day, so the span is one hour short of a full day.
        assertEquals(23 * 3_600_000L, edges.last() - edges.first())
    }

    @Test
    fun `a part-finished week is compared against the same stretch of the previous one`() {
        val wednesday = LocalDate.of(2026, 3, 11)
        val current = PeriodMath.week(wednesday, zone, monday)
        val previous = PeriodMath.week(wednesday.minusWeeks(1), zone, monday)
        // Wednesday at noon: two and a half days into the week.
        val now = PeriodMath.startOfDayMs(wednesday, zone) + 12 * 3_600_000L

        val (currentRange, previousRange) = PeriodMath.comparisonWindows(current, previous, now)

        val elapsed = currentRange.last + 1 - currentRange.first
        val previousElapsed = previousRange.last + 1 - previousRange.first
        assertEquals(elapsed, previousElapsed)
        assertEquals(current.startMs, currentRange.first)
        assertEquals(previous.startMs, previousRange.first)
    }

    @Test
    fun `a long month is not compared against days the short month never had`() {
        // 31 March compared against February, which has only 28 days in 2026.
        val lastDayOfMarch = LocalDate.of(2026, 3, 31)
        val current = PeriodMath.month(lastDayOfMarch, zone)
        val previous = PeriodMath.month(lastDayOfMarch.minusMonths(1), zone)
        val now = PeriodMath.startOfDayMs(lastDayOfMarch, zone) + 23 * 3_600_000L

        val (_, previousRange) = PeriodMath.comparisonWindows(current, previous, now)

        assertEquals(previous.endMs, previousRange.last + 1)
    }

    @Test
    fun `a finished period compares against the whole of the previous one`() {
        val february = LocalDate.of(2026, 2, 10)
        val current = PeriodMath.month(february, zone)
        val previous = PeriodMath.month(february.minusMonths(1), zone)
        // "Now" is well after February ended.
        val now = PeriodMath.startOfDayMs(LocalDate.of(2026, 5, 1), zone)

        val (currentRange, previousRange) = PeriodMath.comparisonWindows(current, previous, now)

        assertEquals(current.endMs, currentRange.last + 1)
        assertEquals(previous.endMs, previousRange.last + 1)
        assertFalse(PeriodMath.isPartial(current, now))
    }

    @Test
    fun `isPartial is true only while the clock sits inside the window`() {
        val today = LocalDate.of(2026, 3, 11)
        val window = PeriodMath.day(today, zone)
        assertTrue(PeriodMath.isPartial(window, window.startMs + 1))
        assertFalse(PeriodMath.isPartial(window, window.endMs))
    }

    @Test
    fun `week and month edges are ascending and the right length`() {
        val today = LocalDate.of(2026, 3, 11)

        val weeks = PeriodMath.weekEdges(today, 12, zone, monday)
        val months = PeriodMath.monthEdges(today, 6, zone)

        assertEquals(13, weeks.size)
        assertEquals(7, months.size)
        assertTrue(weeks.toList().zipWithNext().all { (a, b) -> b > a })
        assertTrue(months.toList().zipWithNext().all { (a, b) -> b > a })
        // The final week bucket is the one containing today.
        assertEquals(PeriodMath.startOfDayMs(PeriodMath.weekStart(today, monday), zone), weeks[11])
    }
}

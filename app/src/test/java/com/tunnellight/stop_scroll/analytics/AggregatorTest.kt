package com.tunnellight.stop_scroll.analytics

import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.ScrollSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AggregatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")

    private fun session(
        start: Long,
        end: Long,
        pkg: String = "com.reddit.frontpage",
        surface: FeedSurface = FeedSurface.FEED,
        px: Long = 0L,
    ) = ScrollSession(start, end, pkg, surface, scrollCount = 4, scrollPx = px)

    @Test
    fun `a bout spanning midnight is split between the two days`() {
        val day = LocalDate.of(2026, 3, 10)
        val midnight = PeriodMath.startOfDayMs(day, zone)
        // Ten minutes either side of midnight.
        val crossing = session(midnight - 600_000L, midnight + 600_000L)

        val edges = PeriodMath.dayEdges(day.minusDays(1), day, zone)
        val spread = Aggregator.spread(listOf(crossing), edges)

        assertEquals(600_000L, spread[0])
        assertEquals(600_000L, spread[1])
    }

    @Test
    fun `hourly buckets receive only the overlap that falls inside them`() {
        val day = LocalDate.of(2026, 3, 10)
        val edges = PeriodMath.hourEdges(day, zone)
        // 09:50 to 10:20.
        val start = edges[9] + 50 * 60_000L
        val end = edges[10] + 20 * 60_000L

        val spread = Aggregator.spread(listOf(session(start, end)), edges)

        assertEquals(25, edges.size)
        assertEquals(10 * 60_000L, spread[9])
        assertEquals(20 * 60_000L, spread[10])
        assertEquals(0L, spread[11])
    }

    @Test
    fun `sessions outside the window contribute nothing`() {
        val day = LocalDate.of(2026, 3, 10)
        val edges = PeriodMath.hourEdges(day, zone)
        val before = session(edges[0] - 3_600_000L, edges[0] - 60_000L)
        val after = session(edges[24] + 60_000L, edges[24] + 120_000L)

        val spread = Aggregator.spread(listOf(before, after), edges)

        assertEquals(0L, spread.sum())
    }

    @Test
    fun `per-app totals clip to the window and split distance in the same proportion`() {
        val from = 1_000_000L
        val to = 2_000_000L
        // Half of this bout falls before the window opens.
        val straddling = session(from - 500_000L, from + 500_000L, px = 1_000L)

        val totals = Aggregator.byApp(listOf(straddling), from, to)

        assertEquals(1, totals.size)
        assertEquals(500_000L, totals.single().totalMs)
        assertEquals(500L, totals.single().scrollPx)
    }

    @Test
    fun `stats count only the days that saw scrolling`() {
        val start = LocalDate.of(2026, 3, 9)
        val end = LocalDate.of(2026, 3, 15)
        val window = PeriodMath.window(start, end, zone)
        val edges = PeriodMath.dayEdges(window, zone)
        val monday = PeriodMath.startOfDayMs(start, zone)
        val wednesday = PeriodMath.startOfDayMs(start.plusDays(2), zone)

        val stats = Aggregator.stats(
            sessions = listOf(
                session(monday + 3_600_000L, monday + 4_200_000L),
                session(wednesday + 3_600_000L, wednesday + 3_900_000L),
            ),
            from = window.startMs,
            to = window.endMs,
            dayEdges = edges,
        )

        assertEquals(2, stats.activeDays)
        assertEquals(2, stats.sessionCount)
        assertEquals(900_000L, stats.totalMs)
        assertEquals(600_000L, stats.longestSessionMs)
    }

    @Test
    fun `the tail beyond the palette folds into a single Other bucket`() {
        val totals = (1..9).map {
            com.tunnellight.stop_scroll.data.model.AppTotal(
                packageName = "app$it",
                totalMs = (10 - it) * 1_000L,
                sessions = 1,
                scrollPx = 100L,
            )
        }

        val folded = Aggregator.topAppsWithOther(totals, limit = 6)

        assertEquals(7, folded.size)
        assertEquals(Aggregator.OTHER_PACKAGE, folded.last().packageName)
        // apps 7, 8 and 9 carried 3s, 2s and 1s.
        assertEquals(6_000L, folded.last().totalMs)
        assertEquals(3, folded.last().sessions)
    }

    @Test
    fun `surface totals separate short video from feeds`() {
        val stats = Aggregator.bySurface(
            sessions = listOf(
                session(0L, 60_000L, surface = FeedSurface.SHORT_VIDEO),
                session(60_000L, 90_000L, surface = FeedSurface.FEED),
                session(90_000L, 120_000L, surface = FeedSurface.SHORT_VIDEO),
            ),
            from = 0L,
            to = 200_000L,
        )

        assertEquals(90_000L, stats[FeedSurface.SHORT_VIDEO])
        assertEquals(30_000L, stats[FeedSurface.FEED])
    }
}

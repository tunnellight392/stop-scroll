package com.tunnellight.stop_scroll.util

import com.tunnellight.stop_scroll.data.model.Comparison
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    private fun comparison(currentMs: Long, previousMs: Long) =
        Comparison(currentMs, previousMs, previousLabel = "last week", partial = false)

    @Test
    fun `an empty pair of periods says there is nothing to compare`() {
        assertEquals("Nothing to compare yet", Format.comparisonText(comparison(0L, 0L)))
    }

    @Test
    fun `an empty previous period still reports the change, as an amount`() {
        // A percentage against zero is undefined, but "you scrolled 35m more than last week"
        // is both true and the most useful thing to say.
        assertEquals(
            "35m more than last week",
            Format.comparisonText(comparison(currentMs = 35 * 60_000L, previousMs = 0L)),
        )
    }

    @Test
    fun `dropping to nothing reads as a full reduction`() {
        assertEquals(
            "100% less than last week",
            Format.comparisonText(comparison(currentMs = 0L, previousMs = 40 * 60_000L)),
        )
    }

    @Test
    fun `an ordinary change reads as a percentage`() {
        assertEquals(
            "25% less than last week",
            Format.comparisonText(comparison(currentMs = 45 * 60_000L, previousMs = 60 * 60_000L)),
        )
        assertEquals(
            "50% more than last week",
            Format.comparisonText(comparison(currentMs = 90 * 60_000L, previousMs = 60 * 60_000L)),
        )
    }

    @Test
    fun `no change at all is called out as such`() {
        assertEquals(
            "Same as last week",
            Format.comparisonText(comparison(currentMs = 60_000L, previousMs = 60_000L)),
        )
    }

    @Test
    fun `durations drop an empty minutes component`() {
        assertEquals("1h", Format.duration(3_600_000L))
        assertEquals("2h 14m", Format.duration(8_040_000L))
        assertEquals("48m", Format.duration(2_880_000L))
        assertEquals("45s", Format.duration(45_000L))
        assertEquals("0m", Format.duration(0L))
    }
}

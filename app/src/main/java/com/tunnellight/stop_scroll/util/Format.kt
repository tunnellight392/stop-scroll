package com.tunnellight.stop_scroll.util

import com.tunnellight.stop_scroll.data.model.Comparison
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {

    /** "2h 14m", "1h", "48m", "45s", "0m" — the long form used for headline values. */
    fun duration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            seconds > 0 -> "${seconds}s"
            else -> "0m"
        }
    }

    /** Compact form for axis ticks and dense rows: "2h", "48m", "0". */
    fun durationCompact(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60.0
                if (hours >= 10) "${hours.roundToInt()}h" else trimZero(hours) + "h"
            }
            minutes > 0 -> "${minutes}m"
            ms > 0 -> "<1m"
            else -> "0"
        }
    }

    fun signedDuration(ms: Long): String = (if (ms >= 0) "+" else "−") + duration(abs(ms))

    fun percent(fraction: Float): String = "${(abs(fraction) * 100).roundToInt()}%"

    fun signedPercent(fraction: Float): String =
        (if (fraction >= 0) "+" else "−") + percent(fraction)

    /**
     * Scroll distance as a physical length. Pixels are divided by the display's vertical
     * DPI, so the number means the same thing on a dense phone as on a tablet.
     */
    fun distance(px: Long, yDpi: Float): String {
        if (px <= 0L || yDpi <= 0f) return "0 m"
        val metres = (px / yDpi) * 0.0254
        return when {
            metres >= 1000 -> String.format(Locale.getDefault(), "%.1f km", metres / 1000)
            metres >= 10 -> "${metres.roundToInt()} m"
            else -> String.format(Locale.getDefault(), "%.1f m", metres)
        }
    }

    /**
     * A period compared with the one before it, in words.
     *
     * The empty case says "Nothing to compare yet" rather than splicing the period label into
     * a sentence: labels are phrased for the populated reading ("yesterday by now", "last
     * week"), and "No yesterday by now to compare" is not English. When only the earlier
     * period was empty the change is real and gets reported as an amount, since a percentage
     * against zero is undefined.
     */
    fun comparisonText(comparison: Comparison): String {
        val delta = comparison.deltaMs
        return when {
            !comparison.hasAnythingToCompare -> "Nothing to compare yet"
            delta == 0L -> "Same as ${comparison.previousLabel}"
            else -> {
                val amount = comparison.deltaFraction?.let { percent(it) } ?: duration(abs(delta))
                val direction = if (delta < 0) "less than" else "more than"
                "$amount $direction ${comparison.previousLabel}"
            }
        }
    }

    fun count(value: Int): String = when {
        value >= 10_000 -> "${value / 1000}k"
        value >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", value / 1000f)
        else -> value.toString()
    }

    fun hourLabel(hour: Int): String = when (hour) {
        0 -> "12a"
        12 -> "12p"
        in 1..11 -> "${hour}a"
        else -> "${hour - 12}p"
    }

    fun weekdayInitial(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    fun shortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    fun monthShort(date: LocalDate): String =
        date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun longDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    fun dayRange(start: LocalDate, end: LocalDate): String =
        if (start.month == end.month) {
            "${start.dayOfMonth}–${end.dayOfMonth} ${monthShort(end)}"
        } else {
            "${shortDate(start)} – ${shortDate(end)}"
        }

    private fun trimZero(value: Double): String =
        String.format(Locale.getDefault(), "%.1f", value).removeSuffix(".0")
}

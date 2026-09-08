package com.tunnellight.stop_scroll.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// --- product palette --------------------------------------------------------------------

private val Indigo10 = Color(0xFF1E1B4B)
private val Indigo40 = Color(0xFF4F46E5)
private val Indigo80 = Color(0xFFA5B4FC)
private val Indigo90 = Color(0xFFE0E7FF)
private val Indigo30 = Color(0xFF3730A3)

// The secondary is deliberately harmonious with the primary rather than an alarm colour:
// Material spends it on ordinary selection states (nav items, chips, segmented buttons), so a
// red or rose secondary would make every selected tab look like a warning. Alerts use the
// status colours in ChartColors, and the error role, instead.
private val Slate10 = Color(0xFF181A2C)
private val Slate30 = Color(0xFF434659)
private val Slate40 = Color(0xFF5B5D72)
private val Slate80 = Color(0xFFC4C5DD)
private val Slate90 = Color(0xFFE1E0F5)

private val Teal10 = Color(0xFF042F2E)
private val Teal30 = Color(0xFF115E59)
private val Teal40 = Color(0xFF0D9488)
private val Teal80 = Color(0xFF5EEAD4)
private val Teal90 = Color(0xFFCCFBF1)

val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    inversePrimary = Indigo80,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3FB),
    surfaceContainer = Color(0xFFEFEDF6),
    surfaceContainerHigh = Color(0xFFE9E7F1),
    surfaceContainerHighest = Color(0xFFE4E1EC),
    outline = Color(0xFF77767F),
    outlineVariant = Color(0xFFC7C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF2F0F7),
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo10,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    inversePrimary = Indigo40,
    secondary = Slate80,
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Teal80,
    onTertiary = Teal10,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    background = Color(0xFF121218),
    onBackground = Color(0xFFE4E1EC),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE4E1EC),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34343B),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE4E1EC),
    inverseOnSurface = Color(0xFF2F3036),
    scrim = Color(0xFF000000),
)

// --- chart palette ----------------------------------------------------------------------

/**
 * Colours for data marks, kept separate from the Material scheme.
 *
 * The eight categorical slots are a validated set: each mode is stepped for its own surface
 * rather than being a flip of the other, and the slot *order* is the colour-blind-safety
 * mechanism — adjacent slots are the pairs most likely to sit next to each other in a stacked
 * bar, so the ordering is what keeps them apart. Slots are handed to packages on first sight
 * and never reassigned, so filtering a chart never repaints the series that remain.
 *
 * Ninth and later apps fold into a single neutral "Other" rather than getting a generated
 * hue, which under colour-blind simulation would be indistinguishable from a slot already
 * on screen.
 */
@Immutable
data class ChartColors(
    val series: List<Color>,
    val other: Color,
    val sequential: List<Color>,
    val grid: Color,
    val axisText: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val emphasis: Color,
    val deEmphasis: Color,
) {
    /** Slot -1 means the app overflowed the eight-slot palette. */
    fun forSlot(slot: Int): Color = if (slot in series.indices) series[slot] else other

    /** Sequential step for a 0..1 magnitude; index 0 is reserved for "nothing happened". */
    fun heat(fraction: Float): Color {
        if (fraction <= 0f) return sequential.first()
        val span = sequential.size - 1
        val index = 1 + ((fraction.coerceIn(0f, 1f) * (span - 1)).toInt())
        return sequential[index.coerceIn(1, span)]
    }
}

private val CategoricalLight = listOf(
    Color(0xFF2A78D6), // blue
    Color(0xFFEB6834), // orange
    Color(0xFF1BAF7A), // aqua
    Color(0xFFEDA100), // yellow
    Color(0xFFE87BA4), // magenta
    Color(0xFF008300), // green
    Color(0xFF4A3AA7), // violet
    Color(0xFFE34948), // red
)

private val CategoricalDark = listOf(
    Color(0xFF3987E5),
    Color(0xFFD95926),
    Color(0xFF199E70),
    Color(0xFFC98500),
    Color(0xFFD55181),
    Color(0xFF008300),
    Color(0xFF9085E9),
    Color(0xFFE66767),
)

/** One hue, light to dark. The first step reads as "near zero" against the light surface. */
private val SequentialLight = listOf(
    Color(0xFFEFEDF6),
    Color(0xFFCDE2FB),
    Color(0xFF9EC5F4),
    Color(0xFF6DA7EC),
    Color(0xFF3987E5),
    Color(0xFF2A78D6),
    Color(0xFF1C5CAB),
)

/** The same hue inverted for a dark surface: more magnitude means brighter, not darker. */
private val SequentialDark = listOf(
    Color(0xFF1E1F25),
    Color(0xFF0D366B),
    Color(0xFF184F95),
    Color(0xFF256ABF),
    Color(0xFF3987E5),
    Color(0xFF6DA7EC),
    Color(0xFF9EC5F4),
)

val LightChartColors = ChartColors(
    series = CategoricalLight,
    other = Color(0xFF8B8996),
    sequential = SequentialLight,
    grid = Color(0xFFDFDDE8),
    axisText = Color(0xFF6C6B75),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFFAB219),
    critical = Color(0xFFD03B3B),
    emphasis = Color(0xFF2A78D6),
    deEmphasis = Color(0xFFC7C5D0),
)

val DarkChartColors = ChartColors(
    series = CategoricalDark,
    other = Color(0xFF8B8996),
    sequential = SequentialDark,
    grid = Color(0xFF32333A),
    axisText = Color(0xFF9E9CA8),
    good = Color(0xFF0CA30C),
    warning = Color(0xFFFAB219),
    critical = Color(0xFFD03B3B),
    emphasis = Color(0xFF3987E5),
    deEmphasis = Color(0xFF3E3F47),
)

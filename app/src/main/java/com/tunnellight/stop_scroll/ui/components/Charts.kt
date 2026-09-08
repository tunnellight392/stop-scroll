package com.tunnellight.stop_scroll.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors
import com.tunnellight.stop_scroll.ui.theme.TabularNumbers
import com.tunnellight.stop_scroll.util.Format
import java.time.LocalDate

/** One named series of values, aligned to the chart's label list. */
data class ChartSeries(val name: String, val color: Color, val values: List<Long>)

// --- shared drawing helpers ---------------------------------------------------------------

/**
 * A column with rounded data-end and a square baseline, so the mark visibly grows *from* the
 * axis rather than floating as a pill.
 */
private fun DrawScope.drawColumn(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color,
    radiusPx: Float,
) {
    val width = right - left
    val height = bottom - top
    if (width <= 0f || height <= 0f) return
    val radius = minOf(radiusPx, width / 2f, height)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top, right, bottom),
                topLeft = CornerRadius(radius),
                topRight = CornerRadius(radius),
                bottomRight = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}

/** The horizontal twin: rounded at the value end, square where it meets the baseline. */
private fun DrawScope.drawRowBar(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color,
    radiusPx: Float,
) {
    val width = right - left
    val height = bottom - top
    if (width <= 0f || height <= 0f) return
    val radius = minOf(radiusPx, height / 2f, width)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top, right, bottom),
                topLeft = CornerRadius.Zero,
                topRight = CornerRadius(radius),
                bottomRight = CornerRadius(radius),
                bottomLeft = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}

// --- column chart ---------------------------------------------------------------------------

/**
 * Columns for one or two series.
 *
 * Values are read by tapping a column, which reveals the exact figure above the plot; the peak
 * is shown whenever there is one, so a column can be read approximately without touching it.
 * Marks are capped at 24dp and separated by a surface gap rather than a stroke.
 */
@Composable
fun ColumnChart(
    labels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 156.dp,
    emphasisIndex: Int? = null,
    labelEvery: Int = 1,
    formatter: (Long) -> String = { Format.durationCompact(it) },
    accessibleLabel: (Int) -> String = { index -> labels.getOrElse(index) { "" } },
) {
    if (series.isEmpty() || labels.isEmpty()) return
    val chart = LocalChartColors.current
    val density = LocalDensity.current
    var selected by remember(labels, series) { mutableIntStateOf(-1) }

    // The true peak drives the readout; the scale is the same number floored at 1 so that an
    // all-zero chart cannot divide by zero. Reading the floored value back out is what made an
    // empty chart claim a peak of "<1m".
    val peakValue = remember(series) { series.flatMap { it.values }.maxOrNull() ?: 0L }
    val maxValue = peakValue.coerceAtLeast(1L)
    val grow = remember(series) { Animatable(0f) }
    LaunchedEffect(series) { grow.animateTo(1f, tween(durationMillis = 420)) }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            val readout = if (selected >= 0) {
                val parts = series.joinToString("  ·  ") { serie ->
                    "${serie.name} ${formatter(serie.values.getOrElse(selected) { 0L })}"
                }
                "${accessibleLabel(selected)} — $parts"
            } else if (peakValue > 0L) {
                "Peak ${formatter(peakValue)}"
            } else {
                "Nothing recorded yet"
            }
            Text(
                text = readout,
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                color = if (selected >= 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(10.dp))

        val chartDescription = remember(labels, series) {
            buildString {
                append("Bar chart. ")
                series.forEach { serie ->
                    append(serie.name)
                    append(": ")
                    append(
                        labels.indices.joinToString(", ") { index ->
                            "${labels[index]} ${Format.duration(serie.values.getOrElse(index) { 0L })}"
                        },
                    )
                    append(". ")
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .semantics { contentDescription = chartDescription }
                .pointerInput(labels, series) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / labels.size
                        val index = (offset.x / slot).toInt().coerceIn(0, labels.size - 1)
                        selected = if (selected == index) -1 else index
                    }
                },
        ) {
            val gapPx = with(density) { 2.dp.toPx() }
            val maxThicknessPx = with(density) { 24.dp.toPx() }
            val radiusPx = with(density) { 4.dp.toPx() }
            val minVisiblePx = with(density) { 2.dp.toPx() }
            val baseline = size.height

            // Recessive hairline grid: baseline, midpoint, and the scale top.
            val hairline = with(density) { 1.dp.toPx() }
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = baseline - baseline * fraction
                drawRect(
                    color = chart.grid,
                    topLeft = Offset(0f, y - hairline / 2f),
                    size = Size(size.width, hairline),
                )
            }

            val slotWidth = size.width / labels.size
            val groupWidth = (slotWidth - gapPx * 2).coerceAtLeast(1f)
            val barWidth = minOf(
                maxThicknessPx,
                (groupWidth - gapPx * (series.size - 1)) / series.size,
            ).coerceAtLeast(1f)
            val groupSpan = barWidth * series.size + gapPx * (series.size - 1)

            labels.indices.forEach { index ->
                val slotLeft = index * slotWidth
                val groupLeft = slotLeft + (slotWidth - groupSpan) / 2f

                if (index == selected) {
                    drawRect(
                        color = chart.grid,
                        topLeft = Offset(slotLeft, 0f),
                        size = Size(slotWidth, size.height),
                    )
                }

                series.forEachIndexed { seriesIndex, serie ->
                    val value = serie.values.getOrElse(index) { 0L }
                    val color = when {
                        emphasisIndex == null -> serie.color
                        index == emphasisIndex -> serie.color
                        else -> chart.deEmphasis
                    }
                    val fraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                    val rawHeight = baseline * fraction * grow.value
                    val height = if (value > 0L) maxOf(rawHeight, minVisiblePx) else 0f
                    val left = groupLeft + seriesIndex * (barWidth + gapPx)
                    drawColumn(
                        left = left,
                        top = baseline - height,
                        right = left + barWidth,
                        bottom = baseline,
                        color = color,
                        radiusPx = radiusPx,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = if (index % labelEvery == 0) label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                    color = if (index == selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        chart.axisText
                    },
                    fontWeight = if (index == selected) FontWeight.SemiBold else null,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    // A label's slot is only 1/24th or 1/31st of the chart, far narrower than
                    // "12a" or "30". Because only every nth slot carries a label, the
                    // neighbouring slots are empty and the text can safely spill into them
                    // rather than being cropped to its first character.
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

// --- part-to-whole ---------------------------------------------------------------------------

data class ShareSegment(val label: String, val valueMs: Long, val color: Color)

/**
 * A single horizontal bar split into its parts — the honest form for "what made up this
 * total". Segments are separated by a surface gap, never by a stroke.
 */
@Composable
fun ShareBar(
    segments: List<ShareSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val total = segments.sumOf { it.valueMs }
    val chart = LocalChartColors.current
    val description = remember(segments) {
        if (total <= 0L) {
            "No scrolling recorded."
        } else {
            "Share of scroll time. " + segments.joinToString(", ") {
                "${it.label} ${Format.percent(it.valueMs.toFloat() / total)}"
            }
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        val radius = size.height / 2f
        if (total <= 0L) {
            drawRoundRect(color = chart.deEmphasis, cornerRadius = CornerRadius(radius))
            return@Canvas
        }
        val gap = 2.dp.toPx()
        val usable = size.width - gap * (segments.size - 1).coerceAtLeast(0)
        var x = 0f
        segments.forEach { segment ->
            val width = usable * (segment.valueMs.toFloat() / total)
            if (width > 0.5f) {
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(minOf(radius, width / 2f)),
                )
            }
            x += width + gap
        }
    }
}

/** A horizontal magnitude bar for one row of a list. */
@Composable
fun RowBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val chart = LocalChartColors.current
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = chart.deEmphasis.copy(alpha = 0.5f),
            cornerRadius = CornerRadius(radius),
        )
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRowBar(0f, 0f, maxOf(width, size.height), size.height, color, radius)
        }
    }
}

/**
 * Progress against the daily goal. The unfilled track is a wash of the fill's own colour, so
 * the state reads across the whole bar rather than only the filled part.
 */
@Composable
fun GoalMeter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "goal-meter",
    )
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2f
        drawRoundRect(color = color.copy(alpha = 0.18f), cornerRadius = CornerRadius(radius))
        val width = size.width * animated
        if (width > 0f) {
            drawRoundRect(
                color = color,
                size = Size(maxOf(width, size.height), size.height),
                cornerRadius = CornerRadius(radius),
            )
        }
    }
}

// --- calendar heatmap ------------------------------------------------------------------------

data class HeatCell(val date: LocalDate?, val valueMs: Long)

/**
 * A month laid out as a calendar, coloured on one hue from light to dark. Magnitude is the
 * job here, so a single-hue sequential ramp is the right encoding; the scale legend below the
 * grid is not optional, because a colour scale cannot be read without one.
 */
@Composable
fun MonthHeatmap(
    cells: List<HeatCell>,
    weekdayInitials: List<String>,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    onSelect: (LocalDate) -> Unit = {},
) {
    val chart = LocalChartColors.current
    // As in ColumnChart: the legend reports the real peak, the ramp divides by a floored one.
    val peak = remember(cells) { cells.maxOfOrNull { it.valueMs } ?: 0L }
    val max = peak.coerceAtLeast(1L)

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            weekdayInitials.forEach { initial ->
                Text(
                    text = initial,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = chart.axisText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val date = cell.date
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (date == null) {
                                    Color.Transparent
                                } else {
                                    chart.heat(cell.valueMs.toFloat() / max.toFloat())
                                },
                            )
                            .then(
                                if (date != null && date == selected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (date != null) {
                                    Modifier
                                        .clickable { onSelect(date) }
                                        .semantics {
                                            contentDescription =
                                                "${Format.shortDate(date)}, " +
                                                    Format.duration(cell.valueMs)
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        HeatScaleLegend(peakMs = peak)
    }
}

@Composable
private fun HeatScaleLegend(peakMs: Long) {
    val chart = LocalChartColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = chart.axisText,
        )
        Spacer(Modifier.width(6.dp))
        chart.sequential.forEach { step ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(step),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = chart.axisText,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (peakMs > 0L) "Peak ${Format.durationCompact(peakMs)}" else "Nothing recorded yet",
            style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
            color = chart.axisText,
        )
    }
}

// --- legend ----------------------------------------------------------------------------------

data class LegendItem(val label: String, val color: Color)

/**
 * Always present when a chart carries two or more series: identity should never rest on
 * colour matching alone.
 */
@Composable
fun ChartLegend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(item.color))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

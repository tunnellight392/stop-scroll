package com.tunnellight.stop_scroll.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tunnellight.stop_scroll.data.model.Comparison
import com.tunnellight.stop_scroll.ui.model.AppUsageRow
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors
import com.tunnellight.stop_scroll.ui.theme.TabularNumbers
import com.tunnellight.stop_scroll.util.Format

/** The standard container: one idea per card, generous padding, no heavy chrome. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

/**
 * A signed change against a named period.
 *
 * Less scrolling is the good direction, so a fall is coloured "good" and a rise "critical" —
 * and both are spelled out in words next to the arrow, because a status colour must never be
 * the only thing carrying the meaning.
 */
@Composable
fun DeltaChip(comparison: Comparison, modifier: Modifier = Modifier) {
    val chart = LocalChartColors.current
    val delta = comparison.deltaMs
    val neutral = !comparison.hasAnythingToCompare || delta == 0L
    val color = when {
        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        delta < 0 -> chart.good
        else -> chart.critical
    }
    val arrow = when {
        neutral -> "•"
        delta < 0 -> "▼"
        else -> "▲"
    }
    val text = Format.comparisonText(comparison)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(arrow, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Label, value, optional footnote. The form to reach for before drawing a one-bar chart. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AppIcon(icon: ImageBitmap?, label: String, tint: Color, size: Dp = 34.dp) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

/**
 * One app in a breakdown list. The name sits beside the coloured mark, so identity never
 * depends on matching a hue to a legend.
 */
@Composable
fun AppUsageRowItem(
    row: AppUsageRow,
    maxMs: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val chart = LocalChartColors.current
    val color = chart.forSlot(row.colorSlot)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.icon, row.label, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Format.duration(row.totalMs),
                    style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            RowBar(
                fraction = if (maxMs <= 0L) 0f else row.totalMs.toFloat() / maxMs,
                color = color,
            )
            val share = row.scrollShareOfApp
            if (share != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "${Format.percent(share)} of your ${Format.duration(row.foregroundMs)} in the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Used wherever a screen has nothing to show yet. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

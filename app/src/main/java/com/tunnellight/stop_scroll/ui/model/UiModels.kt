package com.tunnellight.stop_scroll.ui.model

import androidx.compose.ui.graphics.ImageBitmap
import com.tunnellight.stop_scroll.data.model.FeedSurface

/**
 * One app as the UI shows it. [colorSlot] is a palette slot rather than a colour so the view
 * model stays free of theme types and the same row renders correctly in light and dark.
 */
data class AppUsageRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val totalMs: Long,
    val share: Float,
    val colorSlot: Int,
    val sessions: Int,
    /** Total foreground time from usage stats, or 0 when that permission is not granted. */
    val foregroundMs: Long,
) {
    /** What share of the time in this app was spent scrolling. Null when unknown. */
    val scrollShareOfApp: Float?
        get() = if (foregroundMs <= 0L) null else (totalMs.toFloat() / foregroundMs).coerceAtMost(1f)
}

data class SurfaceSlice(val surface: FeedSurface, val totalMs: Long) {
    val label: String
        get() = when (surface) {
            FeedSurface.SHORT_VIDEO -> "Short video"
            FeedSurface.FEED -> "Feeds"
            FeedSurface.OTHER -> "Elsewhere"
        }
}

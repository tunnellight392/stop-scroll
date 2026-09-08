package com.tunnellight.stop_scroll.service

import com.tunnellight.stop_scroll.data.model.AppCatalog
import com.tunnellight.stop_scroll.data.model.FeedSurface

/**
 * Works out which surface a scroll happened on from the scrolling view itself.
 *
 * An accessibility scroll event carries the view that scrolled, so its resource id
 * (`com.google.android.youtube:id/reel_recycler`) and class name identify the container the
 * finger is on. That is both cheaper and more precise than walking the window tree: it is a
 * single node, and it tells Reels apart from the Instagram grid even though both live in the
 * same activity.
 */
object SurfaceClassifier {

    fun classify(packageName: String, viewId: String?, className: String?): FeedSurface {
        val app = AppCatalog.find(packageName)
        if (app?.alwaysShortVideo == true) return FeedSurface.SHORT_VIDEO
        if (app == null) return FeedSurface.FEED

        val haystack = buildString {
            viewId?.let { append(it.lowercase()); append(' ') }
            className?.let { append(it.lowercase()) }
        }
        if (haystack.isBlank()) return FeedSurface.FEED

        if (app.shortVideoMarkers.any { haystack.contains(it) }) return FeedSurface.SHORT_VIDEO
        if (app.feedMarkers.any { haystack.contains(it) }) return FeedSurface.FEED

        // The app is known but this is not one of its recognised feeds — settings, DMs,
        // search results. Still the user's time, but not attributed to a feed.
        return if (app.shortVideoMarkers.isEmpty() && app.feedMarkers.isEmpty()) {
            FeedSurface.FEED
        } else {
            FeedSurface.OTHER
        }
    }
}

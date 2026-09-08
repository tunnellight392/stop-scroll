package com.tunnellight.stop_scroll.service

import com.tunnellight.stop_scroll.data.model.FeedSurface
import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceClassifierTest {

    @Test
    fun `tiktok is short video whatever scrolled`() {
        assertEquals(
            FeedSurface.SHORT_VIDEO,
            SurfaceClassifier.classify("com.zhiliaoapp.musically", null, null),
        )
    }

    @Test
    fun `the youtube shorts player is told apart from the rest of youtube`() {
        assertEquals(
            FeedSurface.SHORT_VIDEO,
            SurfaceClassifier.classify(
                packageName = "com.google.android.youtube",
                viewId = "com.google.android.youtube:id/reel_recycler",
                className = "androidx.recyclerview.widget.RecyclerView",
            ),
        )
        assertEquals(
            FeedSurface.FEED,
            SurfaceClassifier.classify(
                packageName = "com.google.android.youtube",
                viewId = "com.google.android.youtube:id/results",
                className = "androidx.recyclerview.widget.RecyclerView",
            ),
        )
    }

    @Test
    fun `instagram reels and the instagram feed are different surfaces`() {
        assertEquals(
            FeedSurface.SHORT_VIDEO,
            SurfaceClassifier.classify(
                "com.instagram.android",
                "com.instagram.android:id/clips_viewer_view_pager",
                null,
            ),
        )
        assertEquals(
            FeedSurface.FEED,
            SurfaceClassifier.classify(
                "com.instagram.android",
                "com.instagram.android:id/feed_recycler_view",
                null,
            ),
        )
    }

    @Test
    fun `scrolling somewhere unrecognised in a known app is not credited to a feed`() {
        assertEquals(
            FeedSurface.OTHER,
            SurfaceClassifier.classify(
                "com.instagram.android",
                "com.instagram.android:id/settings_list",
                "android.widget.ListView",
            ),
        )
    }

    @Test
    fun `an app the user added themselves counts as a feed`() {
        assertEquals(
            FeedSurface.FEED,
            SurfaceClassifier.classify("com.example.someapp", "com.example.someapp:id/list", null),
        )
    }

    @Test
    fun `an app with no markers of its own counts as a feed`() {
        assertEquals(
            FeedSurface.FEED,
            SurfaceClassifier.classify("com.linkedin.android", null, null),
        )
    }
}

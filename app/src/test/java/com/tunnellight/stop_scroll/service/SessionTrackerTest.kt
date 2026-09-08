package com.tunnellight.stop_scroll.service

import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.ScrollSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {

    private val config = TrackerConfig(
        feedIdleGapMs = 45_000L,
        shortVideoIdleGapMs = 120_000L,
        feedDwellMs = 3_000L,
        shortVideoDwellMs = 8_000L,
        minScrollCount = 2,
        checkpointIntervalMs = 60_000L,
    )

    private class Recorder {
        val finals = mutableListOf<ScrollSession>()
        val checkpoints = mutableListOf<ScrollSession>()
        fun record(session: ScrollSession, isFinal: Boolean) {
            if (isFinal) finals += session else checkpoints += session
        }
    }

    private fun tracker(recorder: Recorder) = SessionTracker(config, recorder::record)

    @Test
    fun `a single flick is not a scrolling bout`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 1_000L, 500)
        tracker.flush(100_000L)

        assertTrue(recorder.finals.isEmpty())
    }

    @Test
    fun `continuous scrolling is one bout credited to the last scroll plus dwell`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 10_000L, 400)
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 12_000L, 600)
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 20_000L, 300)
        // Long after the idle window, so the tick closes it.
        tracker.tick(200_000L)

        assertEquals(1, recorder.finals.size)
        val session = recorder.finals.single()
        assertEquals(10_000L, session.startTime)
        // Ends at the last scroll plus the feed dwell credit, not at the moment we noticed.
        assertEquals(23_000L, session.endTime)
        assertEquals(13_000L, session.durationMs)
        assertEquals(3, session.scrollCount)
        assertEquals(1300L, session.scrollPx)
    }

    @Test
    fun `a gap longer than the idle window splits the bout in two`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 0L, 100)
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 1_000L, 100)
        // 46s later: past the 45s feed window.
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 47_000L, 100)
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 48_000L, 100)
        tracker.flush(200_000L)

        assertEquals(2, recorder.finals.size)
        assertEquals(0L, recorder.finals[0].startTime)
        assertEquals(47_000L, recorder.finals[1].startTime)
    }

    @Test
    fun `short video tolerates a gap that would end a feed bout`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.zhiliaoapp.musically", FeedSurface.SHORT_VIDEO, 0L, 1_000)
        // 60s between swipes: over the feed window, well inside the short-video one.
        tracker.onScroll("com.zhiliaoapp.musically", FeedSurface.SHORT_VIDEO, 60_000L, 1_000)
        tracker.flush(300_000L)

        assertEquals(1, recorder.finals.size)
        val session = recorder.finals.single()
        assertEquals(0L, session.startTime)
        assertEquals(68_000L, session.endTime)
    }

    @Test
    fun `switching app closes the previous bout`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 0L, 100)
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 1_000L, 100)
        tracker.onScroll("com.instagram.android", FeedSurface.FEED, 2_000L, 100)
        tracker.onScroll("com.instagram.android", FeedSurface.FEED, 3_000L, 100)
        tracker.flush(100_000L)

        assertEquals(2, recorder.finals.size)
        assertEquals("com.reddit.frontpage", recorder.finals[0].packageName)
        assertEquals("com.instagram.android", recorder.finals[1].packageName)
    }

    @Test
    fun `moving from the feed into reels starts a new bout on the new surface`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.instagram.android", FeedSurface.FEED, 0L, 100)
        tracker.onScroll("com.instagram.android", FeedSurface.FEED, 1_000L, 100)
        tracker.onScroll("com.instagram.android", FeedSurface.SHORT_VIDEO, 2_000L, 100)
        tracker.onScroll("com.instagram.android", FeedSurface.SHORT_VIDEO, 3_000L, 100)
        tracker.flush(100_000L)

        assertEquals(2, recorder.finals.size)
        assertEquals(FeedSurface.FEED, recorder.finals[0].surface)
        assertEquals(FeedSurface.SHORT_VIDEO, recorder.finals[1].surface)
    }

    @Test
    fun `screen off ends the bout there and then`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 0L, 100)
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 1_000L, 100)
        // Screen goes off before the dwell credit has run out, so the bout stops there.
        tracker.onScreenOff(2_000L)

        assertEquals(1, recorder.finals.size)
        assertEquals(2_000L, recorder.finals.single().endTime)
    }

    @Test
    fun `a long bout is check-pointed so a service restart cannot lose it`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.zhiliaoapp.musically", FeedSurface.SHORT_VIDEO, 0L, 100)
        tracker.onScroll("com.zhiliaoapp.musically", FeedSurface.SHORT_VIDEO, 5_000L, 100)
        tracker.tick(61_000L)

        assertEquals(1, recorder.checkpoints.size)
        assertTrue(recorder.finals.isEmpty())
        // The check-point shares the start time, so the final write replaces it rather than
        // adding a second row.
        assertEquals(0L, recorder.checkpoints.single().startTime)
    }

    @Test
    fun `a short-video feed is timed by presence, because it reports no scrolling`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        // YouTube Shorts emits no scroll events at all, so these are window-probe sightings.
        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 0L)
        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 2_000L)
        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 30_000L)
        tracker.flush(300_000L)

        assertEquals(1, recorder.finals.size)
        val session = recorder.finals.single()
        assertEquals(0L, session.startTime)
        assertEquals(38_000L, session.endTime)
        // No swipe was ever reported, so the distance stays honestly at zero.
        assertEquals(0, session.scrollCount)
        assertEquals(0L, session.scrollPx)
    }

    @Test
    fun `a single sighting is not a bout`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 0L)
        tracker.flush(300_000L)

        assertTrue(recorder.finals.isEmpty())
    }

    @Test
    fun `presence keeps a short-video bout alive across long gaps between sightings`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        // Sightings 40s apart: past the 45s feed gap in aggregate, comfortably inside the
        // 120s short-video one, so this stays a single bout.
        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 0L)
        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 40_000L)
        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 80_000L)
        tracker.flush(400_000L)

        assertEquals(1, recorder.finals.size)
        assertEquals(88_000L, recorder.finals.single().endTime)
    }

    @Test
    fun `isOpenOn reports the surface the live bout is on`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 0L)
        tracker.onDwell("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 1_000L)

        assertTrue(tracker.isOpenOn("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 5_000L))
        assertFalse(tracker.isOpenOn("com.google.android.youtube", FeedSurface.FEED, 5_000L))
        assertFalse(tracker.isOpenOn("com.reddit.frontpage", FeedSurface.SHORT_VIDEO, 5_000L))
        assertFalse(
            tracker.isOpenOn("com.google.android.youtube", FeedSurface.SHORT_VIDEO, 300_000L),
        )
    }

    @Test
    fun `activeBoutMs reports zero once the bout has gone idle`() {
        val recorder = Recorder()
        val tracker = tracker(recorder)

        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 0L, 100)
        tracker.onScroll("com.reddit.frontpage", FeedSurface.FEED, 1_000L, 100)

        assertEquals(5_000L, tracker.activeBoutMs(5_000L))
        assertEquals(0L, tracker.activeBoutMs(200_000L))
    }
}

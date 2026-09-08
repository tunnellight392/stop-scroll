package com.tunnellight.stop_scroll.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Answers one question about the window on screen: is a short-video feed open in it?
 *
 * This exists because full-screen video pagers do not report scrolling. A dump of YouTube
 * Shorts contains no node with `scrollable="true"` anywhere — the pager is not exposed as an
 * accessibility-scrollable container, so `TYPE_VIEW_SCROLLED` is never emitted no matter how
 * many times you swipe. What Shorts *does* expose is its view ids, so the presence of
 * `reel_recycler` (or `reel_player_page_container`) is the available evidence that the user is
 * in the feed. Instagram Reels and TikTok use the same full-screen pager pattern.
 *
 * The search is deliberately bounded and looks only at [AccessibilityNodeInfo.viewIdResourceName]
 * — never at text, content descriptions, or anything a user typed or is reading.
 */
object ShortVideoDetector {

    /** A Shorts window is a few hundred nodes; this caps the cost of a pathological tree. */
    const val MAX_NODES = 500
    const val MAX_DEPTH = 16

    fun isOnShortVideoSurface(root: AccessibilityNodeInfo?, markers: List<String>): Boolean {
        if (root == null || markers.isEmpty()) return false

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        val obtained = ArrayList<AccessibilityNodeInfo>()
        queue.addLast(root to 0)
        var visited = 0

        try {
            while (queue.isNotEmpty() && visited < MAX_NODES) {
                val (node, depth) = queue.removeFirst()
                visited++

                val viewId = node.viewIdResourceName?.lowercase()
                if (viewId != null && markers.any { viewId.contains(it) }) return true

                if (depth >= MAX_DEPTH) continue
                for (index in 0 until node.childCount) {
                    val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                    obtained += child
                    queue.addLast(child to depth + 1)
                }
            }
            return false
        } finally {
            // The root belongs to the caller; only children obtained here are released, and
            // only on the versions where recycling still does anything.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                obtained.forEach {
                    @Suppress("DEPRECATION")
                    runCatching { it.recycle() }
                }
            }
        }
    }
}

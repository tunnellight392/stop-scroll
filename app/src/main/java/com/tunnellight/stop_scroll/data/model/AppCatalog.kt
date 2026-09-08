package com.tunnellight.stop_scroll.data.model

/**
 * A social app StopScroll knows how to read.
 *
 * [shortVideoMarkers] are substrings matched (lower-cased) against the *scrolling view's*
 * `viewIdResourceName` and class name, which the accessibility event hands us directly.
 * That is far cheaper than walking the window tree and it identifies the exact container
 * the finger is on, which is what separates "watching Reels" from "reading the Instagram feed".
 */
data class KnownApp(
    val packageName: String,
    val label: String,
    val alwaysShortVideo: Boolean = false,
    val shortVideoMarkers: List<String> = emptyList(),
    val feedMarkers: List<String> = emptyList(),
)

object AppCatalog {

    val apps: List<KnownApp> = listOf(
        KnownApp(
            packageName = "com.google.android.youtube",
            label = "YouTube",
            shortVideoMarkers = listOf("reel", "shorts"),
            feedMarkers = listOf("results", "browse", "watch_list", "comment"),
        ),
        KnownApp(
            packageName = "com.instagram.android",
            label = "Instagram",
            shortVideoMarkers = listOf("clips", "reel"),
            feedMarkers = listOf("feed", "explore", "grid", "profile"),
        ),
        KnownApp(
            packageName = "com.zhiliaoapp.musically",
            label = "TikTok",
            alwaysShortVideo = true,
        ),
        KnownApp(
            packageName = "com.ss.android.ugc.trill",
            label = "TikTok",
            alwaysShortVideo = true,
        ),
        KnownApp(
            packageName = "com.reddit.frontpage",
            label = "Reddit",
            shortVideoMarkers = listOf("video_feed", "immersive"),
            feedMarkers = listOf("link_list", "comment", "feed"),
        ),
        KnownApp(
            packageName = "com.facebook.katana",
            label = "Facebook",
            shortVideoMarkers = listOf("reel", "video_home"),
            feedMarkers = listOf("feed", "newsfeed"),
        ),
        KnownApp(
            packageName = "com.snapchat.android",
            label = "Snapchat",
            shortVideoMarkers = listOf("spotlight", "discover"),
        ),
        KnownApp(
            packageName = "com.twitter.android",
            label = "X",
            shortVideoMarkers = listOf("immersive", "video_pager"),
            feedMarkers = listOf("timeline", "tweet"),
        ),
        KnownApp(
            packageName = "com.instagram.barcelona",
            label = "Threads",
            feedMarkers = listOf("feed", "thread"),
        ),
        KnownApp(
            packageName = "com.pinterest",
            label = "Pinterest",
            shortVideoMarkers = listOf("idea_pin", "watch"),
            feedMarkers = listOf("grid", "feed"),
        ),
        KnownApp(
            packageName = "com.linkedin.android",
            label = "LinkedIn",
            feedMarkers = listOf("feed", "update"),
        ),
        KnownApp(
            packageName = "com.netflix.mediaclient",
            label = "Netflix",
            shortVideoMarkers = listOf("fast_laugh"),
        ),
    )

    private val byPackage: Map<String, KnownApp> = apps.associateBy { it.packageName }

    /** Packages StopScroll turns on for a first-time user, if they are installed. */
    val defaultPackages: Set<String> = apps.map { it.packageName }.toSet()

    fun find(packageName: String): KnownApp? = byPackage[packageName]

    fun labelFor(packageName: String): String? = byPackage[packageName]?.label
}

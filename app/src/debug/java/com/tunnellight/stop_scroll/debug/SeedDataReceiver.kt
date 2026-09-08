package com.tunnellight.stop_scroll.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tunnellight.stop_scroll.appContainer
import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.ScrollSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Development-only. Fills the database with plausible history so the weekly, monthly and
 * trend screens can be looked at without waiting weeks for real data to accumulate.
 *
 * This lives in the debug source set, so it is not compiled into a release build at all.
 *
 *     adb shell am broadcast -a com.tunnellight.stop_scroll.SEED \
 *         -n com.tunnellight.stop_scroll/.debug.SeedDataReceiver
 *
 * Pass `--ez clear true` to wipe existing rows first.
 */
class SeedDataReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = context.appContainer
        val clear = intent.getBooleanExtra("clear", false)
        val days = intent.getIntExtra("days", 120)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (clear) container.repository.clearAll()
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val random = Random(seed = 20260907)
                var written = 0

                for (back in 0 until days) {
                    val date = today.minusDays(back.toLong())
                    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val weekend = date.dayOfWeek.value >= 6
                    // A slow downward drift over time, so week-over-week and month-over-month
                    // have something real to report.
                    val drift = 1.0 + (back / 160.0)
                    val bouts = ((if (weekend) 11 else 7) * drift).toInt() + random.nextInt(-2, 4)

                    repeat(bouts.coerceAtLeast(1)) {
                        val app = APPS[random.nextInt(APPS.size)]
                        val hour = HOURS[random.nextInt(HOURS.size)]
                        val startOffset = hour * 3_600_000L + random.nextLong(0, 3_600_000L)
                        val start = dayStart + startOffset
                        if (start > System.currentTimeMillis()) return@repeat

                        val shortVideo = app.second
                        val lengthMs = if (shortVideo) {
                            random.nextLong(90_000L, 1_800_000L)
                        } else {
                            random.nextLong(30_000L, 720_000L)
                        }
                        val scrolls = (lengthMs / (if (shortVideo) 12_000L else 2_500L)).toInt() + 2
                        container.repository.persist(
                            ScrollSession(
                                startTime = start,
                                endTime = minOf(start + lengthMs, System.currentTimeMillis()),
                                packageName = app.first,
                                surface = if (shortVideo) FeedSurface.SHORT_VIDEO else FeedSurface.FEED,
                                scrollCount = scrolls,
                                scrollPx = scrolls * random.nextLong(600L, 1_800L),
                            ),
                            zone,
                        )
                        written++
                    }
                }
                Log.i(TAG, "Seeded $written sessions across $days days")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SeedDataReceiver"

        /** package to "is a short-video feed". */
        val APPS = listOf(
            "com.instagram.android" to true,
            "com.instagram.android" to false,
            "com.google.android.youtube" to true,
            "com.reddit.frontpage" to false,
            "com.zhiliaoapp.musically" to true,
            "com.facebook.katana" to false,
            "com.twitter.android" to false,
            "com.linkedin.android" to false,
            "com.pinterest" to false,
        )

        /** Morning, lunchtime, afternoon lull, and the evening peak. */
        val HOURS = intArrayOf(7, 8, 9, 12, 13, 15, 17, 19, 20, 21, 21, 22, 22, 23)
    }
}

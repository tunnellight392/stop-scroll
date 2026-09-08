package com.tunnellight.stop_scroll.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tunnellight.stop_scroll.ui.components.SectionCard
import com.tunnellight.stop_scroll.ui.theme.LocalChartColors

@Composable
fun SetupScreen(onEnable: () -> Unit, onSkip: () -> Unit) {
    val chart = LocalChartColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "StopScroll",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Find out how much of your day actually goes into the feed.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        SectionCard(title = "What it measures") {
            Bullet(
                heading = "Scrolling, not just screen time",
                body = "Time is counted while you are actually moving the feed — Reddit, " +
                    "YouTube Shorts, Instagram Reels, TikTok, Facebook and any other app " +
                    "you add.",
            )
            Bullet(
                heading = "Short video is timed differently",
                body = "One swipe on Reels holds you far longer than one flick on Reddit, so " +
                    "each kind of feed gets its own timing rules.",
            )
            Bullet(
                heading = "Daily, weekly and monthly",
                body = "Per-app breakdowns, week-over-week and month-over-month comparisons, " +
                    "and a calendar of the whole month.",
            )
        }

        Spacer(Modifier.height(14.dp))
        SectionCard(title = "What it never does") {
            Text(
                text = "StopScroll needs Android's accessibility service to notice a scroll " +
                    "gesture. It only ever asks two things of that service: which app scrolled, " +
                    "and how far. It does not read text, capture the screen, or send anything " +
                    "anywhere — there is no network permission in this app at all.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "The service is also told to watch only the apps you switch on, so " +
                    "everything else is filtered out before it reaches StopScroll.",
                style = MaterialTheme.typography.bodySmall,
                color = chart.axisText,
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
            Text("Turn on tracking")
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onSkip) { Text("Look around first") }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Find StopScroll under Installed apps in the accessibility settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Bullet(heading: String, body: String) {
    Row(Modifier.padding(bottom = 14.dp)) {
        Text("—", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(heading, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

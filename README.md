# StopScroll

An Android app that measures how much of your day goes into scrolling feeds — Reddit,
YouTube Shorts, Instagram Reels, TikTok, Facebook and anything else you add — and reports it
daily, weekly and monthly with week-over-week and month-over-month comparisons.

## What makes the number mean something

Screen-time tools measure *time in an app*. StopScroll measures *time spent scrolling*, which
is a different and more useful number: 40 minutes in Instagram might be five minutes of
messaging and thirty-five of feed.

**Detection.** An `AccessibilityService` watches only the packages you switch on — the platform
filters everything else out before it reaches the process. Two signals are needed, because two
kinds of feed exist:

- **List-shaped feeds** (Reddit, the YouTube home feed) report scrolling. `TYPE_VIEW_SCROLLED`
  carries the timing and the distance, and the event names the view that scrolled, so its
  resource id identifies the surface without walking the tree.
- **Full-screen video feeds** (Shorts, Reels, TikTok) report nothing. A dump of YouTube Shorts
  contains no node marked `scrollable="true"` anywhere: the pager is not exposed as an
  accessibility-scrollable container, so no amount of swiping emits an event. There the feed is
  found by its view id (`reel_recycler`) and timed by being on screen. That number means "time
  in the feed" rather than "time with your thumb moving" — which is the number you want anyway,
  since most of a Shorts session is watching. Those bouts report no scroll distance, because
  none was ever measured.

**Timing.** `SessionTracker` folds the event stream into bouts. Short-video feeds get a longer
idle window and more dwell credit than text feeds, because one swipe on TikTok holds attention
far longer than one flick on Reddit; a single threshold for both would either shred TikTok
sessions into fragments or credit a Reddit glance as a minute of scrolling. A bout's end is
always derived from its last scroll plus the dwell credit, never from when the tracker noticed
it had ended — so noticing late costs nothing in accuracy.

**Honest comparisons.** While a period is still running, the earlier period is clipped to the
same elapsed span. A Tuesday-morning "this week" measured against a complete previous week
would always show a flattering drop. Once the period is finished, both are taken whole.

## Privacy

The app has **no internet permission**. Nothing is read from the screen: only which app
scrolled, when, and how far. All data lives in a Room database on the device, with a
configurable retention window and a delete-everything button.
See the full [privacy policy](PRIVACY_POLICY.md).

## Layout

```
data/       Room entities, DAO, repository, settings store, usage-stats reader
analytics/  Aggregator and PeriodMath — pure functions, unit tested
service/    The accessibility service, SessionTracker, SurfaceClassifier, notifications
ui/         Compose Material 3 screens, charts drawn on Canvas, theme
```

Charts follow one palette across the app: eight categorical slots, validated as a set for
colour-blind separation and stepped separately for light and dark surfaces. A package keeps
the slot it was first given, so filtering a chart never repaints the series that remain, and
the ninth app onwards folds into a neutral "Other" rather than getting a generated hue.

## Building

```
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # 29 unit tests over the timing and aggregation logic
./gradlew installDebug         # install to a connected device or emulator
```

Requires JDK 17+ (Android Studio's bundled JBR works) and Android SDK 37.

## Running it

1. Install and open the app.
2. **Turn on tracking** — this opens Accessibility settings; find StopScroll under
   *Installed apps* and switch it on.
3. Optionally grant **usage access** in Settings, which adds the "31 of your 42 Instagram
   minutes were spent scrolling" figures.
4. Pick apps under the **Apps** tab. The known social apps are on by default; any installed
   app can be added.

## Development

Debug builds include a seeder that fills the database with plausible history so the weekly,
monthly and trend screens can be reviewed without waiting weeks. It is in the `debug` source
set and is not compiled into a release build.

```
adb shell am broadcast -a com.tunnellight.stop_scroll.SEED \
    -n com.tunnellight.stop_scroll/.debug.SeedDataReceiver --ez clear true
```

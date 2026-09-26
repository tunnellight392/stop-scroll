# StopScroll Privacy Policy

**Effective date:** 26 September 2026

StopScroll ("the app") is an Android app that measures how much time you spend scrolling
feeds in the apps you choose. This policy explains what the app records, where that
information is kept, and what control you have over it.

## Summary

- StopScroll has **no internet permission**. It cannot send anything off your device, and
  the developer never receives any of your data.
- It records **when** you scrolled, **in which app**, and **how far** — never the text,
  images, videos, messages or accounts shown on your screen.
- Everything is stored **only on your device**, and you can delete it at any time.
- There are no accounts, ads, analytics, crash reporting or third-party SDKs.

## What the app records

### Scroll sessions

For each bout of scrolling in an app you have switched on, StopScroll stores:

- the app's package name (for example `com.instagram.android`)
- the start and end time of the bout, and the calendar day it fell on
- the kind of surface it happened on — a short-video feed (such as Shorts or Reels), a
  regular feed, or elsewhere in the app
- how many scroll events occurred and how far the list moved, in pixels

### Settings

Your preferences are stored on the device: which apps are tracked, your daily goal,
alert settings, how long to keep history, the colour assigned to each app in charts, and
when the last alert was shown (so the same alert is not repeated).

## How the app uses Android permissions

### Accessibility service

StopScroll's core function relies on an Android accessibility service, which you must
switch on yourself in system settings. It is used solely to detect scrolling in the apps
you have chosen:

- Android delivers events to StopScroll only for the apps you have switched on.
- From those events the app uses which app is in front, the timing and distance of
  scrolls, and the internal identifier (view ID) and type of the on-screen element that
  scrolled. A view ID is a name given by the app's developer, such as `reel_recycler`; it
  is how the app tells a Reels or Shorts feed apart from a regular feed.
- The app does **not** read, store or transmit on-screen text, images, video, content
  descriptions, typed input, passwords, messages or account information.
- The service does not perform any actions on your behalf and does not change any settings.

### Usage access (optional)

If you grant usage access, StopScroll reads how long each tracked app was in the
foreground, so it can show how much of that time was spent scrolling. This is read from
Android when a screen needs it and is not stored by the app. StopScroll works without this
permission.

### Notifications (optional)

If allowed, StopScroll shows local notifications when you reach your daily goal or have
been scrolling continuously for a long time. These are generated on the device; no push
service is involved.

### Listing installed apps

To let you choose which apps to track, StopScroll lists the apps on your device that have
a launcher icon. This list is shown to you and is not stored, apart from the package names
you choose to track.

## Where your data is stored

All data is kept in the app's private storage on your device, which other apps cannot
access. StopScroll does not request the internet permission, so it has no way to upload
your data, and the developer does not collect, receive, sell or share any of it.

### Android backup

StopScroll allows Android's standard backup. If you have enabled device backup (for
example, Google backup), Android may include the app's data in your backup and restore it
to a new device. This backup is handled by Android and your backup provider under your
account and their privacy terms; the developer has no access to it. You can turn device
backup off in your phone's system settings.

## Data retention and deletion

- **Automatic deletion:** in Settings you can choose to keep history for 30 days, 90 days,
  1 year (the default) or forever. Older sessions are removed automatically.
- **Delete everything:** Settings → *Delete all scroll history* permanently removes every
  recorded session from the device.
- **Uninstalling** the app removes all of its data from the device.

## Children

StopScroll is not directed at children under 13 and does not knowingly collect personal
information from anyone. Because no data leaves the device, no information about any
user, of any age, is collected by the developer.

## Changes to this policy

If this policy changes, the updated version will be published at the same location with a
new effective date. Significant changes — such as the app ever gaining the ability to send
data off the device — would be disclosed in the app before they take effect.

## Contact

Questions about this policy can be sent to
[tunnellightt392@gmail.com](mailto:tunnellightt392@gmail.com), or raised by opening an issue
at <https://github.com/tunnellight392/stop-scroll/issues>.

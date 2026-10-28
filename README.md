# Liquid Glass Home (native Android launcher)

Native Kotlin + Jetpack Compose. No WebView.

## Features
- Liquid Glass UI, glass app tiles, drifting wallpaper (pauses after 25 s idle)
- Dynamic Island: real now-playing + controls, notification banners, incoming-call card, spring animated
- Always-on island over all apps (optional, in Island settings)
- App drawer: swipe up on any page (or tap the apps button in the dock), follows your finger, kinetic scrolling, live search. Charging pill on the island when you plug in.
- Real apps launched from tiles. Paged 4x6 glass app grid with glass page dots, home page with widgets, -1 screen (news cards from an RSS feed, set FEED_URL in Pages.kt)
- Pull down on the left half of the top edge: Notification Center. Right half: Control Center (Wi-Fi/Bluetooth open system panels, real flashlight, brightness and volume sliders)
- Island settings: permissions, always on, hide when idle, banners, calls, position over the camera, battery

## First run
1. Install, press Home, pick Liquid Glass as home app.
2. The settings screen opens by itself: allow Notification access, Display over other apps, Show notifications.
3. If Android says "Restricted setting": App info > ⋮ (top right) > Allow restricted settings, then allow again.

## Build
Push this folder to GitHub (include .github). Actions builds app-release.apk (optimized, signed with the bundled key).
The bundled key means new builds install over old ones. The very first time, uninstall the old debug build.

## Customise (Settings: long-press empty home space, or the gear in the app drawer)
Icons (style, size, roundness, tint, names), wallpaper (phone's own / gradient / your photo, dim, blur),
island (colour, opacity, position), widgets page (real Android widgets), pull-down panels (phone's own or Liquid Glass),
performance (battery saver / balanced / full), HyperOS shortcuts. A Dynamic Island tile can be added to the phone's quick settings.


## HyperOS-style rebuild
- Home page: weather card, analog clock, world clock, glossy real app icons straight on a soft pastel wallpaper.
- Smart page (swipe right from home): music card, brightness + volume capsules, calendar, clock, search, your Android widgets.
- Control Center: common toggles, paired Bluetooth devices, brightness and volume sliders, mini player.
- Glass: milky and frosted variants with rim light, content plate and a clipped-out ambient shadow.
- Needs permissions for: location (weather), calendar, Bluetooth devices, notification access (island, notifications).

# Liquid Glass Home (native Android launcher)

Native Kotlin + Jetpack Compose. No WebView.

## Features
- Liquid Glass UI, glass app tiles, drifting wallpaper (pauses after 25 s idle)
- Dynamic Island: real now-playing + controls, notification banners, incoming-call card, spring animated
- Always-on island over all apps (optional, in Island settings)
- Real apps launched from tiles, swipe-up app drawer with every installed app + search
- Island settings: permissions, always on, hide when idle, banners, calls, position over the camera, battery

## First run
1. Install, press Home, pick Liquid Glass as home app.
2. The settings screen opens by itself: allow Notification access, Display over other apps, Show notifications.
3. If Android says "Restricted setting": App info > ⋮ (top right) > Allow restricted settings, then allow again.

## Build
Push this folder to GitHub (include .github). Actions builds app-release.apk (optimized, signed with the bundled key).
The bundled key means new builds install over old ones. The very first time, uninstall the old debug build.

# Liquid Glass Home (native Android launcher)

Native Kotlin + Jetpack Compose home screen. No WebView.

## What it does
- Liquid Glass UI: glass panels, glass app tiles with glare, drifting colour wallpaper (pauses after 25 s idle to save battery)
- Dynamic Island around the camera: real now-playing info and controls (play/pause/skip/seek, album art, visualizer),
  notification banners, incoming-call card. Spring animated.
- Real apps: tiles launch the real Instagram / Facebook / YouTube / WhatsApp / Spotify (if installed) with a scale-up window animation.
  Swipe up (or tap the handle) for the app drawer with every installed app and search.
- Clock and real battery ring widgets. (Weather is a placeholder.)
- Registers as a home app.

## First run
1. Install the APK, press Home, choose Liquid Glass as the home app (or Settings > Apps > Default apps > Home app).
2. Tap "Allow music & notifications on the island" at the bottom and enable Liquid Glass in Notification access.
   Android may ask you to allow restricted settings: App info > ⋮ > Allow restricted settings.

## Build
GitHub: push this folder, the workflow in .github/workflows/build.yml builds app-debug.apk (Actions > Artifacts).
Android Studio: open folder, Run.

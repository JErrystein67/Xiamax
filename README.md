# Liquid Glass Home (prototype launcher)

Android 15 style home screen: Liquid Glass UI, Dynamic Island music hub, glass app icons.
The UI is the HTML prototype (app/src/main/assets/index.html) running full-screen in a WebView.

## Build the APK
1. Android Studio -> Open -> select this folder, let Gradle sync (needs internet once).
2. Plug in a phone (USB debugging on) or start an emulator, press Run.
   Or: Build > Build APK(s) -> app/build/outputs/apk/debug/app-debug.apk

## Use it as your home screen
Settings > Apps > Default apps > Home app > Liquid Glass.
Tapping a tile plays the open animation, then launches the real app (Instagram, Facebook, YouTube, WhatsApp, Spotify, Photos, Maps, Notes) or the system Phone, Messages, Camera, Settings. If an app is not installed, it shows the demo screen instead.
To add or swap an app, edit the `apps` / `dock` arrays in assets/index.html (last field = package name, e.g. `pkg:com.whatsapp`).

## Tweaks
- Island position over your camera: `#island{top:11px}` in assets/index.html
- Fonts load from Google Fonts when online, otherwise it falls back to the system font.

## Get the APK without Android Studio
1. Create a new GitHub repo and upload everything in this folder (keep the .github folder).
2. Open the Actions tab -> "Build APK" runs automatically (or press Run workflow).
3. When it finishes, download the `LiquidGlassHome-apk` artifact, unzip it, and install app-debug.apk on your phone
   (allow "install unknown apps" for your browser or file manager).

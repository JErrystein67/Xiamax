package com.example.liquidglass

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class AppItem(val label: String, val pkg: String, val id: String, val icon: ImageBitmap?, val intent: Intent)

class Brand(val glyph: Glyph, val c1: Color, val c2: Color, vararg val pkgs: String)

val brands = listOf(
    Brand(Glyph.Instagram, Color(0xFFFF3D7F), Color(0xFF8A4BFF), "com.instagram.android"),
    Brand(Glyph.Facebook, Color(0xFF2F7BFF), Color(0xFF1A4FD6), "com.facebook.katana"),
    Brand(Glyph.YouTube, Color(0xFFFF3B3B), Color(0xFFB81D3A), "com.google.android.youtube"),
    Brand(Glyph.WhatsApp, Color(0xFF2FD47A), Color(0xFF15A05A), "com.whatsapp"),
    Brand(Glyph.Spotify, Color(0xFF22D86E), Color(0xFF0B6B4A), "com.spotify.music")
)

fun brandFor(pkg: String): Brand? = brands.firstOrNull { pkg in it.pkgs }

class Sys(val glyph: Glyph, val c1: Color, val c2: Color, val intent: Intent)

fun systemDock(): List<Sys> = listOf(
    Sys(Glyph.Phone, Color(0xFF2FD47A), Color(0xFF1B9D63), Intent(Intent.ACTION_DIAL)),
    Sys(Glyph.Messages, Color(0xFF3B82F6), Color(0xFF7C5CFF),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
    Sys(Glyph.Camera, Color(0xFF9AA3C8), Color(0xFF3D4466), Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
    Sys(Glyph.Settings, Color(0xFF8E98C4), Color(0xFF4A5280), Intent(Settings.ACTION_SETTINGS))
)

/** Every launchable app on the phone, with real labels and icons. */
fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(q, 0).mapNotNull { ri ->
        val pkg = ri.activityInfo.packageName
        if (pkg == ctx.packageName) return@mapNotNull null
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(pkg, ri.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val icon = try { ri.loadIcon(pm).toBitmap(120, 120).asImageBitmap() } catch (e: Exception) { null }
        AppItem(ri.loadLabel(pm).toString(), pkg, "$pkg/${ri.activityInfo.name}", icon, intent)
    }.sortedBy { it.label.lowercase() }
}

/** Starts the app with a real scale-up window animation growing out of the tapped tile. */
fun launchApp(ctx: Context, view: View, intent: Intent, r: Rect?) {
    try {
        val opts = if (r != null) {
            ActivityOptions.makeScaleUpAnimation(view, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt()).toBundle()
        } else null
        ctx.startActivity(intent, opts)
    } catch (e: Exception) {
        // app missing or blocked: ignore
    }
}

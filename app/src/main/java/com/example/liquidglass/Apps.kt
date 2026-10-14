package com.example.liquidglass

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * icon: for adaptive icons only the foreground layer (so no white square behind it),
 * c1/c2: glass tint taken from the icon's own colours.
 */
class AppItem(
    val label: String, val pkg: String, val id: String,
    val icon: ImageBitmap?, val adaptive: Boolean, val full: ImageBitmap?,
    val c1: Color, val c2: Color, val intent: Intent
)

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

private fun render(d: Drawable, px: Int): Bitmap {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    d.setBounds(0, 0, px, px)
    d.draw(c)
    return b
}

private fun averageColor(src: Bitmap): Color? {
    val n = 24
    val s = Bitmap.createScaledBitmap(src, n, n, true)
    var r = 0f; var g = 0f; var b = 0f; var w = 0f
    for (y in 0 until n) for (x in 0 until n) {
        val p = s.getPixel(x, y)
        val a = (p ushr 24) / 255f
        if (a < .2f) continue
        r += ((p shr 16) and 255) * a
        g += ((p shr 8) and 255) * a
        b += (p and 255) * a
        w += a
    }
    if (w < 1f) return null
    return Color(r / w / 255f, g / w / 255f, b / w / 255f)
}

private fun lum(c: Color) = .2126f * c.red + .7152f * c.green + .0722f * c.blue

private val neutral = Color(0xFF8E98C4)

/** Never tint with white / near-black: that is what produced the white boxes. */
private fun pickTint(a: Color?, b: Color?): Color {
    if (a != null && lum(a) in .10f..0.72f) return a
    if (b != null && lum(b) in .10f..0.72f) return b
    return neutral
}

private fun darker(c: Color) = Color(c.red * .55f, c.green * .55f, c.blue * .62f)

/** Every launchable app on the phone, with real labels and icons. */
fun loadApps(ctx: Context, withFull: Boolean = false): List<AppItem> {
    val pm = ctx.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(q, 0).mapNotNull { ri ->
        val pkg = ri.activityInfo.packageName
        if (pkg == ctx.packageName) return@mapNotNull null
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(pkg, ri.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        var icon: ImageBitmap? = null
        var full: ImageBitmap? = null
        var adaptive = false
        var tint = neutral
        try {
            val d = ri.loadIcon(pm)
            if (withFull) full = render(d, 144).asImageBitmap()
            if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
                val fg = d.foreground
                val bg = d.background
                val fgB = if (fg != null) render(fg, 128) else null
                val bgB = if (bg != null) render(bg, 48) else null
                icon = fgB?.asImageBitmap()
                adaptive = fgB != null
                tint = pickTint(bgB?.let { averageColor(it) }, fgB?.let { averageColor(it) })
            } else {
                val b = render(d, 128)
                icon = b.asImageBitmap()
                tint = pickTint(null, averageColor(b))
            }
        } catch (e: Exception) {
            // keep neutral tint and no icon
        }
        AppItem(ri.loadLabel(pm).toString(), pkg, "$pkg/${ri.activityInfo.name}", icon, adaptive, full, tint, darker(tint), intent)
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

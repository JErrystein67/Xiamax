package com.example.liquidglass

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Helpers for Xiaomi HyperOS 2 / 3 (Android 15 / 16). Everything is wrapped so a missing screen never crashes. */
object HyperOS {
    fun prop(key: String): String = try {
        val c = Class.forName("android.os.SystemProperties")
        c.getMethod("get", String::class.java, String::class.java).invoke(null, key, "") as String
    } catch (e: Throwable) {
        ""
    }

    fun isXiaomi(): Boolean = Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco")

    fun info(): String {
        val os = prop("ro.mi.os.version.name").ifEmpty { prop("ro.miui.ui.version.name") }
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" + if (os.isNotEmpty()) " · $os" else ""
    }

    private fun details(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        } catch (e: Exception) { }
    }

    fun openAutostart(ctx: Context) {
        val tries = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        )
        for (i in tries) {
            try { ctx.startActivity(i); return } catch (e: Exception) { }
        }
        details(ctx)
    }

    fun openBattery(ctx: Context) {
        val i = Intent()
            .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", ctx.packageName)
            .putExtra("package_label", "Liquid Glass")
        try { ctx.startActivity(i); return } catch (e: Exception) { }
        try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); return } catch (e: Exception) { }
        details(ctx)
    }
}

/** Quick Settings tile in the phone's own control center: turns the always-on island on and off. */
class IslandTileService : TileService() {
    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (Prefs.overlay) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }

    override fun onStartListening() {
        Prefs.init(applicationContext)
        refresh()
    }

    override fun onClick() {
        Prefs.init(applicationContext)
        if (!Settings.canDrawOverlays(this)) {
            val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(open)
            }
            return
        }
        Prefs.overlay = !Prefs.overlay
        Prefs.save()
        OverlayControl.apply(applicationContext)
        refresh()
    }
}

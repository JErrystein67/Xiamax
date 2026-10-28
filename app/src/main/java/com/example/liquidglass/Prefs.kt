package com.example.liquidglass

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object OverlayState {
    var running by mutableStateOf(false)
}

object Prefs {
    private var sp: SharedPreferences? = null

    // island
    var overlay by mutableStateOf(false)            // island above all apps
    var hideIdle by mutableStateOf(false)
    var banners by mutableStateOf(true)
    var calls by mutableStateOf(true)
    var offX by mutableFloatStateOf(0f)             // dp
    var offY by mutableFloatStateOf(0f)             // dp
    var islandColor by mutableIntStateOf(0xFF1D2B28.toInt())
    var islandOpacity by mutableFloatStateOf(.72f)

    // app icons
    var iconStyle by mutableIntStateOf(0)           // 0 glass, 1 flat, 2 original
    var iconSize by mutableFloatStateOf(58f)        // dp
    var iconRound by mutableFloatStateOf(.34f)      // corner radius as a fraction of the size
    var iconAlpha by mutableFloatStateOf(1f)        // glass tint strength
    var iconLabels by mutableStateOf(true)
    var iconColorMode by mutableIntStateOf(0)       // 0 automatic (each icon's own colours), 1 custom
    var iconColor1 by mutableIntStateOf(0xFF7C5CFF.toInt())
    var iconColor2 by mutableIntStateOf(0xFF2DE2C8.toInt())
    val appColors = mutableStateMapOf<String, Int>()  // per-app colour overrides

    // wallpaper
    var wallMode by mutableIntStateOf(1)            // 0 system wallpaper, 1 gradient, 2 photo
    var wallPalette by mutableIntStateOf(0)
    var wallAnim by mutableStateOf(false)           // only used in Full quality
    var wallDim by mutableFloatStateOf(0f)
    var wallBlur by mutableFloatStateOf(0f)         // dp, photo only
    var wallVersion by mutableIntStateOf(0)

    // system
    var shadeMode by mutableIntStateOf(0)           // 0 phone's own shade, 1 Liquid Glass panels
    var perf by mutableIntStateOf(1)                // 0 battery saver, 1 balanced, 2 full
    var firstRun by mutableStateOf(true)
    var worldZone by mutableStateOf("Europe/London") // second city on the world clock
    var leftPage by mutableIntStateOf(0)            // 0 smart widgets page, 1 opens the Google app

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
        sp = p
        overlay = p.getBoolean("overlay", false)
        hideIdle = p.getBoolean("hideIdle", false)
        banners = p.getBoolean("banners", true)
        calls = p.getBoolean("calls", true)
        offX = p.getFloat("offX", 0f)
        offY = p.getFloat("offY", 0f)
        islandColor = p.getInt("islandColor", 0xFF1D2B28.toInt())
        islandOpacity = p.getFloat("islandOpacity", .72f)
        iconStyle = p.getInt("iconStyle", 0)
        iconSize = p.getFloat("iconSize", 58f)
        iconRound = p.getFloat("iconRound", .34f)
        iconAlpha = p.getFloat("iconAlpha", 1f)
        iconLabels = p.getBoolean("iconLabels", true)
        iconColorMode = p.getInt("iconColorMode", 0)
        iconColor1 = p.getInt("iconColor1", 0xFF7C5CFF.toInt())
        iconColor2 = p.getInt("iconColor2", 0xFF2DE2C8.toInt())
        p.getString("appColors", "")?.split(";")?.forEach { e ->
            val kv = e.split("=")
            if (kv.size == 2) kv[1].toIntOrNull()?.let { appColors[kv[0]] = it }
        }
        wallMode = p.getInt("wallMode", 1)
        wallPalette = p.getInt("wallPalette", 0)
        wallAnim = p.getBoolean("wallAnim", false)
        wallDim = p.getFloat("wallDim", 0f)
        wallBlur = p.getFloat("wallBlur", 0f)
        wallVersion = p.getInt("wallVersion", 0)
        shadeMode = p.getInt("shadeMode", 0)
        perf = p.getInt("perf", 1)
        firstRun = p.getBoolean("firstRun", true)
        worldZone = p.getString("worldZone", "Europe/London") ?: "Europe/London"
        leftPage = p.getInt("leftPage", 0)
    }

    fun save() {
        sp?.edit()
            ?.putBoolean("overlay", overlay)
            ?.putBoolean("hideIdle", hideIdle)
            ?.putBoolean("banners", banners)
            ?.putBoolean("calls", calls)
            ?.putFloat("offX", offX)
            ?.putFloat("offY", offY)
            ?.putInt("islandColor", islandColor)
            ?.putFloat("islandOpacity", islandOpacity)
            ?.putInt("iconStyle", iconStyle)
            ?.putFloat("iconSize", iconSize)
            ?.putFloat("iconRound", iconRound)
            ?.putFloat("iconAlpha", iconAlpha)
            ?.putBoolean("iconLabels", iconLabels)
            ?.putInt("iconColorMode", iconColorMode)
            ?.putInt("iconColor1", iconColor1)
            ?.putInt("iconColor2", iconColor2)
            ?.putString("appColors", appColors.entries.joinToString(";") { it.key + "=" + it.value })
            ?.putInt("wallMode", wallMode)
            ?.putInt("wallPalette", wallPalette)
            ?.putBoolean("wallAnim", wallAnim)
            ?.putFloat("wallDim", wallDim)
            ?.putFloat("wallBlur", wallBlur)
            ?.putInt("wallVersion", wallVersion)
            ?.putInt("shadeMode", shadeMode)
            ?.putInt("perf", perf)
            ?.putBoolean("firstRun", firstRun)
            ?.putString("worldZone", worldZone)
            ?.putInt("leftPage", leftPage)
            ?.apply()
    }

    fun setAppColor(pkg: String, color: Int?) {
        if (color == null) appColors.remove(pkg) else appColors[pkg] = color
        save()
    }

    fun clearAppColors() {
        appColors.clear()
        save()
    }
}

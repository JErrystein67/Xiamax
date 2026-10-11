package com.example.liquidglass

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object OverlayState {
    var running by mutableStateOf(false)
}

object Prefs {
    private var sp: SharedPreferences? = null

    var overlay by mutableStateOf(false)      // island above all apps
    var hideIdle by mutableStateOf(false)     // hide the empty pill when nothing happens
    var banners by mutableStateOf(true)       // notification banners
    var calls by mutableStateOf(true)         // incoming call card
    var wallAnim by mutableStateOf(true)      // animated wallpaper
    var offX by mutableFloatStateOf(0f)       // dp
    var offY by mutableFloatStateOf(0f)       // dp
    var firstRun by mutableStateOf(true)

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
        sp = p
        overlay = p.getBoolean("overlay", false)
        hideIdle = p.getBoolean("hideIdle", false)
        banners = p.getBoolean("banners", true)
        calls = p.getBoolean("calls", true)
        wallAnim = p.getBoolean("wallAnim", true)
        offX = p.getFloat("offX", 0f)
        offY = p.getFloat("offY", 0f)
        firstRun = p.getBoolean("firstRun", true)
    }

    fun save() {
        sp?.edit()
            ?.putBoolean("overlay", overlay)
            ?.putBoolean("hideIdle", hideIdle)
            ?.putBoolean("banners", banners)
            ?.putBoolean("calls", calls)
            ?.putBoolean("wallAnim", wallAnim)
            ?.putFloat("offX", offX)
            ?.putFloat("offY", offY)
            ?.putBoolean("firstRun", firstRun)
            ?.apply()
    }
}

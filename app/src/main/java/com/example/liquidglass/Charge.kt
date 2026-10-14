package com.example.liquidglass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Battery state for the island. Plugging in the charger shows a 4 s charging pill. */
object ChargeHub {
    var level by mutableIntStateOf(100)
    var charging by mutableStateOf(false)
    var banner by mutableStateOf(false)

    private var started = false
    private var first = true
    private val handler = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { read(i) }
        }
        read(ContextCompat.registerReceiver(app, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
    }

    private fun read(i: Intent?) {
        if (i == null) return
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l >= 0 && s > 0) level = l * 100 / s
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        if (ch && !charging && !first) {
            banner = true
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ banner = false }, 4000)
        }
        charging = ch
        first = false
    }
}

package com.example.liquidglass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherData(val temp: Int, val code: Int, val hi: Int, val lo: Int, val city: String)

/** Current weather from open-meteo.com (no account needed). Uses your last known coarse location. */
object WeatherHub {
    var data by mutableStateOf<WeatherData?>(null)
    var state by mutableIntStateOf(0) // 0 ok / idle, 1 loading, 2 needs location permission, 3 no location yet, 4 error
    private var last = 0L

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(ctx: Context, force: Boolean = false) {
        if (!hasPermission(ctx)) { state = 2; return }
        if (!force && data != null && System.currentTimeMillis() - last < 30 * 60_000L) return
        state = 1
        val loc = withContext(Dispatchers.IO) { lastLocation(ctx) }
        if (loc == null) { state = if (data == null) 3 else 0; return }
        val r = withContext(Dispatchers.IO) { fetch(ctx, loc) }
        if (r != null) {
            data = r
            last = System.currentTimeMillis()
            state = 0
        } else {
            state = if (data == null) 4 else 0
        }
    }

    private fun lastLocation(ctx: Context): Location? = try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = lm.getLastKnownLocation(p) ?: continue
            if (best == null || l.time > best.time) best = l
        }
        best
    } catch (e: Exception) {
        null
    }

    private fun fetch(ctx: Context, loc: Location): WeatherData? = try {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
            loc.latitude, loc.longitude
        )
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val j = JSONObject(text)
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        var city = ""
        try {
            @Suppress("DEPRECATION")
            val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
            if (!a.isNullOrEmpty()) city = a[0].locality ?: a[0].subAdminArea ?: ""
        } catch (e: Exception) { }
        WeatherData(
            Math.round(cur.getDouble("temperature_2m")).toInt(),
            cur.getInt("weather_code"),
            Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            city
        )
    } catch (e: Exception) {
        null
    }
}

fun weatherText(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    in 85..86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> "—"
}

/** 0 sun, 1 partly cloudy, 2 cloud, 3 rain, 4 snow, 5 storm, 6 fog */
fun weatherKind(code: Int): Int = when (code) {
    0 -> 0
    1, 2 -> 1
    3 -> 2
    45, 48 -> 6
    in 51..67, in 80..82 -> 3
    in 71..77, in 85..86 -> 4
    in 95..99 -> 5
    else -> 2
}

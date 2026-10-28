package com.example.liquidglass

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun rememberNow(periodMs: Long = 1000L): State<Long> {
    val s = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            s.longValue = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return s
}

// ------------------------------------------------------------------ weather

private fun DrawScope.cloud(x: Float, y: Float, w: Float, c: Color) {
    val h = w * .55f
    drawCircle(c, w * .22f, Offset(x + w * .30f, y + h * .60f))
    drawCircle(c, w * .30f, Offset(x + w * .56f, y + h * .42f))
    drawCircle(c, w * .20f, Offset(x + w * .80f, y + h * .64f))
    drawRoundRect(c, Offset(x + w * .12f, y + h * .58f), Size(w * .78f, h * .42f), CornerRadius(h * .21f))
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFD36B), Color.Transparent), center = Offset(cx, cy), radius = r * 2.1f), r * 2.1f, Offset(cx, cy))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2B0), Color(0xFFFFB02E)), center = Offset(cx - r * .3f, cy - r * .3f), radius = r * 1.4f), r, Offset(cx, cy))
}

@Composable
fun WeatherIcon(kind: Int, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val white = Color.White.copy(alpha = .96f)
        val st = Stroke(width = w * .045f, cap = StrokeCap.Round)
        when (kind) {
            0 -> sun(w * .5f, w * .5f, w * .24f)
            1 -> { sun(w * .36f, w * .36f, w * .2f); cloud(w * .22f, w * .38f, w * .74f, white) }
            2 -> cloud(w * .10f, w * .22f, w * .82f, white)
            3 -> {
                cloud(w * .10f, w * .08f, w * .82f, white)
                for (i in 0..2) drawLine(Color(0xFF5AA8FF), Offset(w * (.32f + i * .18f), w * .66f), Offset(w * (.27f + i * .18f), w * .84f), st.width, StrokeCap.Round)
            }
            4 -> {
                cloud(w * .10f, w * .08f, w * .82f, white)
                for (i in 0..2) drawCircle(white, w * .028f, Offset(w * (.30f + i * .20f), w * (.74f + (i % 2) * .08f)))
            }
            5 -> {
                cloud(w * .10f, w * .06f, w * .82f, Color(0xFFDDE6F2))
                val p = Path().apply {
                    moveTo(w * .54f, w * .56f); lineTo(w * .40f, w * .76f); lineTo(w * .52f, w * .76f)
                    lineTo(w * .44f, w * .94f); lineTo(w * .66f, w * .70f); lineTo(w * .54f, w * .70f); close()
                }
                drawPath(p, Color(0xFFFFC93C))
            }
            else -> {
                cloud(w * .10f, w * .08f, w * .82f, Color(0xCCFFFFFF))
                for (i in 0..2) drawLine(white, Offset(w * .22f, w * (.68f + i * .09f)), Offset(w * .78f, w * (.68f + i * .09f)), st.width, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun WeatherCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Signals.resume++ }
    val shape = RoundedCornerShape(28.dp)
    val w = WeatherHub.data
    Box(
        modifier
            .pressable(.97f, .97f) {
                if (!WeatherHub.hasPermission(ctx)) launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                else scope.launch { WeatherHub.refresh(ctx, true) }
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF4A9BEF), Color(0xFF7DBDF3), Color(0xFFB4DAF6))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = .15f))), shape)
            .drawWithCache {
                val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = .30f), Color.Transparent), endY = size.height * .4f)
                onDrawWithContent { drawContent(); drawRect(sheen) }
            }
    ) {
        if (w != null) {
            WeatherIcon(weatherKind(w.code), Modifier.align(Alignment.TopEnd).padding(12.dp).size(46.dp))
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (w.city.isNotEmpty()) w.city else "Weather", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${w.temp}°", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
                }
                Column {
                    Text(weatherText(w.code), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("H ${w.hi}°  L ${w.lo}°", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                Text("Weather", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    when (WeatherHub.state) {
                        1 -> "Loading…"
                        2 -> "Tap to allow location"
                        3 -> "Waiting for a location"
                        4 -> "Couldn't load. Tap to retry"
                        else -> "Tap to load"
                    },
                    color = Color.White.copy(alpha = .9f), fontSize = 13.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------------ clocks

@Composable
fun AnalogClockCard(modifier: Modifier) {
    val now by rememberNow(1000L)
    val density = LocalDensity.current.density
    val paint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(235, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 14f * density
        }
    }
    val small = remember(density) {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(160, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 9f * density
        }
    }
    Canvas(modifier.glass(100.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 9.dp.toPx()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val sec = cal.get(Calendar.SECOND)
        val min = cal.get(Calendar.MINUTE) + sec / 60f
        val hour = (cal.get(Calendar.HOUR) % 12) + min / 60f

        for (i in 0 until 60) {
            val a = i * 6f * (Math.PI.toFloat() / 180f)
            val big = i % 5 == 0
            val l = if (big) 6.dp.toPx() else 3.dp.toPx()
            val o = r
            drawLine(
                Color.White.copy(alpha = if (big) .85f else .4f),
                Offset(cx + sin(a) * (o - l), cy - cos(a) * (o - l)),
                Offset(cx + sin(a) * o, cy - cos(a) * o),
                if (big) 1.5.dp.toPx() else 1.dp.toPx(), StrokeCap.Round
            )
        }
        drawIntoCanvas { c ->
            for (n in 1..12) {
                val a = n * 30f * (Math.PI.toFloat() / 180f)
                val rr = r - 22.dp.toPx()
                val x = cx + sin(a) * rr
                val y = cy - cos(a) * rr
                c.nativeCanvas.drawText(n.toString(), x, y - (paint.ascent() + paint.descent()) / 2f, paint)
            }
            c.nativeCanvas.drawText("LOCAL", cx, cy + r * .52f, small)
        }
        fun hand(angleDeg: Float, len: Float, width: Float, color: Color, tail: Float = 0f) {
            val a = angleDeg * (Math.PI.toFloat() / 180f)
            drawLine(
                color,
                Offset(cx - sin(a) * tail, cy + cos(a) * tail),
                Offset(cx + sin(a) * len, cy - cos(a) * len),
                width, StrokeCap.Round
            )
        }
        hand(hour * 30f, r * .50f, 4.5.dp.toPx(), Color.White)
        hand(min * 6f, r * .76f, 3.dp.toPx(), Color.White)
        hand(sec * 6f, r * .86f, 1.5.dp.toPx(), Color(0xFF3D8BFF), tail = r * .16f)
        drawCircle(Color.White, 4.5.dp.toPx(), Offset(cx, cy))
        drawCircle(Color(0xFF3D8BFF), 2.dp.toPx(), Offset(cx, cy))
    }
}

private fun cityOf(zone: String): String = zone.substringAfter('/').replace('_', ' ')

@Composable
fun WorldClockCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val now by rememberNow(15_000L)
    val is24 = DateFormat.is24HourFormat(ctx)
    val fmt = if (is24) "H:mm" else "h:mm"
    val tz = remember(Prefs.worldZone) { TimeZone.getTimeZone(Prefs.worldZone) }
    val there = SimpleDateFormat(fmt, Locale.getDefault()).apply { timeZone = tz }.format(Date(now))
    val here = SimpleDateFormat(fmt, Locale.getDefault()).format(Date(now))
    val diff = (tz.getOffset(now) - TimeZone.getDefault().getOffset(now)) / 3_600_000f
    val diffText = when {
        abs(diff) < .01f -> "Same time"
        else -> {
            val n = abs(diff)
            val num = if (n % 1f == 0f) n.toInt().toString() else n.toString()
            if (diff < 0) "${num}h behind" else "${num}h ahead"
        }
    }
    Row(
        modifier.glass(26.dp, light = true).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(there, color = Pal.inkDark, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(cityOf(Prefs.worldZone), color = Pal.inkDark2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(diffText, color = Pal.inkDark2, fontSize = 12.sp, maxLines = 1)
            }
        }
        Box(Modifier.width(1.dp).height(34.dp).background(Color(0x221B2A27)))
        Row(Modifier.weight(1f).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(here, color = Pal.inkDark, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Local", color = Pal.inkDark2, fontSize = 12.sp)
                Text("time", color = Pal.inkDark2, fontSize = 12.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ calendar

class CalEvent(val title: String, val range: String)

object CalendarHub {
    var next by mutableStateOf<CalEvent?>(null)

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(ctx: Context) {
        if (!hasPermission(ctx)) { next = null; return }
        next = withContext(Dispatchers.IO) { query(ctx) }
    }

    private fun query(ctx: Context): CalEvent? = try {
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now)
        ContentUris.appendId(b, now + 24L * 3_600_000L)
        val c = ctx.contentResolver.query(
            b.build(),
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
            null, null, "begin ASC"
        )
        var out: CalEvent? = null
        c?.use {
            val f = SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a", Locale.getDefault())
            while (out == null && it.moveToNext()) {
                val end = it.getLong(2)
                if (end <= now) continue
                val allDay = it.getInt(3) == 1
                val title = it.getString(0) ?: "Event"
                out = CalEvent(title, if (allDay) "All day" else f.format(Date(it.getLong(1))) + " – " + f.format(Date(end)))
            }
        }
        out
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CalendarCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val now by rememberNow(60_000L)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Signals.resume++ }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val ev = CalendarHub.next
    Column(
        modifier.glass(28.dp, light = true).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("${cal.get(Calendar.YEAR)}/${cal.get(Calendar.MONTH) + 1}", color = Pal.inkDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(cal.get(Calendar.DAY_OF_MONTH).toString(), color = Pal.inkDark, fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(.97f, .97f) {
                    if (!CalendarHub.hasPermission(ctx)) launcher.launch(Manifest.permission.READ_CALENDAR)
                }
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = .6f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!CalendarHub.hasPermission(ctx)) {
                Text("Show my events", color = Pal.inkDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap to allow calendar", color = Pal.inkDark2, fontSize = 12.sp)
            } else if (ev != null) {
                Text(ev.title, color = Pal.inkDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ev.range, color = Pal.inkDark2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("No events", color = Pal.inkDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Nothing in the next 24 h", color = Pal.inkDark2, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------ media + sliders + search

@Composable
fun MediaCard(modifier: Modifier) {
    val bmp = MediaHub.art
    val img = remember(bmp) { bmp?.asImageBitmap() }
    Column(modifier.glass(28.dp).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (img != null) {
                Image(img, null, Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)))
            } else {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Pal.mint, Pal.violet))))
            }
        }
        Column {
            Text(
                if (MediaHub.active && MediaHub.title.isNotEmpty()) MediaHub.title else "Not playing",
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (MediaHub.active) MediaHub.artist else "Play music to control it here",
                color = Color.White.copy(alpha = .75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).pressable(.85f, .85f) { MediaHub.prev() }, contentAlignment = Alignment.Center) {
                MediaGlyph(MG.Prev, Color.White, Modifier.size(24.dp))
            }
            Box(Modifier.size(38.dp).pressable(.85f, .85f) { MediaHub.toggle() }, contentAlignment = Alignment.Center) {
                MediaGlyph(if (MediaHub.playing) MG.Pause else MG.Play, Color.White, Modifier.size(30.dp))
            }
            Box(Modifier.size(34.dp).pressable(.85f, .85f) { MediaHub.next() }, contentAlignment = Alignment.Center) {
                MediaGlyph(MG.Next, Color.White, Modifier.size(24.dp))
            }
        }
    }
}

/** Tall glass capsule: the lower part fills white, with the icon inside it (brightness / volume). */
@Composable
fun GlassSlider(value: Float, sun: Boolean, onStart: () -> Unit, onChange: (Float) -> Unit, modifier: Modifier) {
    Box(
        modifier
            .glass(34.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onStart() },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        onChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { o -> onStart(); onChange((1f - o.y / size.height).coerceIn(0f, 1f)) })
            }
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0.04f, 1f))
                .background(Color.White.copy(alpha = .96f))
        )
        Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).size(30.dp)) {
            val s = size.minDimension
            if (sun) {
                val c = Color(0xFFFF9F1A)
                drawCircle(c, s * .20f, Offset(s / 2, s / 2))
                for (i in 0 until 8) {
                    val a = i * 45f * (Math.PI.toFloat() / 180f)
                    drawLine(c, Offset(s / 2 + sin(a) * s * .32f, s / 2 - cos(a) * s * .32f), Offset(s / 2 + sin(a) * s * .46f, s / 2 - cos(a) * s * .46f), 2.dp.toPx(), StrokeCap.Round)
                }
            } else {
                val c = Color(0xFF2F7BFF)
                val p = Path().apply {
                    moveTo(s * .08f, s * .38f); lineTo(s * .28f, s * .38f); lineTo(s * .52f, s * .16f)
                    lineTo(s * .52f, s * .84f); lineTo(s * .28f, s * .62f); lineTo(s * .08f, s * .62f); close()
                }
                drawPath(p, c)
                drawArc(c, -40f, 80f, false, Offset(s * .50f, s * .30f), Size(s * .30f, s * .40f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawArc(c, -50f, 100f, false, Offset(s * .50f, s * .16f), Size(s * .46f, s * .68f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun SearchPill(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.pressable(.98f, .98f, onClick).glass(28.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(22.dp)) {
            val s = size.minDimension
            drawCircle(Color.White, s * .34f, Offset(s * .42f, s * .42f), style = Stroke(2.2.dp.toPx()))
            drawLine(Color.White, Offset(s * .68f, s * .68f), Offset(s * .92f, s * .92f), 2.2.dp.toPx(), StrokeCap.Round)
        }
        Spacer(Modifier.width(12.dp))
        Text("Search", color = Color.White, fontSize = 18.sp)
    }
}

/** The smart widgets page (left of home): music + brightness/volume, calendar + clock, search, your Android widgets. */
@Composable
fun SmartPage(onSearch: () -> Unit, onAddWidget: () -> Unit) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableStateOf(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    var bright by remember {
        mutableStateOf(try { android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (e: Exception) { .5f })
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(172.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaCard(Modifier.weight(2.2f).fillMaxHeight())
            GlassSlider(
                value = bright, sun = true,
                onStart = {
                    if (!android.provider.Settings.System.canWrite(ctx)) {
                        ctx.safeStart(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }
                },
                onChange = { v ->
                    bright = v
                    try {
                        if (android.provider.Settings.System.canWrite(ctx)) {
                            android.provider.Settings.System.putInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                            android.provider.Settings.System.putInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
                        }
                    } catch (e: Exception) { }
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GlassSlider(
                value = vol, sun = false, onStart = { },
                onChange = { v ->
                    vol = v
                    try { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0) } catch (e: Exception) { }
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalendarCard(Modifier.weight(1f).aspectRatio(1f))
            AnalogClockCard(Modifier.weight(1f).aspectRatio(1f))
        }
        SearchPill(Modifier.fillMaxWidth().height(54.dp), onSearch)
        WidgetsSection(onAddWidget)
        Spacer(Modifier.height(12.dp))
    }
}

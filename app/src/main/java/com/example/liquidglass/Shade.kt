package com.example.liquidglass

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.bluetooth.BluetoothManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PanelHandle(onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onVerticalDrag = { change, dy -> change.consume(); onDrag(dy) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .45f)))
    }
}

@Composable
private fun PanelFrame(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2150F3A), Color(0xF50B0920))))
            .border(
                1.dp,
                Brush.verticalGradient(0f to Color.White.copy(alpha = .22f), 1f to Color.White.copy(alpha = .08f)),
                shape
            )
            .pointerInput(Unit) { detectTapGestures { } } // swallow taps
            .statusBarsPadding(),
        content = content
    )
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .pressable(.9f, .92f, onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = .14f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = Pal.ink, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- Notification Center (top-left pull)

@Composable
fun NotificationCenter(
    onOpenSetup: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit
) {
    PanelFrame {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", color = Pal.ink, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            if (MediaHub.notifs.isNotEmpty()) SmallPill("Clear all") { MediaHub.dismissAll() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !MediaHub.hasAccess -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Turn on notification access to see your notifications here.", color = Pal.ink2, fontSize = 14.sp, textAlign = TextAlign.Center)
                    SmallPill("Set up", onOpenSetup)
                }
                MediaHub.notifs.isEmpty() -> Text("No notifications", color = Pal.ink2, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(MediaHub.notifs, key = { it.key }) { NotifCard(it) }
                }
            }
        }
        PanelHandle(onDragStart, onDrag, onDragEnd)
    }
}

@Composable
private fun NotifCard(n: NotifItem) {
    val scope = rememberCoroutineScope()
    var dx by remember { mutableFloatStateOf(0f) }
    val time = remember(n.whenMs) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.whenMs)) }
    fun back() {
        scope.launch { animate(dx, 0f, 0f, spring(dampingRatio = .85f, stiffness = 400f)) { v, _ -> dx = v } }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(dx.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(dx) / 700f).coerceIn(0f, 1f) }
            .glass(22.dp)
            .pointerInput(n.key) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(dx) > 260f && n.clearable) MediaHub.dismiss(n.key) else back() },
                    onDragCancel = { back() },
                    onHorizontalDrag = { change, d -> change.consume(); dx += d }
                )
            }
            .pointerInput(n.key) {
                detectTapGestures(onTap = { try { n.open?.send() } catch (e: Exception) { } })
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(n.app, color = Pal.mint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(time, color = Pal.ink2, fontSize = 11.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(n.title, color = Pal.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotEmpty()) Text(n.text, color = Pal.ink2, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (n.clearable) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(30.dp).pressable(.85f, .85f) { MediaHub.dismiss(n.key) }.clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) { Text("×", color = Pal.ink, fontSize = 18.sp) }
        }
    }
}

// ---------------------------------------------------------------- Control Center (top-right pull)

@Composable
private fun QuickGlyph(kind: Int, tint: Color) {
    // 0 wifi, 1 bluetooth, 2 flashlight
    Canvas(Modifier.size(28.dp)) {
        val s = size.minDimension / 24f
        val st = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            0 -> {
                for (r in listOf(4f, 8f, 12f)) {
                    drawArc(tint, 225f, 90f, false, Offset((12 - r) * s, (19 - r) * s), Size(2 * r * s, 2 * r * s), style = st)
                }
                drawCircle(tint, 1.3f * s, Offset(12 * s, 19 * s))
            }
            1 -> {
                val p = Path().apply {
                    moveTo(7f * s, 8f * s); lineTo(17f * s, 16f * s); lineTo(12f * s, 21f * s)
                    lineTo(12f * s, 3f * s); lineTo(17f * s, 8f * s); lineTo(7f * s, 16f * s)
                }
                drawPath(p, tint, style = st)
            }
            else -> {
                val p = Path().apply {
                    moveTo(8f * s, 3f * s); lineTo(16f * s, 3f * s); lineTo(14.5f * s, 8f * s); lineTo(9.5f * s, 8f * s); close()
                    moveTo(10f * s, 8f * s); lineTo(10f * s, 21f * s); lineTo(14f * s, 21f * s); lineTo(14f * s, 8f * s)
                }
                drawPath(p, tint, style = st)
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .pressable(.94f, .94f, onClick)
            .clip(shape)
            .background(
                if (on) Brush.linearGradient(listOf(Pal.violet.copy(alpha = .85f), Pal.pink.copy(alpha = .75f)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .06f)))
            )
            .border(1.dp, Color.White.copy(alpha = if (on) .35f else .16f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        icon()
        Text(label, color = Pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun VSlider(value: Float, label: String, onStart: () -> Unit, onChange: (Float) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(34.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = .10f))
            .border(1.dp, Color.White.copy(alpha = .18f), shape)
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
                .fillMaxHeight(value.coerceIn(0.03f, 1f))
                .background(Brush.verticalGradient(listOf(Pal.mint.copy(alpha = .9f), Pal.violet.copy(alpha = .9f))))
        )
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
    }
}

@Composable
private fun BatteryBadge(level: Int, charging: Boolean) {
    Row(
        Modifier.glass(18.dp).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
            val st = Stroke(width = 1.5.dp.toPx())
            drawRoundRect(Color.White.copy(alpha = .9f), Offset(0f, 0f), Size(size.width - 3.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = st)
            val fillW = (size.width - 7.dp.toPx()) * level / 100f
            drawRoundRect(if (charging) Pal.mint else Color.White, Offset(2.dp.toPx(), 2.dp.toPx()), Size(fillW, size.height - 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = .9f), Offset(size.width - 2.dp.toPx(), size.height * .3f), Size(2.dp.toPx(), size.height * .4f), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
        }
        Text("$level%", color = Pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ControlCenter(
    onOpenIslandSettings: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit
) {
    val ctx = LocalContext.current
    val battery = rememberBattery()

    // Wi-Fi / Bluetooth: Android no longer lets apps flip them, so these show the state and open the system panel.
    val wifiOn = remember {
        try { ctx.applicationContext.getSystemService(Context.WIFI_SERVICE).let { (it as WifiManager).isWifiEnabled } } catch (e: Exception) { false }
    }
    val btOn = remember {
        try { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true } catch (e: Exception) { false }
    }

    // Flashlight: real toggle
    val cm = remember { ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val camId = remember {
        try { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } } catch (e: Exception) { null }
    }
    var torch by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == camId) torch = enabled }
        }
        try { cm.registerTorchCallback(cb, null) } catch (e: Exception) { }
        onDispose { try { cm.unregisterTorchCallback(cb) } catch (e: Exception) { } }
    }

    // Volume: real
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }

    // Brightness: real, needs the "change system settings" permission once
    var bright by remember {
        mutableFloatStateOf(try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (e: Exception) { .5f })
    }

    PanelFrame {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Control Center", color = Pal.ink, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            BatteryBadge(battery.level, battery.charging)
        }
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile("Wi-Fi", wifiOn, Modifier.weight(1f).fillMaxHeight(), {
                        ctx.safeStart(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                    }) { QuickGlyph(0, Color.White) }
                    QuickTile("Bluetooth", btOn, Modifier.weight(1f).fillMaxHeight(), {
                        ctx.safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) { QuickGlyph(1, Color.White) }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile("Flashlight", torch, Modifier.weight(1f).fillMaxHeight(), {
                        try { camId?.let { cm.setTorchMode(it, !torch) } } catch (e: Exception) { }
                    }) { QuickGlyph(2, Color.White) }
                    QuickTile("Island", false, Modifier.weight(1f).fillMaxHeight(), onOpenIslandSettings) {
                        Icon(Icons.Filled.Settings, null, Modifier.size(28.dp), tint = Color.White)
                    }
                }
                MiniPlayer()
            }
            VSlider(
                value = bright, label = "Light",
                onStart = {
                    if (!Settings.System.canWrite(ctx)) {
                        ctx.safeStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }
                },
                onChange = { v ->
                    bright = v
                    try {
                        if (Settings.System.canWrite(ctx)) {
                            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
                        }
                    } catch (e: Exception) { }
                },
                modifier = Modifier.width(74.dp).fillMaxHeight()
            )
            VSlider(
                value = vol, label = "Volume", onStart = { },
                onChange = { v ->
                    vol = v
                    try { am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0) } catch (e: Exception) { }
                },
                modifier = Modifier.width(74.dp).fillMaxHeight()
            )
        }
        PanelHandle(onDragStart, onDrag, onDragEnd)
    }
}

@Composable
private fun MiniPlayer() {
    Row(
        Modifier.fillMaxWidth().height(76.dp).glass(26.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = MediaHub.art
        val img = remember(bmp) { bmp?.asImageBitmap() }
        if (img != null) {
            androidx.compose.foundation.Image(img, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Pal.pink, Pal.violet))))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (MediaHub.active && MediaHub.title.isNotEmpty()) MediaHub.title else "Nothing playing",
                color = Pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(MediaHub.artist, color = Pal.ink2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).pressable(.85f, .85f) { MediaHub.prev() }, contentAlignment = Alignment.Center) {
            MediaGlyph(MG.Prev, Color.White, Modifier.size(18.dp))
        }
        Box(
            Modifier.size(40.dp).pressable(.85f, .85f) { MediaHub.toggle() }.clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            MediaGlyph(if (MediaHub.playing) MG.Pause else MG.Play, Color(0xFF1A1238), Modifier.size(20.dp))
        }
        Box(Modifier.size(36.dp).pressable(.85f, .85f) { MediaHub.next() }, contentAlignment = Alignment.Center) {
            MediaGlyph(MG.Next, Color.White, Modifier.size(18.dp))
        }
    }
}

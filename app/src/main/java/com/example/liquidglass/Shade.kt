package com.example.liquidglass

import android.Manifest
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.bluetooth.BluetoothManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.core.content.ContextCompat
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
            .background(Brush.verticalGradient(listOf(Color(0xEC21423A), Color(0xF2142823))))
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
private fun SectionLabel(text: String) {
    Text(text, color = Pal.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

/** Round glass toggle. On = solid white with a coloured icon, like the HyperOS common controls. */
@Composable
private fun CircleToggle(label: String, on: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(58.dp)
                .pressable(.92f, .92f, onClick)
                .clip(CircleShape)
                .background(
                    if (on) Brush.linearGradient(listOf(Color.White, Color(0xFFDDEBFF)))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = .18f), Color.White.copy(alpha = .09f)))
                )
                .border(1.dp, Color.White.copy(alpha = if (on) .8f else .3f), CircleShape),
            contentAlignment = Alignment.Center
        ) { icon(if (on) Color(0xFF2F6FE0) else Color.White) }
        Text(label, color = Pal.ink, fontSize = 11.sp)
    }
}

class BtDev(val name: String, val major: Int, val connected: Boolean)

private fun btAllowed(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Paired Bluetooth devices, connected ones (audio) first. */
@Composable
private fun rememberBtDevices(allowed: Boolean): List<BtDev> {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<BtDev>>(emptyList()) }
    DisposableEffect(allowed) {
        var proxy: BluetoothProfile? = null
        val adapter = try { ctx.getSystemService(BluetoothManager::class.java)?.adapter } catch (e: Exception) { null }
        fun load() {
            try {
                if (adapter == null || !allowed) { list = emptyList(); return }
                val on = proxy?.connectedDevices?.map { it.address }?.toSet() ?: emptySet()
                list = adapter.bondedDevices
                    .map { d -> BtDev(d.name ?: d.address, d.bluetoothClass?.majorDeviceClass ?: 0, d.address in on) }
                    .sortedByDescending { it.connected }
            } catch (e: Exception) {
                list = emptyList()
            }
        }
        val l = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, p: BluetoothProfile) { proxy = p; load() }
            override fun onServiceDisconnected(profile: Int) { proxy = null; load() }
        }
        try { if (allowed) adapter?.getProfileProxy(ctx, l, BluetoothProfile.A2DP) } catch (e: Exception) { }
        load()
        onDispose { try { proxy?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) } } catch (e: Exception) { } }
    }
    return list
}

@Composable
private fun DeviceGlyph(major: Int, tint: Color) {
    Canvas(Modifier.size(30.dp)) {
        val s = size.minDimension
        val st = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)
        when (major) {
            0x0400 -> { // headphones
                drawArc(tint, 180f, 180f, false, Offset(s * .15f, s * .12f), Size(s * .70f, s * .70f), style = st)
                drawRoundRect(tint, Offset(s * .08f, s * .50f), Size(s * .16f, s * .30f), CornerRadius(s * .06f))
                drawRoundRect(tint, Offset(s * .76f, s * .50f), Size(s * .16f, s * .30f), CornerRadius(s * .06f))
            }
            0x0700 -> { // watch
                drawRoundRect(tint, Offset(s * .25f, s * .27f), Size(s * .50f, s * .46f), CornerRadius(s * .14f), style = st)
                drawLine(tint, Offset(s * .38f, s * .08f), Offset(s * .38f, s * .27f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(s * .62f, s * .08f), Offset(s * .62f, s * .27f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(s * .38f, s * .73f), Offset(s * .38f, s * .92f), st.width, StrokeCap.Round)
                drawLine(tint, Offset(s * .62f, s * .73f), Offset(s * .62f, s * .92f), st.width, StrokeCap.Round)
            }
            0x0200 -> { // phone
                drawRoundRect(tint, Offset(s * .28f, s * .06f), Size(s * .44f, s * .88f), CornerRadius(s * .10f), style = st)
                drawCircle(tint, s * .03f, Offset(s * .5f, s * .82f))
            }
            else -> {
                drawCircle(tint, s * .34f, Offset(s / 2, s / 2), style = st)
                drawCircle(tint, s * .08f, Offset(s / 2, s / 2))
            }
        }
    }
}

@Composable
private fun DeviceTile(dev: BtDev, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .height(104.dp)
            .clip(shape)
            .background(
                if (dev.connected) Brush.linearGradient(listOf(Color(0xFF4F8EE8), Color(0xFF7BB0F2)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .09f)))
            )
            .border(1.dp, Color.White.copy(alpha = if (dev.connected) .55f else .28f), shape)
            .padding(14.dp)
    ) {
        Box(Modifier.align(Alignment.TopStart)) { DeviceGlyph(dev.major, Color.White) }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(dev.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (dev.connected) "Connected" else "Paired", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
        }
    }
}

/** Wide glass slider (brightness / volume): fills white from the left, icon inside the fill. */
@Composable
private fun HSlider(value: Float, sun: Boolean, onStart: () -> Unit, onChange: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .glass(28.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { onStart() }) { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { o -> onStart(); onChange((o.x / size.width).coerceIn(0f, 1f)) })
            }
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(.08f, 1f))
                .background(Color.White.copy(alpha = .96f))
        )
        Canvas(Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(26.dp)) {
            val s = size.minDimension
            if (sun) {
                val c = Color(0xFFFF9F1A)
                drawCircle(c, s * .20f, Offset(s / 2, s / 2))
                for (i in 0 until 8) {
                    val a = i * 45f * (Math.PI.toFloat() / 180f)
                    drawLine(c, Offset(s / 2 + kotlin.math.sin(a) * s * .32f, s / 2 - kotlin.math.cos(a) * s * .32f),
                        Offset(s / 2 + kotlin.math.sin(a) * s * .46f, s / 2 - kotlin.math.cos(a) * s * .46f), 2.dp.toPx(), StrokeCap.Round)
                }
            } else {
                val c = Color(0xFF2F7BFF)
                val p = Path().apply {
                    moveTo(s * .08f, s * .38f); lineTo(s * .28f, s * .38f); lineTo(s * .52f, s * .16f)
                    lineTo(s * .52f, s * .84f); lineTo(s * .28f, s * .62f); lineTo(s * .08f, s * .62f); close()
                }
                drawPath(p, c)
                drawArc(c, -40f, 80f, false, Offset(s * .50f, s * .30f), Size(s * .30f, s * .40f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
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

    // Bluetooth devices
    val btAllowedState = remember { mutableStateOf(btAllowed(ctx)) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { btAllowedState.value = it }
    val devices = rememberBtDevices(btAllowedState.value)

    // Volume: real
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }

    // Brightness: real, needs the "change system settings" permission once
    var bright by remember {
        mutableFloatStateOf(try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (e: Exception) { .5f })
    }

    PanelFrame {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Control Center", color = Pal.ink, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            BatteryBadge(battery.level, battery.charging)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel("Common")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleToggle("Wi-Fi", wifiOn, { ctx.safeStart(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }) { QuickGlyph(0, it) }
                CircleToggle("Bluetooth", btOn, { ctx.safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { QuickGlyph(1, it) }
                CircleToggle("Flashlight", torch, { try { camId?.let { cm.setTorchMode(it, !torch) } } catch (e: Exception) { } }) { QuickGlyph(2, it) }
                CircleToggle("Settings", false, onOpenIslandSettings) { Icon(Icons.Filled.Settings, null, Modifier.size(28.dp), tint = it) }
            }

            SectionLabel("Nearby & personal devices")
            when {
                !btAllowedState.value -> Box(
                    Modifier.fillMaxWidth().pressable(.98f, .98f) { btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }.glass(24.dp).padding(18.dp)
                ) { Text("Allow Bluetooth to see your devices", color = Pal.ink, fontSize = 14.sp) }
                devices.isEmpty() -> Text("No paired devices", color = Pal.ink2, fontSize = 13.sp)
                else -> devices.take(6).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { DeviceTile(it, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SectionLabel("Brightness & sound")
            HSlider(
                value = bright, sun = true,
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
                }
            )
            HSlider(
                value = vol, sun = false, onStart = { },
                onChange = { v ->
                    vol = v
                    try { am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0) } catch (e: Exception) { }
                }
            )
            MiniPlayer()
            Spacer(Modifier.height(6.dp))
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

package com.example.liquidglass

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Context.safeStart(i: Intent) {
    try { startActivity(i) } catch (e: Exception) { }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(26.dp).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, color = Pal.mint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, color = Pal.ink2, fontSize = 12.sp)
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.ink, fontSize = 16.sp)
            if (sub != null) Text(sub, color = Pal.ink2, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Pal.violet,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = .8f),
                uncheckedTrackColor = Color.White.copy(alpha = .2f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .pressable(.9f, .92f, onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Pal.violet, Pal.pink)))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.ink, fontSize = 16.sp)
            Text(sub, color = Pal.ink2, fontSize = 12.sp)
        }
        if (ok) Text("On", color = Pal.mint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        else PillButton("Allow", onGrant)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Pal.ink, fontSize = 16.sp)
            Text(label, color = Pal.ink2, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = { Prefs.save() },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Pal.mint,
                inactiveTrackColor = Color.White.copy(alpha = .2f)
            )
        )
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Pal.ink, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .pressable(.95f, .95f) { onSelect(i) }
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (on) Brush.linearGradient(listOf(Pal.violet, Pal.pink))
                            else SolidColor(Color.White.copy(alpha = .10f))
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SwatchRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val on = c == selected
            Box(
                Modifier
                    .size(34.dp)
                    .pressable(.85f, .85f) { onSelect(c) }
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = .3f), CircleShape)
            )
        }
    }
}

private val islandSwatches = listOf(0xFF2C245E, 0xFF000000, 0xFF23262F, 0xFF12306B, 0xFF0F4D3A, 0xFF5A1236).map { it.toInt() }

@Composable
fun IslandSettings(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = Signals.resume // re-check permissions when coming back from system settings
    val listenerOk = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val writeOk = remember(tick) { Settings.System.canWrite(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Signals.resume++ }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { Signals.resume++ }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveWallpaper(ctx, uri) }
                if (ok) {
                    Prefs.wallVersion++
                    Prefs.wallMode = 2
                    Prefs.save()
                }
            }
        }
    }
    val pkgUri = Uri.parse("package:${ctx.packageName}")
    val roleManager = if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java) else null
    val isHome = remember(tick) {
        try { roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true } catch (e: Exception) { false }
    }
    var hue by remember { mutableFloatStateOf(270f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF00B0920), Color(0xFA0B0920))))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .padding(top = 56.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = Pal.ink, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).pressable(.88f, .88f, onClose).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("SETUP") {
                PermRow("Default launcher", "Use Liquid Glass as your home screen", isHome) {
                    try {
                        roleLauncher.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    } catch (e: Exception) {
                        ctx.safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                }
                PermRow("Notification access", "Music, notifications and calls on the island", listenerOk) {
                    ctx.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                PermRow("Display over other apps", "Island on top of every app", overlayOk) {
                    ctx.safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
                }
                PermRow("Show notifications", "Needed to keep the island running", notifOk) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.safeStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
                PermRow("Change system settings", "Lets the Control Center set brightness", writeOk) {
                    ctx.safeStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri))
                }
                if (!listenerOk) {
                    Caption("If Android says \"Restricted setting\": tap Open app info, press the ⋮ menu (top right), choose \"Allow restricted settings\", then press Allow again.")
                    PillButton("Open app info") {
                        ctx.safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
                    }
                }
            }

            Section("DYNAMIC ISLAND") {
                SwitchRow("Always on, over all apps", "Keeps the island on top of every app", Prefs.overlay) {
                    Prefs.overlay = it
                    Prefs.save()
                    if (it && !Settings.canDrawOverlays(ctx)) {
                        ctx.safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
                    }
                    OverlayControl.apply(ctx)
                }
                SwitchRow("Hide when idle", "No empty pill when nothing is happening", Prefs.hideIdle) { Prefs.hideIdle = it; Prefs.save() }
                SwitchRow("Notification banners", null, Prefs.banners) { Prefs.banners = it; Prefs.save() }
                SwitchRow("Incoming call card", null, Prefs.calls) { Prefs.calls = it; Prefs.save() }
                Text("Color", color = Pal.ink, fontSize = 16.sp)
                SwatchRow(islandSwatches, Prefs.islandColor) { Prefs.islandColor = it; Prefs.save() }
                SliderRow("Custom color", hue, 0f..360f, "${hue.toInt()}°") {
                    hue = it
                    Prefs.islandColor = Color.hsv(it, .55f, .42f).toArgb()
                }
                SliderRow("Opacity", Prefs.islandOpacity, .3f..1f, "${(Prefs.islandOpacity * 100).toInt()}%") { Prefs.islandOpacity = it }
                SliderRow("Horizontal position", Prefs.offX, -80f..80f, "${Prefs.offX.toInt()} dp") { Prefs.offX = it }
                SliderRow("Vertical position", Prefs.offY, -20f..60f, "${Prefs.offY.toInt()} dp") { Prefs.offY = it }
            }

            Section("APP ICONS") {
                ChoiceRow("Style", listOf("Glass", "Flat", "Original"), Prefs.iconStyle) { Prefs.iconStyle = it; Prefs.save() }
                SliderRow("Size", Prefs.iconSize, 48f..68f, "${Prefs.iconSize.toInt()} dp") { Prefs.iconSize = it }
                SliderRow("Roundness", Prefs.iconRound, .2f..0.5f, "${(Prefs.iconRound * 100).toInt()}%") { Prefs.iconRound = it }
                if (Prefs.iconStyle == 0) {
                    SliderRow("Glass tint", Prefs.iconAlpha, .3f..1f, "${(Prefs.iconAlpha * 100).toInt()}%") { Prefs.iconAlpha = it }
                }
                SwitchRow("Show names under icons", null, Prefs.iconLabels) { Prefs.iconLabels = it; Prefs.save() }
            }

            Section("WALLPAPER") {
                ChoiceRow("Source", listOf("Phone's own", "Gradient", "Photo"), Prefs.wallMode) { Prefs.wallMode = it; Prefs.save() }
                if (Prefs.wallMode == 1) {
                    ChoiceRow("Colors", palettes.map { it.name }, Prefs.wallPalette) { Prefs.wallPalette = it; Prefs.save() }
                    SwitchRow("Animate gradient", "Only in Full quality. Uses more battery.", Prefs.wallAnim) { Prefs.wallAnim = it; Prefs.save() }
                }
                if (Prefs.wallMode == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Choose photo") {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        PillButton("Remove") {
                            clearWallpaper(ctx)
                            Prefs.wallVersion++
                            Prefs.wallMode = 1
                            Prefs.save()
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        SliderRow("Blur", Prefs.wallBlur, 0f..25f, "${Prefs.wallBlur.toInt()} dp") { Prefs.wallBlur = it }
                    }
                }
                if (Prefs.wallMode == 2 && Prefs.wallVersion == 0) {
                    Caption("Pick a photo to use it as the wallpaper.")
                }
                SliderRow("Dim", Prefs.wallDim, 0f..0.6f, "${(Prefs.wallDim * 100).toInt()}%") { Prefs.wallDim = it }
                if (Prefs.wallMode == 0) Caption("Shows the wallpaper set in your phone's own settings. Use Dim if the icons are hard to read.")
            }

            Section("PULL-DOWN PANELS") {
                ChoiceRow("From the top edge", listOf("Phone's own", "Liquid Glass"), Prefs.shadeMode) { Prefs.shadeMode = it; Prefs.save() }
                Caption("Phone's own keeps your normal notification shade and control center. Liquid Glass swaps in ours (notifications on the left half, control center on the right) while you are on the home screen.")
            }

            Section("PERFORMANCE") {
                ChoiceRow("Quality", listOf("Battery saver", "Balanced", "Full"), Prefs.perf) { Prefs.perf = it; Prefs.save() }
                Caption("Battery saver: no shadows or highlights, still wallpaper. Balanced: lighter shadows, still wallpaper. Full: richest look, animated wallpaper allowed.")
            }

            Section("HYPEROS") {
                Caption(HyperOS.info())
                Caption("Works on HyperOS 2 (Android 15) and HyperOS 3 (Android 16). Xiaomi can stop background apps, which would remove the always-on island. Allow autostart and set battery to No restrictions.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Autostart") { HyperOS.openAutostart(ctx) }
                    PillButton("Battery") { HyperOS.openBattery(ctx) }
                }
                Caption("The Dynamic Island tile can also be added to your phone's own quick settings: edit the tiles in the control center and look for Dynamic Island.")
                Caption("If swipe gestures or Recents act oddly with a third-party launcher, look for a Recents or home screen option in the phone's Settings under Home screen.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

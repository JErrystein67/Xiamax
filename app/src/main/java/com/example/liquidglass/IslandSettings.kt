package com.example.liquidglass

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

fun Context.safeStart(i: Intent) {
    try { startActivity(i) } catch (e: Exception) { }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, color = Pal.mint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        content()
    }
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
private fun PermRow(title: String, sub: String, ok: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.ink, fontSize = 16.sp)
            Text(sub, color = Pal.ink2, fontSize = 12.sp)
        }
        if (ok) {
            Text("On", color = Pal.mint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        } else {
            PillButton("Allow", onGrant)
        }
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
private fun SliderRow(title: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Pal.ink, fontSize = 16.sp)
            Text("${value.toInt()} dp", color = Pal.ink2, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -80f..80f,
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
fun IslandSettings(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val tick = Signals.resume // re-check permissions when coming back from system settings
    val listenerOk = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Signals.resume++ }
    val pkgUri = Uri.parse("package:${ctx.packageName}")

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF00B0920), Color(0xFA0B0920))))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .padding(top = 56.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Island settings", color = Pal.ink, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
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
            Section("PERMISSIONS") {
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
                if (!listenerOk) {
                    Text(
                        "If Android says \"Restricted setting\" or blocks it: tap Open app info, press the ⋮ menu (top right), choose \"Allow restricted settings\", then press Allow again.",
                        color = Pal.ink2, fontSize = 12.sp
                    )
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
                SwitchRow("Hide when idle", "No empty pill when nothing is happening", Prefs.hideIdle) {
                    Prefs.hideIdle = it; Prefs.save()
                }
                SwitchRow("Notification banners", null, Prefs.banners) { Prefs.banners = it; Prefs.save() }
                SwitchRow("Incoming call card", null, Prefs.calls) { Prefs.calls = it; Prefs.save() }
            }
            Section("POSITION OVER THE CAMERA") {
                SliderRow("Horizontal", Prefs.offX) { Prefs.offX = it }
                SliderRow("Vertical", Prefs.offY) { Prefs.offY = it }
            }
            Section("BATTERY") {
                SwitchRow("Animated wallpaper", "Pauses by itself after 25 s without touch", Prefs.wallAnim) {
                    Prefs.wallAnim = it; Prefs.save()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

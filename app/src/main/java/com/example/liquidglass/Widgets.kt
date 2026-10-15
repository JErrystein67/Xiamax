package com.example.liquidglass

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val REQ_BIND = 4712
const val REQ_CONFIGURE = 4713

/**
 * Hosts real Android app widgets (weather, calendar, notes ...) like any launcher does.
 * Adding: pick a widget, Android asks once for permission to bind it, optional config screen, done.
 */
object WidgetHub {
    private const val HOST_ID = 4242
    lateinit var host: AppWidgetHost
    lateinit var mgr: AppWidgetManager
    val ids = mutableStateListOf<Int>()
    var pending = -1
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (::host.isInitialized) return
        val app = ctx.applicationContext
        host = AppWidgetHost(app, HOST_ID)
        try { host.startListening() } catch (e: Exception) { }
        mgr = AppWidgetManager.getInstance(app)
        val p = app.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        sp = p
        p.getString("ids", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { ids.addAll(it) }
    }

    private fun save() {
        sp?.edit()?.putString("ids", ids.joinToString(","))?.apply()
    }

    fun start() { try { host.startListening() } catch (e: Exception) { } }
    fun stop() { try { host.stopListening() } catch (e: Exception) { } }

    fun begin(activity: Activity, info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pending = id
        if (mgr.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind(activity)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                cancel()
            }
        }
    }

    fun afterBind(activity: Activity) {
        val id = pending
        if (id < 0) return
        val info = mgr.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                commit()
            }
        } else {
            commit()
        }
    }

    fun commit() {
        val id = pending
        if (id >= 0 && id !in ids) {
            ids.add(id)
            save()
        }
        pending = -1
    }

    fun cancel() {
        if (pending >= 0) {
            try { host.deleteAppWidgetId(pending) } catch (e: Exception) { }
        }
        pending = -1
    }

    fun remove(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (e: Exception) { }
        ids.remove(id)
        save()
    }
}

@Composable
private fun WPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .pressable(.9f, .92f, onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (filled) Brush.linearGradient(listOf(Pal.violet, Pal.pink))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .14f)))
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** The widgets page, right of the home page. */
@Composable
fun WidgetsPage(onAdd: () -> Unit) {
    var edit by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", color = Pal.ink, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            if (WidgetHub.ids.isNotEmpty()) {
                WPill(if (edit) "Done" else "Edit", false) { edit = !edit }
                Spacer(Modifier.width(8.dp))
            }
            WPill("+ Add", true, onAdd)
        }
        if (WidgetHub.ids.isEmpty()) {
            Column(Modifier.fillMaxWidth().glass(24.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No widgets yet", color = Pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Tap + Add to place weather, calendar, notes, music and any other widget your apps offer.", color = Pal.ink2, fontSize = 14.sp)
            }
        }
        WidgetHub.ids.toList().forEach { id -> key(id) { WidgetCard(id, edit) } }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WidgetCard(id: Int, edit: Boolean) {
    val info = remember(id) { WidgetHub.mgr.getAppWidgetInfo(id) }
    if (info == null) {
        LaunchedEffect(id) { WidgetHub.remove(id) }
        return
    }
    val hDp = if (info.minHeight > 0) info.minHeight.toFloat().coerceIn(90f, 400f) else 150f

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(hDp.dp)
            .glass(24.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        val wDp = maxWidth.value
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                try {
                    WidgetHub.host.createView(c, id, info)
                } catch (e: Exception) {
                    android.widget.TextView(c).apply {
                        text = "Error loading widget"
                        setTextColor(android.graphics.Color.WHITE)
                    }
                }
            },
            update = { v ->
                @Suppress("DEPRECATION")
                try {
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp.toInt())
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp.toInt())
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp.toInt())
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp.toInt())
                    }
                    v.updateAppWidgetSize(options, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
                } catch (e: Exception) { }
            }
        )
        if (edit) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .pressable(.85f, .85f) { WidgetHub.remove(id) }
                    .clip(CircleShape)
                    .background(Color(0xDDFF4B5C)),
                contentAlignment = Alignment.Center
            ) { Text("×", color = Color.White, fontSize = 20.sp) }
        }
    }
}

// ---------------------------------------------------------------- picker

class PickItem(val info: AppWidgetProviderInfo, val label: String, val app: String, val preview: ImageBitmap?, val size: String)

private fun loadProviders(ctx: Context): List<PickItem> {
    val pm = ctx.packageManager
    val mgr = AppWidgetManager.getInstance(ctx)
    val dens = ctx.resources.displayMetrics.density
    return try {
        mgr.installedProviders.filter {
            (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0
        }.map { info ->
            val label = try { info.loadLabel(pm) } catch (e: Exception) { info.provider.packageName }
            val app = try { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() } catch (e: Exception) { info.provider.packageName }
            val prev: ImageBitmap? = try {
                val d: Drawable? = info.loadPreviewImage(ctx, 0)
                if (d != null) {
                    val w = d.intrinsicWidth.coerceIn(120, 600)
                    val h = d.intrinsicHeight.coerceIn(80, 420)
                    d.toBitmap(w, h).asImageBitmap()
                } else null
            } catch (e: Exception) { null }
            val size = "${(info.minWidth / dens).toInt()} × ${(info.minHeight / dens).toInt()} dp"
            PickItem(info, label, app, prev, size)
        }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun WidgetPicker(activity: Activity?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val items by produceState(emptyList<PickItem>()) {
        value = withContext(Dispatchers.Default) { loadProviders(ctx) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF20B0920), Color(0xFA0B0920))))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .padding(top = 28.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add a widget", color = Pal.ink, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).pressable(.88f, .88f, onClose).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White) }
        }
        if (items.isEmpty()) {
            Text("Loading widgets…", color = Pal.ink2, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.info.provider.flattenToString() }) { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(.98f, .98f) {
                            if (activity != null) {
                                WidgetHub.begin(activity, p.info)
                                onClose()
                            }
                        }
                        .glass(22.dp)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(width = 110.dp, height = 70.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (p.preview != null) Image(p.preview, null, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.label, color = Pal.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.app, color = Pal.mint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.size, color = Pal.ink2, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
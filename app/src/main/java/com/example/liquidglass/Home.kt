package com.example.liquidglass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(Prefs.firstRun) }
    var pkgVer by remember { mutableIntStateOf(0) }
    var touch by remember { mutableLongStateOf(0L) }
    var calm by remember { mutableStateOf(false) }

    // drawer position: 1 = closed (below the screen), 0 = fully open. It follows the finger.
    var frac by remember { mutableFloatStateOf(1f) }
    val jobHolder = remember { arrayOfNulls<Job>(1) }
    val lastDy = remember { floatArrayOf(0f) }
    val drawerOpen by remember { derivedStateOf { frac < 0.98f } }
    val fullyOpen by remember { derivedStateOf { frac <= 0.001f } }

    fun animateDrawer(target: Float) {
        jobHolder[0]?.cancel()
        jobHolder[0] = scope.launch {
            animate(frac, target, 0f, spring(dampingRatio = .9f, stiffness = 260f)) { v, _ -> frac = v }
        }
    }
    fun dragStart() { jobHolder[0]?.cancel(); lastDy[0] = 0f }
    fun settle() {
        val t = when {
            lastDy[0] < -6f -> 0f
            lastDy[0] > 6f -> 1f
            frac < .6f -> 0f
            else -> 1f
        }
        animateDrawer(t)
    }

    // refresh app list when apps are installed / removed
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { pkgVer++ }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    val apps by produceState(emptyList<AppItem>(), pkgVer) {
        value = withContext(Dispatchers.Default) { loadApps(ctx) }
    }

    // battery saver: wallpaper drift pauses after 25 s without touch
    LaunchedEffect(touch) { calm = false; delay(25_000); calm = true }
    var lastHome by remember { mutableIntStateOf(Signals.home) }
    LaunchedEffect(Signals.home) {
        if (Signals.home != lastHome) {
            lastHome = Signals.home
            animateDrawer(1f); expanded = false; settingsOpen = false
        }
    }
    LaunchedEffect(MediaHub.active) { if (!MediaHub.active) expanded = false }
    BackHandler(enabled = settingsOpen || drawerOpen || expanded) {
        when {
            settingsOpen -> { settingsOpen = false; Prefs.firstRun = false; Prefs.save() }
            drawerOpen -> animateDrawer(1f)
            else -> expanded = false
        }
    }

    val d = LocalDensity.current
    val barPx = maxOf(WindowInsets.statusBars.getTop(d), WindowInsets.displayCutout.getTop(d))
    val topPad = maxOf(6.dp, (with(d) { barPx.toDp() } - 36.dp) / 2)

    val brandApps = remember(apps) { brands.mapNotNull { b -> apps.firstOrNull { it.pkg in b.pkgs } } }
    val favs = remember(apps) {
        (brandApps + apps.filter { a -> a !in brandApps && brandFor(a.pkg) == null }).take(8)
    }
    val dock = remember { systemDock() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Pal.base)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        val n = System.nanoTime()
                        if (n - touch > 3_000_000_000L) touch = n
                    }
                }
            }
    ) {
        val maxW = maxWidth - 24.dp
        val hPx = constraints.maxHeight.toFloat()
        Wallpaper(!calm && !drawerOpen && Prefs.wallAnim)

        // ---- home content ----
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = 1f - frac
                    alpha = 1f - .5f * k
                    scaleX = 1f - .05f * k
                    scaleY = scaleX
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 8.dp)
                .pointerInput(fullyOpen) {
                    if (!fullyOpen) {
                        detectVerticalDragGestures(
                            onDragStart = { dragStart() },
                            onDragEnd = { settle() },
                            onDragCancel = { settle() },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                lastDy[0] = dy
                                frac = (frac + dy / hPx).coerceIn(0f, 1f)
                            }
                        )
                    }
                }
        ) {
            Row(Modifier.fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ClockCard(Modifier.weight(1.55f).fillMaxHeight())
                BatteryCard(Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp)
                    .glass(RoundedCornerShape(30.dp))
                    .padding(vertical = 18.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                favs.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { AppItemTile(it) }
                        repeat(4 - row.size) { Spacer(Modifier.width(66.dp)) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!MediaHub.hasAccess) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .pressable { settingsOpen = true }
                        .glass(RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Set up the Dynamic Island", color = Pal.ink, fontSize = 13.sp)
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(80.dp)
                    .height(28.dp)
                    .pressable { animateDrawer(0f) },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .45f)))
            }
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(38.dp)).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dock.forEach { s -> AppTile(null, s.c1, s.c2, s.intent) { GlyphIcon(s.glyph) } }
            }
        }

        // ---- app drawer: always composed, slides with the finger ----
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = frac * size.height
                    alpha = if (frac >= 1f) 0f else 1f
                }
        ) {
            Drawer(
                apps = apps,
                onDragStart = { dragStart() },
                onDrag = { dy -> lastDy[0] = dy; frac = (frac + dy / hPx).coerceIn(0f, 1f) },
                onDragEnd = { settle() },
                onClose = { animateDrawer(1f) },
                onSettings = { settingsOpen = true }
            )
        }

        // ---- island (above everything). Hidden here when the always-on overlay is showing it. ----
        if (!(Prefs.overlay && OverlayState.running)) {
            Box(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).offset(Prefs.offX.dp, Prefs.offY.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Island(expanded, { expanded = it }, topPad, maxW)
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInVertically(spring<IntOffset>(dampingRatio = .9f, stiffness = 300f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180))
        ) {
            IslandSettings { settingsOpen = false; Prefs.firstRun = false; Prefs.save() }
        }
    }
}

@Composable
private fun Wallpaper(animate: Boolean) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (animate) {
            var last = 0L
            while (true) {
                withFrameNanos { n -> if (n - last > 33_000_000L) { last = n; phase = n / 1_000_000_000f } }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = phase
        val w = size.width
        val h = size.height
        drawRect(Pal.base)
        fun blob(c: Color, cx: Float, cy: Float, r: Float, ax: Float, ay: Float, sp: Float, ph: Float, a: Float) {
            val x = cx + ax * sin(p * sp + ph)
            val y = cy + ay * cos(p * sp * .8f + ph)
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(x, y), radius = r),
                radius = r, center = Offset(x, y)
            )
        }
        blob(Pal.violet, w * .05f, h * .30f, w * 1.0f, w * .18f, h * .05f, .25f, 0f, .85f)
        blob(Pal.mint, w * .95f, h * .55f, w * .9f, w * .16f, h * .06f, .20f, 2f, .70f)
        blob(Pal.pink, w * .25f, h * .92f, w * 1.0f, w * .20f, h * .04f, .17f, 4f, .75f)
        blob(Pal.peach, w * .85f, h * .04f, w * .6f, w * .12f, h * .03f, .22f, 1f, .45f)
    }
}

@Composable
private fun ClockCard(modifier: Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { now = System.currentTimeMillis() }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    Column(modifier.glass(RoundedCornerShape(30.dp)).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(time, color = Pal.ink, fontSize = 60.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-2).sp, maxLines = 1)
            Text(date, color = Pal.ink2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // weather is a placeholder for now (needs a data source)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFFF3C4), Pal.peach)))
            )
            Spacer(Modifier.width(10.dp))
            Text("18°", color = Pal.ink, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(8.dp))
            Text("Partly cloudy", color = Pal.ink2, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun BatteryCard(modifier: Modifier) {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(100) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            if (i == null) return
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) level = l * 100 / s
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { read(i) }
        }
        read(ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { ctx.unregisterReceiver(r) }
    }
    val sweep by animateFloatAsState(level * 3.6f, tween(900), label = "batt")
    Box(modifier.glass(RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 9.dp.toPx()
                val tl = Offset(sw / 2, sw / 2)
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(Color.White.copy(alpha = .14f), -90f, 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(Brush.linearGradient(listOf(Pal.mint, Pal.violet)), -90f, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$level", color = Pal.ink, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(if (charging) "Charging" else "Battery", color = Pal.ink2, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun AppTile(
    label: String?, c1: Color, c2: Color, intent: Intent,
    light: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(
        Modifier
            .onGloballyPositioned { coords[0] = it }
            .pressable(.9f, .94f) {
                val r = try { coords[0]?.takeIf { it.isAttached }?.boundsInWindow() } catch (e: Exception) { null }
                launchApp(ctx, view, intent, r)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassTile(c1, c2, light = light, content = content)
        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                label, color = Pal.ink2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.width(66.dp)
            )
        }
    }
}

@Composable
fun AppItemTile(a: AppItem, light: Boolean = false) {
    val b = brandFor(a.pkg)
    if (b != null) {
        AppTile(a.label, b.c1, b.c2, a.intent, light) { GlyphIcon(b.glyph) }
    } else {
        AppTile(a.label, Color(0xFF9AA3E8), Color(0xFF4A5280), a.intent, light) {
            a.icon?.let { Image(it, null, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))) }
        }
    }
}

@Composable
private fun Drawer(
    apps: List<AppItem>,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) {
        if (q.isBlank()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF20B0920), Color(0xFA0B0920))))
            .pointerInput(Unit) { detectTapGestures { } } // swallow touches so nothing behind reacts
            .statusBarsPadding()
            .padding(top = 28.dp)
    ) {
        // drag handle: pull down to close
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
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
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .4f)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .glass(RoundedCornerShape(23.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Pal.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(Pal.mint),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) Text("Search apps", color = Pal.ink2, fontSize = 16.sp)
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(46.dp).pressable(.88f, .88f, onSettings).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, null, Modifier.size(22.dp), tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(46.dp).pressable(.88f, .88f, onClose).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.weight(1f).navigationBarsPadding()
        ) {
            items(list, key = { it.id }, contentType = { "app" }) { AppItemTile(it, light = true) }
        }
    }
}

package com.example.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Shadow
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

private val LabelShadow = Shadow(Color.Black.copy(alpha = .35f), Offset(0f, 1f), 3f)

@Composable
fun HomeScreen(activity: Activity? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(Prefs.firstRun) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pkgVer by remember { mutableIntStateOf(0) }

    // pull-down panels: shade 0 = closed, 1 = open. Side 0 = notifications (left), 1 = control center (right).
    var shade by remember { mutableFloatStateOf(0f) }
    var shadeSide by remember { mutableIntStateOf(0) }
    val shadeJob = remember { arrayOfNulls<Job>(1) }
    val shadeDy = remember { floatArrayOf(0f) }
    val shadeOpen by remember { derivedStateOf { shade > 0.001f } }

    fun animateShade(t: Float) {
        shadeJob[0]?.cancel()
        shadeJob[0] = scope.launch {
            animate(shade, t, 0f, spring(dampingRatio = .85f, stiffness = 650f)) { v, _ -> shade = v }
        }
    }
    fun settleShade() {
        animateShade(
            when {
                shadeDy[0] > 6f -> 1f
                shadeDy[0] < -6f -> 0f
                shade > .4f -> 1f
                else -> 0f
            }
        )
    }

    // app drawer: 1 = closed (below the screen), 0 = fully open. It follows the finger.
    var frac by remember { mutableFloatStateOf(1f) }
    val drawerJob = remember { arrayOfNulls<Job>(1) }
    val drawerDy = remember { floatArrayOf(0f) }
    val drawerOpen by remember { derivedStateOf { frac < 0.98f } }
    val drawerFull by remember { derivedStateOf { frac <= 0.001f } }

    fun animateDrawer(t: Float) {
        drawerJob[0]?.cancel()
        drawerJob[0] = scope.launch {
            animate(frac, t, 0f, spring(dampingRatio = .85f, stiffness = 650f)) { v, _ -> frac = v }
        }
    }
    fun drawerSettle() {
        animateDrawer(
            when {
                drawerDy[0] < -6f -> 0f
                drawerDy[0] > 6f -> 1f
                frac < .6f -> 0f
                else -> 1f
            }
        )
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
    val wantFull = Prefs.iconStyle != 1
    val apps by produceState(emptyList<AppItem>(), pkgVer, wantFull) {
        value = withContext(Dispatchers.Default) { loadApps(ctx, wantFull) }
    }

    val brandApps = remember(apps) { brands.mapNotNull { b -> apps.firstOrNull { it.pkg in b.pkgs } } }
    val favs = remember(apps) {
        (brandApps + apps.filter { a -> a !in brandApps && brandFor(a.pkg) == null }).take(8)
    }
    val appPages = remember(apps, favs) { apps.filter { it !in favs }.chunked(24) }
    val pageCount = 2 + maxOf(1, appPages.size) // smart page, home, app pages
    val pagerState = rememberPagerState(initialPage = 1) { pageCount }

    // refresh weather + calendar whenever the launcher comes back
    LaunchedEffect(Signals.resume) { WeatherHub.refresh(ctx); CalendarHub.refresh(ctx) }
    var lastHome by remember { mutableIntStateOf(Signals.home) }
    LaunchedEffect(Signals.home) {
        if (Signals.home != lastHome) {
            lastHome = Signals.home
            animateShade(0f); animateDrawer(1f); expanded = false; settingsOpen = false; pickerOpen = false
            pagerState.animateScrollToPage(1)
        }
    }
    LaunchedEffect(MediaHub.active) { if (!MediaHub.active) expanded = false }
    BackHandler(enabled = Signals.colorTarget != null || pickerOpen || settingsOpen || drawerOpen || shadeOpen || expanded || pagerState.currentPage != 1) {
        when {
            Signals.colorTarget != null -> Signals.colorTarget = null
            pickerOpen -> pickerOpen = false
            settingsOpen -> { settingsOpen = false; Prefs.firstRun = false; Prefs.save() }
            drawerOpen -> animateDrawer(1f)
            shadeOpen -> animateShade(0f)
            expanded -> expanded = false
            else -> scope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    val d = LocalDensity.current
    val barPx = maxOf(WindowInsets.statusBars.getTop(d), WindowInsets.displayCutout.getTop(d))
    val barDp = with(d) { barPx.toDp() }
    val topPad = maxOf(6.dp, (barDp - 36.dp) / 2)

    val dock by produceState(emptyList<DockItem>()) { value = withContext(Dispatchers.Default) { loadDock(ctx) } }

    BoxWithConstraints(
        Modifier.fillMaxSize()
    ) {
        val maxW = maxWidth - 24.dp
        val backdrop = rememberBackdrop(constraints.maxWidth, constraints.maxHeight)
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
        val hPx = constraints.maxHeight.toFloat()
        val panelH = maxHeight * .84f
        val panelPx = hPx * .84f
        WallpaperLayer()

        // ---- pages + page dots + dock ----
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = 1f - frac
                    alpha = 1f - .4f * shade - .5f * k
                    scaleX = 1f - .04f * shade - .05f * k
                    scaleY = scaleX
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 56.dp, bottom = 6.dp)
                .pointerInput(drawerFull) {
                    // swipe up anywhere on the pages to pull the app drawer up with your finger
                    if (!drawerFull) {
                        detectVerticalDragGestures(
                            onDragStart = { drawerJob[0]?.cancel(); drawerDy[0] = 0f },
                            onDragEnd = { drawerSettle() },
                            onDragCancel = { drawerSettle() },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                drawerDy[0] = dy
                                frac = (frac + dy / hPx).coerceIn(0f, 1f)
                            }
                        )
                    }
                }
        ) {
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage == 0 && Prefs.leftPage == 1) {
                    // The left-most page acts as the Google-app shortcut. Return the launcher
                    // pager to Home first, so coming back from Google lands on the Home screen.
                    pagerState.animateScrollToPage(1)
                    val google = ctx.packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
                        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                    try { ctx.startActivity(google) } catch (e: Exception) { }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = if (Prefs.perf == 0) 0 else 1
            ) { page ->
                when (page) {
                    0 -> if (Prefs.leftPage == 0) SmartPage(onSearch = { animateDrawer(0f) }, onAddWidget = { pickerOpen = true }) else Box(Modifier.fillMaxSize())
                    1 -> HomePage(favs) { settingsOpen = true }
                    else -> AppPage(appPages.getOrNull(page - 2) ?: emptyList())
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PageDots(pageCount, pagerState.currentPage) { i -> scope.launch { pagerState.animateScrollToPage(i) } }
            }
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .glass(38.dp)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dock.forEach { s -> DockIcon(s) }
                AppsButton { animateDrawer(0f) }
            }
        }

        // ---- app drawer: always composed, slides with the finger ----
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = frac.coerceIn(0f, 1f) * size.height
                    alpha = if (frac >= 1f) 0f else 1f
                }
        ) {
            AppDrawer(
                apps = apps,
                onDragStart = { drawerJob[0]?.cancel(); drawerDy[0] = 0f },
                onDrag = { dy -> drawerDy[0] = dy; frac = (frac + dy / hPx).coerceIn(0f, 1f) },
                onDragEnd = { drawerSettle() },
                onClose = { animateDrawer(1f) },
                onSettings = { animateDrawer(1f); settingsOpen = true }
            )
        }

        // ---- top edge: pull down on the left half for notifications, right half for the control center ----
        if (Prefs.shadeMode == 1) Box(
            Modifier
                .fillMaxWidth()
                .height(barDp + 36.dp)
                .align(Alignment.TopCenter)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            shadeJob[0]?.cancel()
                            shadeSide = if (o.x < size.width / 2f) 0 else 1
                            shadeDy[0] = 0f
                        },
                        onDragEnd = { settleShade() },
                        onDragCancel = { settleShade() },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            shadeDy[0] = dy
                            shade = (shade + dy / panelPx).coerceIn(0f, 1f)
                        }
                    )
                }
        )

        if (shadeOpen) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = shade }
                        .background(Color.Black.copy(alpha = .45f))
                        .pointerInput(Unit) { detectTapGestures { animateShade(0f) } }
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(panelH)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { translationY = -(1f - shade.coerceIn(0f, 1f)) * size.height }
                ) {
                    val ds = { shadeJob[0]?.cancel(); shadeDy[0] = 0f }
                    val dr = { dy: Float -> shadeDy[0] = dy; shade = (shade + dy / panelPx).coerceIn(0f, 1f) }
                    val de = { settleShade() }
                    if (shadeSide == 0) {
                        NotificationCenter(onOpenSetup = { animateShade(0f); settingsOpen = true }, onDragStart = ds, onDrag = dr, onDragEnd = de)
                    } else {
                        ControlCenter(onOpenIslandSettings = { animateShade(0f); settingsOpen = true }, onDragStart = ds, onDrag = dr, onDragEnd = de)
                    }
                }
            }
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

        AnimatedVisibility(
            visible = pickerOpen,
            enter = slideInVertically(spring<IntOffset>(dampingRatio = .9f, stiffness = 300f)) { it } + fadeIn(tween(150)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180))
        ) {
            WidgetPicker(activity) { pickerOpen = false }
        }

        val ct = Signals.colorTarget
        if (ct != null) {
            ColorSheet(ct, apps.firstOrNull { it.pkg == ct }?.label ?: ct) { Signals.colorTarget = null }
        }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text, color = Color.White.copy(alpha = .92f), fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), style = TextStyle(shadow = LabelShadow)
    )
}

/** Home page as in HyperOS: weather + clock, world clock, then your icons straight on the wallpaper. */
@Composable
private fun HomePage(favs: List<AppItem>, onSetup: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onSetup() }) } // long-press empty space: settings
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                WeatherCard(Modifier.fillMaxWidth().aspectRatio(1f))
                Caption("Weather")
            }
            Column(Modifier.weight(1f)) {
                AnalogClockCard(Modifier.fillMaxWidth().aspectRatio(1f))
                Caption("Clock")
            }
        }
        Spacer(Modifier.height(8.dp))
        WorldClockCard(Modifier.fillMaxWidth().height(72.dp))
        Caption("Clock")
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            favs.chunked(4).take(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { AppItemTile(it) }
                    repeat(4 - row.size) { Spacer(Modifier.width(72.dp)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (!MediaHub.hasAccess) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .pressable { onSetup() }
                        .glass(22.dp)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Set up the Dynamic Island", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

class BatteryInfo(val level: Int, val charging: Boolean)

@Composable
fun rememberBattery(): BatteryInfo {
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
    return BatteryInfo(level, charging)
}

@Composable
fun AppTile(
    label: String?, c1: Color, c2: Color, intent: Intent,
    light: Boolean = false,
    raw: Boolean = false,
    pkg: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val open = {
        val r = try { coords[0]?.takeIf { it.isAttached }?.boundsInWindow() } catch (e: Exception) { null }
        launchApp(ctx, view, intent, r)
    }
    val base = Modifier.onGloballyPositioned { coords[0] = it }
    Column(
        // long-press an app icon to pick its own colour (tinted glass style)
        if (pkg != null) base.pressableLong(.9f, .94f, { Signals.colorTarget = pkg }) { open() }
        else base.pressable(.9f, .94f) { open() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (raw) Box(Modifier.size(Prefs.iconSize.dp), contentAlignment = Alignment.Center, content = content)
        else GlassTile(c1, c2, light = light, content = content)
        if (label != null && Prefs.iconLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                label, color = Color.White.copy(alpha = .94f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.width(72.dp),
                style = TextStyle(shadow = LabelShadow)
            )
        }
    }
}

@Composable
fun AppItemTile(a: AppItem, light: Boolean = false) {
    if (Prefs.iconStyle != 1) {
        // glossy (0) or plain (2): the app's own icon
        AppTile(a.label, a.c1, a.c2, a.intent, light, raw = true, pkg = null) {
            GlossyIcon(a.full ?: a.icon, plain = Prefs.iconStyle == 2, tint = a.c2)
        }
    } else {
        val b = brandFor(a.pkg)
        val (t1, t2) = tintPair(a.pkg, b?.c1 ?: a.c1, b?.c2 ?: a.c2)
        if (b != null) {
            AppTile(a.label, t1, t2, a.intent, light, pkg = a.pkg) { GlyphIcon(b.glyph) }
        } else {
            AppTile(a.label, t1, t2, a.intent, light, pkg = a.pkg) {
                a.icon?.let {
                    // adaptive icons: foreground only, centered, no square behind it
                    Image(it, null, Modifier.size(if (a.adaptive) 50.dp else 36.dp))
                }
            }
        }
    }
}

/** A dock slot: the phone's own default app (dialer, messages, camera, browser) with its real icon. */
@Composable
private fun DockIcon(d: DockItem) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier
            .onGloballyPositioned { coords[0] = it }
            .pressable(.9f, .94f) {
                val r = try { coords[0]?.takeIf { it.isAttached }?.boundsInWindow() } catch (e: Exception) { null }
                launchApp(ctx, view, d.intent, r)
            }
    ) {
        if (d.icon != null && Prefs.iconStyle != 1) GlossyIcon(d.icon, plain = Prefs.iconStyle == 2, tint = d.c2)
        else GlassTile(d.c1, d.c2) { GlyphIcon(d.glyph) }
    }
}

@Composable
private fun AppsButton(onClick: () -> Unit) {
    Column(Modifier.pressable(.9f, .94f, onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassTile(Pal.violet, Pal.mint) { GlyphIcon(Glyph.Apps) }
    }
}

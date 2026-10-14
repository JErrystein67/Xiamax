package com.example.liquidglass

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

private enum class IslandState { Idle, Music, Full, Call, Note, Charge }

@Composable
fun Island(expanded: Boolean, onExpand: (Boolean) -> Unit, topPad: Dp, maxW: Dp, shadowOn: Boolean = true) {
    val note = MediaHub.note
    var lastNote by remember { mutableStateOf<Note?>(null) }
    LaunchedEffect(note) { if (note != null) lastNote = note }
    LaunchedEffect(note) {
        if (note != null && !note.isCall) {
            delay(4200)
            if (MediaHub.note === note) MediaHub.note = null
        }
    }

    val st = when {
        note != null && note.isCall -> IslandState.Call
        note != null -> IslandState.Note
        expanded && MediaHub.active -> IslandState.Full
        ChargeHub.banner -> IslandState.Charge
        MediaHub.active -> IslandState.Music
        else -> IslandState.Idle
    }
    val sz = when (st) {
        IslandState.Idle -> if (Prefs.hideIdle) Triple(0.dp, 0.dp, 0.dp) else Triple(126.dp, 36.dp, 18.dp)
        IslandState.Music -> Triple(214.dp, 36.dp, 18.dp)
        IslandState.Charge -> Triple(214.dp, 36.dp, 18.dp)
        IslandState.Full -> Triple(358.dp, 222.dp, 36.dp)
        IslandState.Call -> Triple(358.dp, 96.dp, 38.dp)
        IslandState.Note -> Triple(344.dp, 76.dp, 32.dp)
    }
    // damping close to 1: quick and settles with almost no bounce
    val spec = spring<Dp>(dampingRatio = 0.85f, stiffness = 400f)
    val w by animateDpAsState(sz.first.coerceAtMost(maxW), spec, label = "w")
    val h by animateDpAsState(sz.second, spec, label = "h")
    val r by animateDpAsState(sz.third, spec, label = "r")
    val shape = RoundedCornerShape(r)
    val base = Color(Prefs.islandColor)
    val op = Prefs.islandOpacity
    val bg = Brush.linearGradient(
        listOf(
            base.copy(alpha = (op * .92f).coerceIn(0f, 1f)),
            Color(base.red * .35f, base.green * .35f, base.blue * .4f, op.coerceIn(0f, 1f))
        )
    )
    val glow = Color(
        minOf(1f, base.red * 1.8f + .15f), minOf(1f, base.green * 1.8f + .15f), minOf(1f, base.blue * 1.8f + .15f)
    )

    Box(
        Modifier
            .padding(top = topPad)
            .size(w, h)
            .then(if (shadowOn) Modifier.softShadow(r, glow, 16.dp, 6.dp, .5f) else Modifier)
            .clip(shape)
            .background(bg)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = .5f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .2f))),
                shape
            )
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.Transparent), endY = size.height * .42f))
            }
            .pointerInput(st) {
                detectTapGestures(onTap = {
                    when (st) {
                        IslandState.Music -> onExpand(true)
                        IslandState.Call, IslandState.Note -> {
                            try { lastNote?.open?.send() } catch (e: Exception) { }
                        }
                        else -> Unit
                    }
                })
            }
            .pointerInput(st) {
                if (st == IslandState.Full) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onVerticalDrag = { _, dy ->
                            acc += dy
                            if (acc < -36f) { acc = 0f; onExpand(false) }
                        }
                    )
                }
            }
    ) {
        Layer(st == IslandState.Music) { CompactMusic() }
        Layer(st == IslandState.Charge) { ChargeCompact() }
        Layer(st == IslandState.Full) { FullPlayer { onExpand(false) } }
        Layer(st == IslandState.Call) { lastNote?.let { CallCard(it) } }
        Layer(st == IslandState.Note) { lastNote?.let { NoteCard(it) } }
    }
}

@Composable
private fun Layer(visible: Boolean, content: @Composable BoxScope.() -> Unit) {
    val a by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(if (visible) 220 else 90, delayMillis = if (visible) 110 else 0),
        label = "layer"
    )
    if (a > 0.01f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = a }, content = content)
}

@Composable
private fun ArtView(size: Dp, corner: Dp) {
    val bmp: Bitmap? = MediaHub.art
    val img = remember(bmp) { bmp?.asImageBitmap() }
    val shape = RoundedCornerShape(corner)
    if (img != null) {
        Image(img, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(Pal.pink, Pal.violet))))
    }
}

@Composable
private fun Waveform(playing: Boolean) {
    val tr = rememberInfiniteTransition(label = "wv")
    val a by tr.animateFloat(.25f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(.3f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by tr.animateFloat(.2f, .9f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    val d by tr.animateFloat(.35f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    Row(Modifier.height(18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(a, b, c, d).forEach { v ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (playing) v else .22f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(Pal.mint, Pal.violet)))
            )
        }
    }
}

@Composable
private fun CompactMusic() {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ArtView(24.dp, 8.dp)
        Waveform(MediaHub.playing && Prefs.perf > 0)
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun CtlButton(size: Dp, bg: Color, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).pressable(.84f, .84f, onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun FullPlayer(onCollapse: () -> Unit) {
    var pos by remember { mutableLongStateOf(0L) }
    var t by remember { mutableFloatStateOf(0f) }
    val dur = MediaHub.durationMs
    LaunchedEffect(Unit) { while (true) { pos = MediaHub.position(); delay(100) } }
    LaunchedEffect(MediaHub.playing) {
        while (MediaHub.playing) { t = (System.nanoTime() / 1_000_000L) / 1000f; delay(if (Prefs.perf == 0) 66L else 33L) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .height(222.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtView(56.dp, 16.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    MediaHub.title.ifEmpty { "Nothing playing" }, color = Pal.ink, fontSize = 17.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(MediaHub.artist, color = Pal.ink2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CtlButton(32.dp, Color.White.copy(alpha = .1f), onCollapse) {
                Icon(Icons.Filled.KeyboardArrowUp, null, Modifier.size(20.dp), tint = Color.White)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val n = 30
            val gap = 3.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            val tt = t
            val on = MediaHub.playing
            for (i in 0 until n) {
                val a = if (on) (.5f + .5f * sin(tt * 3.8f * (1f + (i % 5) * .23f) + i * 1.7f)) * (.55f + .45f * sin(tt * 1.9f + i * .6f)) else 0f
                val bh = 4.dp.toPx() + a * 30.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(listOf(Pal.mint, Pal.violet), startY = size.height - bh, endY = size.height),
                    Offset(i * (bw + gap), size.height - bh), Size(bw, bh), CornerRadius(bw / 3)
                )
            }
        }
        Column {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .pointerInput(dur) {
                        detectTapGestures { o -> if (dur > 0) MediaHub.seek((o.x / size.width * dur).toLong()) }
                    }
                    .pointerInput(dur) {
                        detectHorizontalDragGestures { change, _ ->
                            val x = change.position.x.coerceIn(0f, size.width.toFloat())
                            if (dur > 0) MediaHub.seek((x / size.width * dur).toLong())
                        }
                    }
            ) {
                val barH = 6.dp.toPx()
                val y = (size.height - barH) / 2
                val p = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                drawRoundRect(Color.White.copy(alpha = .16f), Offset(0f, y), Size(size.width, barH), CornerRadius(barH / 2))
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Pal.mint, Pal.violet)),
                    Offset(0f, y), Size(size.width * p, barH), CornerRadius(barH / 2)
                )
                drawCircle(Color.White, 7.dp.toPx(), Offset((size.width * p).coerceIn(7.dp.toPx(), size.width - 7.dp.toPx()), size.height / 2))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), color = Pal.ink2, fontSize = 11.sp)
                Text(fmt(dur), color = Pal.ink2, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CtlButton(42.dp, Color.Transparent, { MediaHub.prev() }) { MediaGlyph(MG.Prev, Color.White, Modifier.size(22.dp)) }
            Spacer(Modifier.width(22.dp))
            CtlButton(50.dp, Color.White, { MediaHub.toggle() }) {
                MediaGlyph(if (MediaHub.playing) MG.Pause else MG.Play, Color(0xFF1A1238), Modifier.size(24.dp))
            }
            Spacer(Modifier.width(22.dp))
            CtlButton(42.dp, Color.Transparent, { MediaHub.next() }) { MediaGlyph(MG.Next, Color.White, Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun CallCard(n: Note) {
    val tr = rememberInfiniteTransition(label = "ring")
    val pr by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearOutSlowInEasing)), label = "p")
    Row(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .height(96.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .drawBehind { drawCircle(Pal.pink.copy(alpha = .5f * (1 - pr)), radius = size.minDimension / 2 + 10.dp.toPx() * pr) }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Pal.peach, Pal.pink))),
            contentAlignment = Alignment.Center
        ) {
            Text(n.title.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(n.title, color = Pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Incoming call · tap to open", color = Pal.ink2, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NoteCard(n: Note) {
    Row(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .height(76.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pal.mint, Pal.violet))),
            contentAlignment = Alignment.Center
        ) {
            Text(n.title.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(n.title, color = Pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n.text, color = Pal.ink2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChargeCompact() {
    val tr = rememberInfiniteTransition(label = "chg")
    val pulse by tr.animateFloat(.55f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Canvas(Modifier.size(18.dp)) {
            val s = size.minDimension / 24f
            val p = Path().apply {
                moveTo(13f * s, 2f * s); lineTo(5f * s, 13.5f * s); lineTo(11f * s, 13.5f * s)
                lineTo(10f * s, 22f * s); lineTo(19f * s, 10f * s); lineTo(13f * s, 10f * s); close()
            }
            drawPath(p, Pal.mint.copy(alpha = pulse))
        }
        Text("${ChargeHub.level}%", color = Pal.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

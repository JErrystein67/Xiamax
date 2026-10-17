package com.example.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

// ------------------------------------------------------------------ squircle (iPhone icon shape)

/** Superellipse |x|^n + |y|^n = 1. n around 4-5 is the iPhone "continuous corner" look, n = 2 is a circle. */
fun squirclePath(w: Float, h: Float, n: Float): Path {
    val p = Path()
    val steps = 160
    val a = w / 2f
    val b = h / 2f
    val e = 2f / n
    for (i in 0..steps) {
        val t = i.toFloat() / steps * (2f * Math.PI.toFloat())
        val c = cos(t)
        val s = sin(t)
        val x = a + a * sign(c) * abs(c).pow(e)
        val y = b + b * sign(s) * abs(s).pow(e)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

class Squircle(private val n: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(squirclePath(size.width, size.height, n))
}

/** Settings roundness (0.2 .. 0.5) -> superellipse exponent. 0.34 is about the iPhone shape. */
fun squircleN(round: Float): Float = 2f + (1f - ((round - .2f) / .3f).coerceIn(0f, 1f)) * 4.5f

// ------------------------------------------------------------------ frosted backdrop (photo wallpaper)

/** A heavily blurred, screen-sized copy of the photo wallpaper. Glass panels draw their own slice of it. */
class Backdrop(val img: ImageBitmap, val rootW: Float, val rootH: Float)

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

// ------------------------------------------------------------------ brushes (created once)

private val GlassFill = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .09f))
)

/**
 * The iOS 26 trick: light catches the top-left and bottom-right edges and dies out in between,
 * so the glass reads like a thick lens, not a flat outline.
 */
private val LiquidRim = Brush.linearGradient(
    0f to Color.White.copy(alpha = .95f),
    .18f to Color.White.copy(alpha = .24f),
    .50f to Color.White.copy(alpha = .05f),
    .82f to Color.White.copy(alpha = .22f),
    1f to Color.White.copy(alpha = .70f)
)

/**
 * Soft drop shadow drawn ONLY outside the shape (Android's elevation shadow shows through translucent
 * glass as a hard-edged box). Skipped in battery saver. shapePath lets squircle tiles cut the exact hole.
 */
fun Modifier.softShadow(
    radius: Dp, color: Color,
    spread: Dp = 16.dp, dy: Dp = 6.dp, alpha: Float = .30f,
    shapePath: ((Float, Float) -> Path)? = null
): Modifier =
    this.drawWithCache {
        val w = size.width
        val h = size.height
        val steps = when (Prefs.perf) { 0 -> 0; 1 -> 2; else -> 4 }
        if (w < 2f || h < 2f || steps == 0) {
            onDrawBehind { }
        } else {
            val r = minOf(radius.toPx(), minOf(w, h) / 2f)
            val hole = shapePath?.invoke(w, h) ?: Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
            val sp = spread.toPx()
            val off = dy.toPx()
            onDrawBehind {
                clipPath(hole, ClipOp.Difference) {
                    for (k in 1..steps) {
                        val g = sp * k / steps
                        drawRoundRect(
                            color.copy(alpha = alpha / steps),
                            Offset(-g, off - g),
                            Size(w + 2 * g, h + 2 * g),
                            CornerRadius(r + g)
                        )
                    }
                }
            }
        }
    }

/**
 * iOS-style liquid glass panel:
 *  1. frosted, saturated slice of the blurred photo wallpaper behind it (photo wallpaper only)
 *  2. translucent fill
 *  3. bright diagonal rim light
 *  4. refraction band: soft light collecting just inside the edge
 *  5. dark inner edge on the far corner for depth, top sheen, specular glint
 */
fun Modifier.glass(radius: Dp): Modifier = composed {
    val bd = LocalBackdrop.current
    val pos = remember { FloatArray(2) }
    var tick by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(radius)
    this
        .onGloballyPositioned { c ->
            if (bd != null) {
                val p = c.positionInRoot()
                if (abs(p.x - pos[0]) > .5f || abs(p.y - pos[1]) > .5f) {
                    pos[0] = p.x
                    pos[1] = p.y
                    tick++
                }
            }
        }
        .softShadow(radius, Color(0xFF030014), spread = 10.dp, dy = 4.dp, alpha = .34f)
        .clip(shape)
        .drawWithCache {
            val b = bd
            val sat = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })
            onDrawBehind {
                val t = tick // redraw when the card moves, so the glass keeps showing the right part of the photo
                if (b != null && t >= 0) {
                    val f = b.img.width / b.rootW
                    val sw = maxOf(1, (size.width * f).toInt())
                    val sh = maxOf(1, (size.height * f).toInt())
                    val sx = (pos[0] * f).toInt().coerceIn(0, maxOf(0, b.img.width - sw))
                    val sy = (pos[1] * f).toInt().coerceIn(0, maxOf(0, b.img.height - sh))
                    drawImage(
                        b.img,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(sw, sh),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        colorFilter = sat,
                        filterQuality = FilterQuality.High
                    )
                }
            }
        }
        .background(GlassFill)
        .border(1.2.dp, LiquidRim, shape)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val r = minOf(radius.toPx(), minOf(w, h) / 2f)
            val rich = Prefs.perf > 0
            val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f), Color.Transparent), endY = h * .30f)
            val spec = Brush.radialGradient(
                listOf(Color.White.copy(alpha = .20f), Color.Transparent),
                center = Offset(w * .15f, 0f), radius = w * .65f
            )
            val farEdge = Brush.linearGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .20f)),
                start = Offset(0f, 0f), end = Offset(w, h)
            )
            val band = 2.dp.toPx()
            val one = 1.dp.toPx()
            onDrawWithContent {
                drawContent()
                if (rich) {
                    for (i in 0..2) {
                        val inset = one * (1.5f + i * 2.5f)
                        drawRoundRect(
                            Color.White.copy(alpha = .14f - i * .045f),
                            Offset(inset, inset),
                            Size(w - 2 * inset, h - 2 * inset),
                            CornerRadius(maxOf(0f, r - inset)),
                            style = Stroke(band)
                        )
                    }
                    drawRoundRect(
                        farEdge, Offset(one, one), Size(w - 2 * one, h - 2 * one),
                        CornerRadius(maxOf(0f, r - one)), style = Stroke(3 * one)
                    )
                    drawRect(sheen)
                    drawRect(spec)
                }
            }
        }
}

// ------------------------------------------------------------------ press feedback

private fun Modifier.pressableImpl(sxPressed: Float, syPressed: Float, onLong: (() -> Unit)?, onClick: () -> Unit): Modifier = composed {
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val click = rememberUpdatedState(onClick)
    val long = rememberUpdatedState(onLong)
    val lp: ((Offset) -> Unit)? = onLong?.let { { _: Offset -> long.value?.invoke(); Unit } }
    this
        .graphicsLayer {
            val k = p.value
            scaleX = 1f + (sxPressed - 1f) * k
            scaleY = 1f + (syPressed - 1f) * k
        }
        .pointerInput(onLong != null) {
            detectTapGestures(
                onPress = {
                    scope.launch { p.animateTo(1f, spring(dampingRatio = .8f, stiffness = 700f)) }
                    tryAwaitRelease()
                    scope.launch { p.animateTo(0f, spring(dampingRatio = .8f, stiffness = 700f)) }
                },
                onLongPress = lp,
                onTap = { click.value() }
            )
        }
}

/** Smooth, springy response to touch. */
fun Modifier.pressable(sxPressed: Float = .93f, syPressed: Float = .95f, onClick: () -> Unit): Modifier =
    pressableImpl(sxPressed, syPressed, null, onClick)

/** Same, with a long-press action (used for per-app icon colours). */
fun Modifier.pressableLong(sxPressed: Float = .93f, syPressed: Float = .95f, onLong: () -> Unit, onClick: () -> Unit): Modifier =
    pressableImpl(sxPressed, syPressed, onLong, onClick)

// ------------------------------------------------------------------ app tile

/**
 * Squircle app tile with iOS-style glass: translucent colour, diagonal rim light, inner refraction band,
 * soft glare, colour caustic in the lower corner.
 * Size, roundness, tint strength and style (glass / flat) come from the settings.
 * light = true skips the outer shadow, which keeps big grids cheap.
 */
@Composable
fun GlassTile(
    c1: Color, c2: Color,
    tileSize: Dp = Prefs.iconSize.dp,
    light: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val n = squircleN(Prefs.iconRound)
    val shape = remember(n) { Squircle(n) }
    val flat = Prefs.iconStyle == 1
    val tint = Prefs.iconAlpha
    val perf = Prefs.perf
    val radius = tileSize * Prefs.iconRound
    val fill = remember(c1, c2, flat, tint) {
        if (flat) Brush.linearGradient(listOf(c1, c2))
        else Brush.linearGradient(listOf(c1.copy(alpha = .62f * tint), c2.copy(alpha = .34f * tint)))
    }
    val base = if (light || flat || perf == 0) Modifier.size(tileSize)
    else Modifier.size(tileSize).softShadow(
        radius, c2, spread = 8.dp, dy = 4.dp, alpha = .38f,
        shapePath = { w, h -> squirclePath(w, h, n) }
    )
    Box(
        base
            .clip(shape)
            .background(fill)
            .then(if (flat) Modifier else Modifier.border(1.dp, LiquidRim, shape))
            .drawWithCache {
                val w = size.width
                val h = size.height
                val rich = !flat && perf > 0
                val path = if (rich) squirclePath(w, h, n) else null
                val caustic = Brush.radialGradient(
                    listOf(c2.copy(alpha = .55f * tint), Color.Transparent),
                    center = Offset(w * .85f, h * .95f), radius = w * .85f
                )
                val glare = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .40f), Color.Transparent),
                    center = Offset(w * .3f, h * .03f), radius = w * .8f
                )
                val topLine = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .32f), Color.Transparent), endY = h * .2f
                )
                val glow = 1.5.dp.toPx()
                val step = 2.2.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    if (!flat) drawRect(caustic)
                    if (rich) {
                        drawRect(glare)
                        drawRect(topLine)
                        if (path != null) {
                            for (i in 1..2) {
                                val inset = i * step
                                val sc = (w - 2 * inset) / w
                                withTransform({ scale(sc, sc, Offset(w / 2f, h / 2f)) }) {
                                    drawPath(path, Color.White.copy(alpha = .16f - i * .05f), style = Stroke(glow / sc))
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // content is designed for a 58 dp tile and scaled to the chosen size
        Box(
            Modifier
                .requiredSize(58.dp)
                .graphicsLayer {
                    val k = tileSize.toPx() / 58.dp.toPx()
                    scaleX = k
                    scaleY = k
                },
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

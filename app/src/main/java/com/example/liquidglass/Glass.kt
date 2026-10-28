package com.example.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.SolidColor
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

/*
 * iOS "Liquid Glass" layer stack, bottom to top:
 *   wallpaper / content
 *   -> ambient shadow (outside the shape only, never behind the glass)
 *   -> glass shell: frosted backdrop slice (photo wallpaper) + translucent fill
 *   -> content plate: faint dark veil so text and glyphs stay readable on bright wallpapers
 *   -> rim light: crisp specular on the top / left edges, soft ambient rim on bottom / right
 *   -> content (text, icons)
 *   -> surface light: top-lit sheen, corner glare, refraction band along the inner edge
 *
 * Performance (Prefs.perf): 0 = fill + rim only, 1 = + specular edges, sheen, glare, light shadow,
 * 2 = + refraction bands, far-edge depth, full shadow. Every brush that does not depend on the size is a
 * top-level constant; size-dependent ones are built inside drawWithCache, so nothing allocates while scrolling.
 */

// ------------------------------------------------------------------ squircle (iPhone icon shape)

private val squircleCache = HashMap<String, Path>()

/** Superellipse |x|^n + |y|^n = 1. n around 4-5 is the iPhone "continuous corner" look, n = 2 is a circle. */
fun squirclePath(w: Float, h: Float, n: Float): Path {
    val key = "${w.toInt()}x${h.toInt()}x${(n * 100).toInt()}"
    squircleCache[key]?.let { return it }
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
    if (squircleCache.size > 48) squircleCache.clear()
    squircleCache[key] = p
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

// ------------------------------------------------------------------ constant brushes / colours (allocated once)

private val ShadowColor = Color(0xFF0B1F1A)
private val SatFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

private val GlassFill = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .07f), Color.White.copy(alpha = .11f))
)

/** Milky glass (calendar, world clock): light, with dark text on top. */
private val MilkFill = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .66f), Color.White.copy(alpha = .46f), Color.White.copy(alpha = .56f))
)
private val NoPlate = SolidColor(Color.Transparent)

/** Content plate: soft ambient contrast between the glass and whatever is drawn on it. */
private val Plate = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = .08f),
    1f to Color.Black.copy(alpha = .20f)
)

/**
 * Rim light as a lens catches it: strong on the top-left, nearly gone through the middle,
 * a soft ambient return on the bottom-right.
 */
private val LiquidRim = Brush.linearGradient(
    0f to Color.White.copy(alpha = .85f),
    .20f to Color.White.copy(alpha = .22f),
    .50f to Color.White.copy(alpha = .05f),
    .85f to Color.White.copy(alpha = .14f),
    1f to Color.White.copy(alpha = .34f)
)

/**
 * Soft ambient shadow drawn ONLY outside the shape: the card's own geometry is cut out of it
 * (ClipOp.Difference), so nothing dark ever sits behind translucent glass.
 * shapePath lets squircle tiles cut the exact hole.
 */
fun Modifier.softShadow(
    radius: Dp, color: Color,
    spread: Dp = 16.dp, dy: Dp = 6.dp, alpha: Float = .30f,
    shapePath: ((Float, Float) -> Path)? = null
): Modifier =
    this.drawWithCache {
        val w = size.width
        val h = size.height
        val steps = when (Prefs.perf) { 0 -> 0; 1 -> 2; else -> 5 }
        if (w < 2f || h < 2f || steps == 0) {
            onDrawBehind { }
        } else {
            val r = minOf(radius.toPx(), minOf(w, h) / 2f)
            val hole = shapePath?.invoke(w, h) ?: Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
            val sp = spread.toPx()
            val off = dy.toPx()
            val layer = color.copy(alpha = alpha / steps)
            onDrawBehind {
                clipPath(hole, ClipOp.Difference) {
                    for (k in 1..steps) {
                        val g = sp * k / steps
                        drawRoundRect(layer, Offset(-g, off - g), Size(w + 2 * g, h + 2 * g), CornerRadius(r + g))
                    }
                }
            }
        }
    }

/** iOS-style liquid glass panel, see the layer stack at the top of this file. */
fun Modifier.glass(radius: Dp, light: Boolean = false): Modifier = composed {
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
        .softShadow(radius, ShadowColor, spread = 12.dp, dy = 5.dp, alpha = .22f)
        .clip(shape)
        // glass shell, layer 1: frosted slice of the photo wallpaper (only when a photo wallpaper is set)
        .drawWithCache {
            val b = bd
            onDrawBehind {
                val t = tick // redraw when the card moves, so it keeps showing the right part of the photo
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
                        colorFilter = SatFilter,
                        filterQuality = FilterQuality.High
                    )
                }
            }
        }
        // glass shell, layer 2: translucent fill, then the content plate
        .background(if (light) MilkFill else GlassFill)
        .background(if (light) NoPlate else Plate)
        .border(1.2.dp, LiquidRim, shape)
        // surface light, drawn above the content
        .drawWithCache {
            val w = size.width
            val h = size.height
            val r = minOf(radius.toPx(), minOf(w, h) / 2f)
            val perf = Prefs.perf
            val specular = perf >= 1
            val rich = perf >= 2
            val hair = 1.dp.toPx()
            val edge = .75.dp.toPx()
            val long = w > 2 * r + 4f && h > 2 * r + 4f

            // crisp specular on top / left, soft ambient rim on bottom / right
            val topHi = if (specular && long) Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = .80f), Color.White.copy(alpha = .30f)), startX = r, endX = w - r
            ) else null
            val leftHi = if (specular && long) Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .70f), Color.White.copy(alpha = .22f)), startY = r, endY = h - r
            ) else null
            val botLo = if (specular && long) Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = .08f), Color.White.copy(alpha = .24f)), startX = r, endX = w - r
            ) else null
            val rightLo = if (specular && long) Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .08f), Color.White.copy(alpha = .22f)), startY = r, endY = h - r
            ) else null

            val sheen = if (specular) Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .13f), Color.Transparent), endY = h * .30f
            ) else null
            val glare = if (specular) Brush.radialGradient(
                listOf(Color.White.copy(alpha = .20f), Color.Transparent), center = Offset(w * .15f, 0f), radius = w * .65f
            ) else null
            val accent = if (rich) Brush.radialGradient(
                listOf(Color.White.copy(alpha = .08f), Color.Transparent), center = Offset(w * .92f, h), radius = w * .40f
            ) else null
            val farEdge = if (rich) Brush.linearGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .20f)),
                start = Offset(0f, 0f), end = Offset(w, h)
            ) else null
            val band = 2.dp.toPx()

            onDrawWithContent {
                drawContent()
                if (specular) {
                    if (topHi != null && leftHi != null && botLo != null && rightLo != null) {
                        drawLine(topHi, Offset(r, edge), Offset(w - r, edge), hair)
                        drawLine(leftHi, Offset(edge, r), Offset(edge, h - r), hair)
                        drawLine(botLo, Offset(r, h - edge), Offset(w - r, h - edge), hair)
                        drawLine(rightLo, Offset(w - edge, r), Offset(w - edge, h - r), hair)
                    }
                    if (sheen != null) drawRect(sheen)
                    if (glare != null) drawRect(glare)
                }
                if (rich) {
                    // refraction band: light collecting just inside the edge, fading inwards
                    for (i in 0..2) {
                        val inset = hair * (1.5f + i * 2.5f)
                        drawRoundRect(
                            Color.White.copy(alpha = .13f - i * .04f),
                            Offset(inset, inset),
                            Size(w - 2 * inset, h - 2 * inset),
                            CornerRadius(maxOf(0f, r - inset)),
                            style = Stroke(band)
                        )
                    }
                    if (farEdge != null) {
                        drawRoundRect(
                            farEdge, Offset(hair, hair), Size(w - 2 * hair, h - 2 * hair),
                            CornerRadius(maxOf(0f, r - hair)), style = Stroke(3 * hair)
                        )
                    }
                    if (accent != null) drawRect(accent)
                }
            }
        }
}

// ------------------------------------------------------------------ press feedback (fluid spring, no recomposition)

private fun Modifier.pressableImpl(sxPressed: Float, syPressed: Float, onLong: (() -> Unit)?, onClick: () -> Unit): Modifier = composed {
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val click = rememberUpdatedState(onClick)
    val long = rememberUpdatedState(onLong)
    val lp: ((Offset) -> Unit)? = onLong?.let { { _: Offset -> long.value?.invoke(); Unit } }
    this
        // the scale is read inside the layer block, so the animation never triggers recomposition
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
 * Squircle app tile, same layer stack as a panel: translucent colour -> content plate behind the glyph ->
 * rim light -> glyph -> surface light (glare, top light, inner refraction bands, colour caustic).
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
    val flat = false
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
                val glass = !flat && perf > 0
                val full = !flat && perf >= 2
                val path = if (glass) squirclePath(w, h, n) else null
                val caustic = if (!flat) Brush.radialGradient(
                    listOf(c2.copy(alpha = .55f * tint), Color.Transparent), center = Offset(w * .85f, h * .95f), radius = w * .85f
                ) else null
                // plate: faint dark core behind the glyph
                val plate = if (glass) Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = .14f), Color.Transparent), center = Offset(w / 2f, h / 2f), radius = w * .42f
                ) else null
                val glare = if (glass) Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .40f), Color.Transparent), center = Offset(w * .3f, h * .03f), radius = w * .8f
                ) else null
                val topLine = if (glass) Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .32f), Color.Transparent), endY = h * .2f
                ) else null
                val glow = 1.5.dp.toPx()
                val step = 2.2.dp.toPx()
                val bands = if (full) 2 else 1
                onDrawWithContent {
                    if (plate != null) drawRect(plate)
                    drawContent()
                    if (caustic != null) drawRect(caustic)
                    if (glare != null) drawRect(glare)
                    if (topLine != null) drawRect(topLine)
                    if (path != null) {
                        for (i in 1..bands) {
                            val inset = i * step
                            val sc = (w - 2 * inset) / w
                            withTransform({ scale(sc, sc, Offset(w / 2f, h / 2f)) }) {
                                drawPath(path, Color.White.copy(alpha = .16f - i * .05f), style = Stroke(glow / sc))
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


/**
 * HyperOS-style glossy app icon: the app's real icon in a squircle with a glass sheen over it,
 * a thin lit rim and a soft shadow in the icon's own colour. plain = no glass, just the icon.
 */
@Composable
fun GlossyIcon(img: ImageBitmap?, plain: Boolean, tint: Color, size: Dp = Prefs.iconSize.dp) {
    val n = squircleN(Prefs.iconRound)
    val shape = remember(n) { Squircle(n) }
    val perf = Prefs.perf
    val base = if (plain || perf == 0) Modifier.size(size)
    else Modifier.size(size).softShadow(
        size * Prefs.iconRound, tint, spread = 7.dp, dy = 4.dp, alpha = .34f,
        shapePath = { w, h -> squirclePath(w, h, n) }
    )
    Box(
        base
            .clip(shape)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val gloss = if (!plain) Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .36f), Color.White.copy(alpha = .06f), Color.Transparent), endY = h * .55f
                ) else null
                val glare = if (!plain && perf > 0) Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .34f), Color.Transparent), center = Offset(w * .28f, h * .02f), radius = w * .7f
                ) else null
                val rim = if (!plain) Brush.verticalGradient(
                    0f to Color.White.copy(alpha = .75f), .4f to Color.White.copy(alpha = .12f), 1f to Color.White.copy(alpha = .28f)
                ) else null
                val path = if (!plain) squirclePath(w, h, n) else null
                val st = 1.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    if (gloss != null) drawRect(gloss)
                    if (glare != null) drawRect(glare)
                    if (rim != null && path != null) drawPath(path, rim, style = Stroke(st))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize())
    }
}

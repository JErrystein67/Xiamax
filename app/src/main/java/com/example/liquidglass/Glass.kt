package com.example.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Brushes are created once. Translucent fills are low alpha so the wallpaper colours show through cleanly.
private val GlassFill = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .18f), Color.White.copy(alpha = .06f), Color.White.copy(alpha = .12f))
)
// Light only on the top edge, fading out. No bright white corners.
private val GlassRim = Brush.verticalGradient(
    0f to Color.White.copy(alpha = .50f),
    .30f to Color.White.copy(alpha = .12f),
    .75f to Color.White.copy(alpha = .05f),
    1f to Color.White.copy(alpha = .20f)
)
private val TileRim = Brush.verticalGradient(
    0f to Color.White.copy(alpha = .65f),
    .35f to Color.White.copy(alpha = .14f),
    .8f to Color.White.copy(alpha = .06f),
    1f to Color.White.copy(alpha = .28f)
)

/**
 * Soft drop shadow drawn ONLY outside the shape.
 * Android's elevation shadow shows through translucent surfaces as a hard-edged rectangle,
 * which is what caused the visible inner boxes. This version clips the inside out.
 */
fun Modifier.softShadow(radius: Dp, color: Color, spread: Dp = 18.dp, dy: Dp = 8.dp, alpha: Float = .35f): Modifier =
    this.drawWithCache {
        val w = size.width
        val h = size.height
        if (w < 2f || h < 2f) {
            onDrawBehind { }
        } else {
            val r = minOf(radius.toPx(), minOf(w, h) / 2f)
            val hole = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
            val sp = spread.toPx()
            val off = dy.toPx()
            val steps = 7
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

/** Frosted glass panel: low-alpha translucent fill, top-lit rim, soft outside shadow, specular sheen. */
fun Modifier.glass(radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .softShadow(radius, Color(0xFF05001F), spread = 10.dp, dy = 4.dp, alpha = .40f)
        .clip(shape)
        .background(GlassFill)
        .border(1.dp, GlassRim, shape)
        .drawWithCache {
            val sheen = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .10f), Color.Transparent), endY = size.height * .35f
            )
            val spec = Brush.radialGradient(
                listOf(Color.White.copy(alpha = .14f), Color.Transparent),
                center = Offset(size.width * .12f, 0f), radius = size.width * .7f
            )
            onDrawWithContent {
                drawContent()
                drawRect(sheen)
                drawRect(spec)
            }
        }
}

/** Press = liquid squash that springs back. Reads the animation in the graphics layer, so no recomposition. */
fun Modifier.pressable(sxPressed: Float = .92f, syPressed: Float = .94f, onClick: () -> Unit): Modifier = composed {
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val click = rememberUpdatedState(onClick)
    this
        .graphicsLayer {
            val k = p.value
            scaleX = 1f + (sxPressed - 1f) * k
            scaleY = 1f + (syPressed - 1f) * k
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scope.launch { p.animateTo(1f, spring(dampingRatio = .85f, stiffness = 600f)) }
                    tryAwaitRelease()
                    scope.launch { p.animateTo(0f, spring(dampingRatio = .85f, stiffness = 600f)) }
                },
                onTap = { click.value() }
            )
        }
}

/**
 * Translucent glass app tile. Everything is clipped to the tile shape, no hard-edged highlight shapes:
 * soft radial glare, thin top light line, coloured caustic at the bottom.
 * light = true skips the outer shadow, which keeps big grids cheap.
 */
@Composable
fun GlassTile(c1: Color, c2: Color, tileSize: Dp = 58.dp, light: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val radius = tileSize * 0.34f
    val shape = RoundedCornerShape(radius)
    val fill = remember(c1, c2) { Brush.linearGradient(listOf(c1.copy(alpha = .55f), c2.copy(alpha = .32f))) }
    val base = if (light) Modifier.size(tileSize)
    else Modifier.size(tileSize).softShadow(radius, c2, spread = 10.dp, dy = 5.dp, alpha = .45f)
    Box(
        base
            .clip(shape)
            .background(fill)
            .border(1.dp, TileRim, shape)
            .drawWithCache {
                val caustic = Brush.verticalGradient(
                    listOf(Color.Transparent, c2.copy(alpha = .5f)), startY = size.height * .5f, endY = size.height
                )
                val glare = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .36f), Color.Transparent),
                    center = Offset(size.width * .3f, size.height * .04f), radius = size.width * .9f
                )
                val topLine = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .30f), Color.Transparent), endY = size.height * .22f
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(caustic)
                    drawRect(glare)
                    drawRect(topLine)
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

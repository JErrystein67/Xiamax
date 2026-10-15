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

// iOS Liquid Glass gradients – čistější, průsvitnější a luxusnější vzhled
private val GlassFill = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .14f))
)
private val GlassRim = Brush.verticalGradient(
    0f to Color.White.copy(alpha = .65f),
    .25f to Color.White.copy(alpha = .15f),
    .75f to Color.White.copy(alpha = .04f),
    1f to Color.White.copy(alpha = .30f)
)
private val TileRim = Brush.verticalGradient(
    0f to Color.White.copy(alpha = .75f),
    .30f to Color.White.copy(alpha = .18f),
    .8f to Color.White.copy(alpha = .05f),
    1f to Color.White.copy(alpha = .35f)
)

/**
 * Optimalizovaný stín: na slabších telefonech (perf == 0 nebo 1) dramaticky snižuje počet vrstev (steps),
 * čímž šetří baterii a zajišťuje absolutní plynulost animací.
 */
fun Modifier.softShadow(radius: Dp, color: Color, spread: Dp = 16.dp, dy: Dp = 6.dp, alpha: Float = .30f): Modifier =
    this.drawWithCache {
        val w = size.width
        val h = size.height
        // Optimalizace pro slabší zařízení: méně výpočetně náročných vrstev stínu
        val steps = when (Prefs.perf) { 0 -> 0; 1 -> 2; else -> 4 }
        if (w < 2f || h < 2f || steps == 0) {
            onDrawBehind { }
        } else {
            val r = minOf(radius.toPx(), minOf(w, h) / 2f)
            val hole = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
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

/** Vylepšený frost-glass panel ve stylu iOS Liquid Glass. */
fun Modifier.glass(radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .softShadow(radius, Color(0xFF030014), spread = 8.dp, dy = 3.dp, alpha = .35f)
        .clip(shape)
        .background(GlassFill)
        .border(1.dp, GlassRim, shape)
        .drawWithCache {
            val full = Prefs.perf > 0
            val sheen = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .12f), Color.Transparent), endY = size.height * .30f
            )
            val spec = Brush.radialGradient(
                listOf(Color.White.copy(alpha = .18f), Color.Transparent),
                center = Offset(size.width * .15f, 0f), radius = size.width * .65f
            )
            onDrawWithContent {
                drawContent()
                if (full) {
                    drawRect(sheen)
                    drawRect(spec)
                }
            }
        }
}

/** Hladká fluidní odezva na dotek (iOS spring physics). */
fun Modifier.pressable(sxPressed: Float = .93f, syPressed: Float = .95f, onClick: () -> Unit): Modifier = composed {
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
                    scope.launch { p.animateTo(1f, spring(dampingRatio = .8f, stiffness = 700f)) }
                    tryAwaitRelease()
                    scope.launch { p.animateTo(0f, spring(dampingRatio = .8f, stiffness = 700f)) }
                },
                onTap = { click.value() }
            )
        }
}

@Composable
fun GlassTile(
    c1: Color, c2: Color,
    tileSize: Dp = Prefs.iconSize.dp,
    light: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val radius = tileSize * Prefs.iconRound
    val shape = RoundedCornerShape(radius)
    val flat = Prefs.iconStyle == 1
    val tint = Prefs.iconAlpha
    val perf = Prefs.perf
    val fill = remember(c1, c2, flat, tint) {
        if (flat) Brush.linearGradient(listOf(c1, c2))
        else Brush.linearGradient(listOf(c1.copy(alpha = .60f * tint), c2.copy(alpha = .35f * tint)))
    }
    val base = if (light || flat || perf == 0) Modifier.size(tileSize)
    else Modifier.size(tileSize).softShadow(radius, c2, spread = 8.dp, dy = 4.dp, alpha = .35f)
    Box(
        base
            .clip(shape)
            .background(fill)
            .then(if (flat) Modifier else Modifier.border(1.dp, TileRim, shape))
            .drawWithCache {
                val fancy = !flat && perf > 1 // Vykreslovat náročné odlesky jen při plném výkonu
                val caustic = Brush.verticalGradient(
                    listOf(Color.Transparent, c2.copy(alpha = .45f * tint)), startY = size.height * .5f, endY = size.height
                )
                val glare = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .40f), Color.Transparent),
                    center = Offset(size.width * .3f, size.height * .03f), radius = size.width * .8f
                )
                val topLine = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .35f), Color.Transparent), endY = size.height * .2f
                )
                onDrawWithContent {
                    drawContent()
                    if (!flat) drawRect(caustic)
                    if (fancy) {
                        drawRect(glare)
                        drawRect(topLine)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
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
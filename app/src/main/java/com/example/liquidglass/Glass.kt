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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// brushes are created once, not on every frame
private val GlassFill = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .10f))
)
private val GlassRim = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .6f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .3f))
)
private val TileRim = Brush.linearGradient(
    listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = .1f), Color.White.copy(alpha = .35f))
)

/** Frosted glass panel: translucent gradient, bright rim, soft shadow, specular sheen on top. */
fun Modifier.glass(shape: Shape): Modifier = this
    .shadow(14.dp, shape, ambientColor = Color(0x66060020), spotColor = Color(0x99060020))
    .clip(shape)
    .background(GlassFill)
    .border(1.dp, GlassRim, shape)
    .drawWithCache {
        val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f), Color.Transparent), endY = size.height * .4f)
        onDrawWithContent {
            drawContent()
            drawRect(sheen)
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
 * Translucent glass app tile with caustic glow at the bottom and a glare across the top edge.
 * light = true skips the elevation shadow, which keeps long scrolling lists smooth.
 */
@Composable
fun GlassTile(c1: Color, c2: Color, tileSize: Dp = 58.dp, light: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(tileSize * 0.36f)
    val fill = remember(c1, c2) { Brush.linearGradient(listOf(c1.copy(alpha = .45f), c2.copy(alpha = .28f))) }
    val base = if (light) Modifier.size(tileSize)
    else Modifier.size(tileSize).shadow(10.dp, shape, ambientColor = c1.copy(alpha = .5f), spotColor = c1.copy(alpha = .8f))
    Box(
        base
            .clip(shape)
            .background(fill)
            .border(1.dp, TileRim, shape)
            .drawWithCache {
                val caustic = Brush.verticalGradient(
                    listOf(Color.Transparent, c2.copy(alpha = .55f)), startY = size.height * .55f, endY = size.height
                )
                val glare = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .5f), Color.White.copy(alpha = .04f)), endY = size.height * .5f
                )
                val tl = Offset(size.width * .06f, size.height * .04f)
                val gs = Size(size.width * .88f, size.height * .46f)
                val cr = CornerRadius(size.width * .3f)
                onDrawWithContent {
                    drawContent()
                    drawRect(caustic)
                    drawRoundRect(glare, topLeft = tl, size = gs, cornerRadius = cr)
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

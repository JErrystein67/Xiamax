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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// Soft black shadow
private val ShadowColor = Color(0xFF000000)

// More transparent Liquid Glass fills
private val GlassFill = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.04f)
    )
)

private val MilkFill = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.25f),
        Color.White.copy(alpha = 0.12f)
    )
)

private val NoPlate = SolidColor(Color.Transparent)
private val Plate = Brush.verticalGradient(
    listOf(
        Color.Black.copy(alpha = 0.03f),
        Color.Black.copy(alpha = 0.08f)
    )
)

private val LiquidRim = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.45f),
        Color.White.copy(alpha = 0.10f)
    )
)

private val SatFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(1.8f) }
)

/**
 * Soft ambient shadow drawn ONLY outside the shape: the card's own geometry is cut out of it
 * (ClipOp.Difference), so nothing dark ever sits behind translucent glass.
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

/** iOS-style liquid glass panel */
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
        .softShadow(radius, ShadowColor, spread = 14.dp, dy = 6.dp, alpha = 0.15f)
        .clip(shape)
        .drawWithCache {
            val b = bd
            onDrawBehind {
                val t = tick
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
        .background(if (light) MilkFill else GlassFill)
        .background(if (light) NoPlate else Plate)
        .border(1.2.dp, LiquidRim, shape)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val r = minOf(radius.toPx(), minOf(w, h) / 2f)
            val perf = Prefs.perf
            val specular = perf >= 1
            val hair = 1.dp.toPx()
            val long = w > 2 * r + 4f && h > 2 * r + 4f

            val topHi = if (specular && long) Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = .80f), Color.White.copy(alpha = .30f)), startX = r, endX = w - r
            ) else null
            val leftHi = if (specular && long) Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .70f), Color.White.copy(alpha = .22f)), startY = r, endY = h - r
            ) else null

            onDrawWithContent {
                drawContent()
                if (topHi != null) drawRect(topHi, Offset(r, 0f), Size(w - 2 * r, hair))
                if (leftHi != null) drawRect(leftHi, Offset(0f, r), Size(hair, h - 2 * r))
            }
        }
}

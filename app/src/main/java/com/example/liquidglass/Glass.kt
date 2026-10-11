package com.example.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Frosted glass panel: translucent gradient, bright rim, soft shadow, specular sheen on top. */
fun Modifier.glass(shape: Shape, strength: Float = 1f): Modifier = this
    .shadow(14.dp, shape, ambientColor = Color(0x66060020), spotColor = Color(0x99060020))
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = .20f * strength),
                Color.White.copy(alpha = .05f * strength),
                Color.White.copy(alpha = .10f * strength)
            )
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = .6f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .3f))),
        shape
    )
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f), Color.Transparent), endY = size.height * .4f))
    }

/** Press = liquid squash (wider than tall) that springs back. */
fun Modifier.pressable(sxPressed: Float = .92f, syPressed: Float = .94f, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sx by animateFloatAsState(if (pressed) sxPressed else 1f, spring(dampingRatio = .7f, stiffness = 600f), label = "sx")
    val sy by animateFloatAsState(if (pressed) syPressed else 1f, spring(dampingRatio = .7f, stiffness = 600f), label = "sy")
    this
        .graphicsLayer { scaleX = sx; scaleY = sy }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Translucent glass app tile with caustic glow at the bottom and a glare across the top edge. */
@Composable
fun GlassTile(c1: Color, c2: Color, tileSize: Dp = 58.dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(tileSize * 0.36f)
    Box(
        Modifier
            .size(tileSize)
            .shadow(10.dp, shape, ambientColor = c1.copy(alpha = .5f), spotColor = c1.copy(alpha = .8f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(c1.copy(alpha = .45f), c2.copy(alpha = .28f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = .1f), Color.White.copy(alpha = .35f))),
                shape
            )
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, c2.copy(alpha = .55f)), startY = size.height * .55f, endY = size.height))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = .5f), Color.White.copy(alpha = .04f)), endY = size.height * .5f),
                    topLeft = Offset(size.width * .06f, size.height * .04f),
                    size = Size(size.width * .88f, size.height * .46f),
                    cornerRadius = CornerRadius(size.width * .3f)
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

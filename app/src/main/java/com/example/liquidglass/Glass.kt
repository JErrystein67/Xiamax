package com.example.liquidglass

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Advanced Liquid Glass Modifier with dynamic drop-shadow and refraction highlights.
 */
fun Modifier.glassEffect(
    cornerRadius: Dp = 26.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.18f),
    borderColor: Color = Color.White.copy(alpha = 0.35f),
    shadowColor: Color = Color.Black.copy(alpha = 0.18f),
    blurRadius: Dp = 16.dp
): Modifier = this
    .drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                color = shadowColor.toArgb()
                maskFilter = BlurMaskFilter(
                    blurRadius.toPx(),
                    BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.nativeCanvas.drawRoundRect(
                0f, 0f, size.width, size.height + 4.dp.toPx(),
                cornerRadius.toPx(), cornerRadius.toPx(),
                paint
            )
        }
    }
    .background(
        color = backgroundColor,
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = 0.05f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

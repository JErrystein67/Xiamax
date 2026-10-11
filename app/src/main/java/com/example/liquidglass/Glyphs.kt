package com.example.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Glyph { Instagram, Facebook, YouTube, WhatsApp, Spotify, Camera, Settings, Phone, Messages }

@Composable
fun GlyphIcon(g: Glyph) {
    when (g) {
        Glyph.Facebook -> Text("f", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Glyph.Settings -> Icon(Icons.Filled.Settings, null, Modifier.size(27.dp), tint = Color.White)
        Glyph.Phone -> Icon(Icons.Filled.Call, null, Modifier.size(26.dp), tint = Color.White)
        Glyph.Messages -> Icon(Icons.Filled.Email, null, Modifier.size(26.dp), tint = Color.White)
        Glyph.WhatsApp -> Box(Modifier.size(27.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawGlyph(g) }
            Icon(Icons.Filled.Call, null, Modifier.size(12.dp), tint = Color.White)
        }
        else -> Canvas(Modifier.size(27.dp)) { drawGlyph(g) }
    }
}

private fun DrawScope.drawGlyph(g: Glyph) {
    val s = size.minDimension / 24f
    val st = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = Color.White
    when (g) {
        Glyph.Instagram -> {
            drawRoundRect(w, Offset(4 * s, 4 * s), Size(16 * s, 16 * s), CornerRadius(5 * s), style = st)
            drawCircle(w, 3.7f * s, Offset(12 * s, 12 * s), style = st)
            drawCircle(w, 1f * s, Offset(17 * s, 7 * s))
        }
        Glyph.YouTube -> {
            drawRoundRect(w, Offset(3 * s, 6 * s), Size(18 * s, 12 * s), CornerRadius(4 * s), style = st)
            val p = Path().apply {
                moveTo(10.3f * s, 9.6f * s); lineTo(14.4f * s, 12f * s); lineTo(10.3f * s, 14.4f * s); close()
            }
            drawPath(p, w)
        }
        Glyph.WhatsApp -> {
            drawCircle(w, 8f * s, Offset(12 * s, 12 * s), style = st)
            val tail = Path().apply {
                moveTo(4f * s, 20.5f * s); lineTo(5.5f * s, 15.5f * s); lineTo(8.5f * s, 18.5f * s); close()
            }
            drawPath(tail, w)
        }
        Glyph.Spotify -> {
            drawCircle(w, 9f * s, Offset(12 * s, 12 * s), style = st)
            for (r in listOf(7f, 4.8f, 2.6f)) {
                drawArc(w, 225f, 90f, false, Offset((12 - r) * s, (17 - r) * s), Size(2 * r * s, 2 * r * s), style = st)
            }
        }
        Glyph.Camera -> {
            drawRoundRect(w, Offset(4 * s, 7.5f * s), Size(16 * s, 12 * s), CornerRadius(3.5f * s), style = st)
            drawCircle(w, 3.2f * s, Offset(12 * s, 13.5f * s), style = st)
            drawLine(w, Offset(9.5f * s, 5f * s), Offset(14.5f * s, 5f * s), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
        }
        else -> Unit
    }
}

enum class MG { Play, Pause, Next, Prev }

@Composable
fun MediaGlyph(m: MG, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (m) {
            MG.Play -> drawPath(Path().apply {
                moveTo(w * .28f, h * .16f); lineTo(w * .86f, h * .5f); lineTo(w * .28f, h * .84f); close()
            }, tint)
            MG.Pause -> {
                drawRoundRect(tint, Offset(w * .22f, h * .16f), Size(w * .2f, h * .68f), CornerRadius(w * .06f))
                drawRoundRect(tint, Offset(w * .58f, h * .16f), Size(w * .2f, h * .68f), CornerRadius(w * .06f))
            }
            MG.Next -> {
                drawPath(Path().apply {
                    moveTo(w * .14f, h * .2f); lineTo(w * .66f, h * .5f); lineTo(w * .14f, h * .8f); close()
                }, tint)
                drawRoundRect(tint, Offset(w * .72f, h * .2f), Size(w * .12f, h * .6f), CornerRadius(w * .04f))
            }
            MG.Prev -> {
                drawPath(Path().apply {
                    moveTo(w * .86f, h * .2f); lineTo(w * .34f, h * .5f); lineTo(w * .86f, h * .8f); close()
                }, tint)
                drawRoundRect(tint, Offset(w * .16f, h * .2f), Size(w * .12f, h * .6f), CornerRadius(w * .04f))
            }
        }
    }
}

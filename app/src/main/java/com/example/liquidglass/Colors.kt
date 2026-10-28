package com.example.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val iconSwatches = listOf(
    0xFF7C5CFF, 0xFFFF6BA6, 0xFFFF3B3B, 0xFFFF9A3C, 0xFFFFD45A, 0xFF22D86E,
    0xFF2DE2C8, 0xFF2F7BFF, 0xFF4B3BFF, 0xFF8A4BFF, 0xFF8E98C4, 0xFFF2F2F7
).map { it.toInt() }

@Composable
private fun SheetPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .pressable(.92f, .94f, onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (filled) Brush.linearGradient(listOf(Pal.violet, Pal.pink))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .14f)))
            )
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Long-press an app icon: give just that app its own colour. */
@Composable
fun ColorSheet(pkg: String, label: String, onClose: () -> Unit) {
    var hue by remember { mutableFloatStateOf(260f) }
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .5f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xF2183029))
                .border(1.dp, Color.White.copy(alpha = .18f), shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Color for $label", color = Pal.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            iconSwatches.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { c ->
                        val on = Prefs.appColors[pkg] == c
                        Box(
                            Modifier
                                .size(40.dp)
                                .pressable(.85f, .85f) { Prefs.setAppColor(pkg, c) }
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (on) 2.5.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = .3f), CircleShape)
                        )
                    }
                }
            }
            Slider(
                value = hue,
                onValueChange = { hue = it; Prefs.setAppColor(pkg, Color.hsv(it, .7f, .95f).toArgb()) },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Pal.mint,
                    inactiveTrackColor = Color.White.copy(alpha = .2f)
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetPill("Automatic", false) { Prefs.setAppColor(pkg, null); onClose() }
                SheetPill("Done", true, onClose)
            }
        }
    }
}

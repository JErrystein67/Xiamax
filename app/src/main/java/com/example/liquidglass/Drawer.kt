package com.example.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full app list. Slides up with the finger (see HomeScreen), vertical list scrolls with normal
 * Android fling physics, search filters as you type.
 */
@Composable
fun AppDrawer(
    apps: List<AppItem>,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) {
        if (q.isBlank()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF20B0920), Color(0xFA0B0920))))
            .pointerInput(Unit) { detectTapGestures { } } // swallow touches so nothing behind reacts
            .statusBarsPadding()
            .padding(top = 28.dp)
    ) {
        // drag handle: pull down to close
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onVerticalDrag = { change, dy -> change.consume(); onDrag(dy) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .4f)))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .glass(23.dp)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Pal.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(Pal.mint),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) Text("Search ${apps.size} apps", color = Pal.ink2, fontSize = 16.sp)
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(46.dp).pressable(.88f, .88f, onSettings).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, null, Modifier.size(22.dp), tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(46.dp).pressable(.88f, .88f, onClose).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.weight(1f).navigationBarsPadding()
        ) {
            items(list, key = { it.id }, contentType = { "app" }) { AppItemTile(it, light = true) }
        }
    }
}

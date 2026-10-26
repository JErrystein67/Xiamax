package com.example.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        val query = q.trim().lowercase()
        if (query.isBlank()) apps else apps.filter { it.label.lowercase().contains(query) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF20B0920), Color(0xFA0B0920))))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .padding(top = 28.dp)
    ) {
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
        val gridState = rememberLazyGridState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxSize().navigationBarsPadding()
            ) {
                items(list, key = { it.id }, contentType = { "app" }) { AppItemTile(it, light = true) }
            }
            
            // Scroll thumb. Everything is derived from stable values, so it never jumps:
            //  - thumb height = viewport / content height, from the row count and one fixed row height
            //    (not from how many items happen to be visible, which changes while flinging)
            //  - thumb position = (first visible row * row stride + pixel offset inside that row) / max scroll,
            //    so it follows the content pixel for pixel
            val thumbAlpha = animateFloatAsState(
                if (gridState.isScrollInProgress) 1f else 0f,
                tween(if (gridState.isScrollInProgress) 80 else 450),
                label = "thumb"
            )
            Canvas(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize()
                    .padding(end = 3.dp, top = 8.dp, bottom = 8.dp)
                    .graphicsLayer { alpha = thumbAlpha.value }
            ) {
                val info = gridState.layoutInfo
                val first = info.visibleItemsInfo.firstOrNull()
                val total = info.totalItemsCount
                if (first != null && total > 0) {
                    val cols = 4
                    val rows = (total + cols - 1) / cols
                    val gap = 18.dp.toPx()
                    val stride = first.size.height + gap           // every tile has the same height
                    val content = rows * stride - gap + 16.dp.toPx() + 40.dp.toPx() // + content padding top / bottom
                    val viewport = info.viewportSize.height.toFloat()
                    val maxScroll = content - viewport
                    if (maxScroll > 1f) {
                        val scrolled = (gridState.firstVisibleItemIndex / cols) * stride + gridState.firstVisibleItemScrollOffset
                        val progress = (scrolled / maxScroll).coerceIn(0f, 1f)
                        val track = size.height
                        val thumbH = (track * (viewport / content)).coerceIn(36.dp.toPx(), track)
                        val y = (track - thumbH) * progress
                        val x = size.width - 2.dp.toPx()
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, track), 3.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.70f), Offset(x, y), Offset(x, y + thumbH), 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
package com.example.liquidglass

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class Palette(val name: String, val a: Color, val b: Color, val c: Color, val d: Color)

val palettes = listOf(
    Palette("Aurora", Pal.violet, Pal.mint, Pal.pink, Pal.peach),
    Palette("Sunset", Color(0xFFFF6B6B), Color(0xFFFFB86B), Color(0xFF8A4BFF), Color(0xFFFF6BA6)),
    Palette("Ocean", Color(0xFF2F7BFF), Color(0xFF2DE2C8), Color(0xFF7C5CFF), Color(0xFF3BE8FF)),
    Palette("Mono", Color(0xFF3A3F66), Color(0xFF5C6190), Color(0xFF2A2E52), Color(0xFF8E98C4))
)

/** 0 = nothing drawn: the phone's own wallpaper shows through the window. */
@Composable
fun WallpaperLayer(active: Boolean) {
    when (Prefs.wallMode) {
        0 -> Unit
        1 -> GradientWallpaper(active && Prefs.perf == 2 && Prefs.wallAnim)
        else -> ImageWallpaper()
    }
    if (Prefs.wallDim > 0.01f) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Prefs.wallDim)))
    }
}

@Composable
private fun GradientWallpaper(animate: Boolean) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (animate) {
            var last = 0L
            while (true) {
                withFrameNanos { n -> if (n - last > 33_000_000L) { last = n; phase = n / 1_000_000_000f } }
            }
        }
    }
    val pal = palettes[Prefs.wallPalette.coerceIn(0, palettes.size - 1)]
    Canvas(Modifier.fillMaxSize()) {
        val p = phase
        val w = size.width
        val h = size.height
        drawRect(Pal.base)
        fun blob(c: Color, cx: Float, cy: Float, r: Float, ax: Float, ay: Float, sp: Float, ph: Float, a: Float) {
            val x = cx + ax * sin(p * sp + ph)
            val y = cy + ay * cos(p * sp * .8f + ph)
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(x, y), radius = r),
                radius = r, center = Offset(x, y)
            )
        }
        blob(pal.a, w * .05f, h * .30f, w * 1.0f, w * .18f, h * .05f, .25f, 0f, .85f)
        blob(pal.b, w * .95f, h * .55f, w * .9f, w * .16f, h * .06f, .20f, 2f, .70f)
        blob(pal.c, w * .25f, h * .92f, w * 1.0f, w * .20f, h * .04f, .17f, 4f, .75f)
        blob(pal.d, w * .85f, h * .04f, w * .6f, w * .12f, h * .03f, .22f, 1f, .45f)
    }
}

@Composable
private fun ImageWallpaper() {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, Prefs.wallVersion) {
        value = withContext(Dispatchers.IO) { loadWallpaper(ctx) }
    }
    val i = img
    if (i != null) {
        val blurDp = if (Build.VERSION.SDK_INT >= 31) Prefs.wallBlur else 0f
        Image(
            i, null,
            Modifier.fillMaxSize().then(if (blurDp > 0.5f) Modifier.blur(blurDp.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.fillMaxSize().background(Pal.base))
    }
}

fun loadWallpaper(ctx: Context): ImageBitmap? {
    val f = File(ctx.filesDir, "wall.jpg")
    if (!f.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outHeight / s > 2400) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
    return BitmapFactory.decodeFile(f.path, o2)?.asImageBitmap()
}

fun saveWallpaper(ctx: Context, uri: Uri): Boolean = try {
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        File(ctx.filesDir, "wall.jpg").outputStream().use { out -> input.copyTo(out) }
    }
    true
} catch (e: Exception) {
    false
}

fun clearWallpaper(ctx: Context) {
    try { File(ctx.filesDir, "wall.jpg").delete() } catch (e: Exception) { }
}

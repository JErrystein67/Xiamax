package com.example.liquidglass

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class ArtPalette(val name: String, val top: Color, val mid: Color, val bottom: Color, val glow: Color, val deep: Color, val line: Color)

val artPalettes = listOf(
    ArtPalette("Sage", Color(0xFFD7E3DE), Color(0xFFA9C2B9), Color(0xFF7FA397), Color(0xFFEDE6B8), Color(0xFF214E44), Color(0xFFF2B36B)),
    ArtPalette("Dusk", Color(0xFF8FB3A6), Color(0xFF4F8274), Color(0xFF1E4A40), Color(0xFFB9C98A), Color(0xFF0F2B27), Color(0xFFE7A15B)),
    ArtPalette("Sand", Color(0xFFEFE7D3), Color(0xFFD9CFAF), Color(0xFFB7C7B0), Color(0xFFF6E9B7), Color(0xFF6C8F78), Color(0xFFE59C5C)),
    ArtPalette("Mist", Color(0xFFE3E8F0), Color(0xFFB7C3D9), Color(0xFF8EA2C4), Color(0xFFEFE6F2), Color(0xFF3A4C78), Color(0xFFE9A6B8))
)

/** 0 = nothing drawn: the phone's own wallpaper shows through the window. */
@Composable
fun WallpaperLayer() {
    when (Prefs.wallMode) {
        0 -> Unit
        1 -> ArtWallpaper()
        else -> ImageWallpaper()
    }
    if (Prefs.wallDim > 0.01f) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Prefs.wallDim)))
    }
}

/** Soft pastel wallpaper with glowing light and big translucent glass sheets. Drawn once, never animated. */
@Composable
private fun ArtWallpaper() {
    val pal = artPalettes[Prefs.wallPalette.coerceIn(0, artPalettes.size - 1)]
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(pal.top, pal.mid, pal.bottom)))
        fun glow(c: Color, cx: Float, cy: Float, r: Float, a: Float) {
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r, center = Offset(cx, cy)
            )
        }
        glow(pal.glow, w * .35f, h * .80f, w * .95f, .90f)
        glow(pal.glow, w * .05f, h * .42f, w * .60f, .55f)
        glow(pal.mid, w * 1.00f, h * .38f, w * .65f, .70f)
        glow(pal.deep, w * 1.05f, -h * .02f, w * .85f, .95f)
        glow(pal.deep, -w * .05f, h * 1.02f, w * .55f, .45f)

        // big translucent glass leaf
        val leaf = Path().apply {
            moveTo(w * .30f, h * .50f)
            cubicTo(w * .62f, h * .56f, w * .98f, h * .74f, w * .82f, h * 1.05f)
            cubicTo(w * .55f, h * .92f, w * .30f, h * .72f, w * .30f, h * .50f)
            close()
        }
        drawPath(
            leaf,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = .26f), Color.White.copy(alpha = .04f)),
                start = Offset(w * .3f, h * .5f), end = Offset(w * .9f, h)
            )
        )
        drawPath(
            leaf,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = .60f), pal.line.copy(alpha = .55f), Color.Transparent),
                start = Offset(w * .3f, h * .5f), end = Offset(w * .9f, h)
            ),
            style = Stroke(1.5.dp.toPx())
        )
        // big soft ring top-left
        drawCircle(Color.White.copy(alpha = .10f), w * .62f, Offset(w * .18f, h * .14f))
        drawCircle(Color.White.copy(alpha = .26f), w * .62f, Offset(w * .18f, h * .14f), style = Stroke(1.2.dp.toPx()))
        // thin warm diagonal line
        drawLine(pal.line.copy(alpha = .75f), Offset(w * .92f, h * .58f), Offset(w * .52f, h * 1.0f), 1.5.dp.toPx())
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

/** Heavily blurred screen-sized copy of the photo wallpaper, only built for photo wallpapers. */
fun loadBackdrop(ctx: Context, rootW: Int, rootH: Int): Backdrop? {
    val f = File(ctx.filesDir, "wall.jpg")
    if (!f.exists() || rootW <= 0 || rootH <= 0) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / (s * 2) >= 360 && o.outHeight / (s * 2) >= 360) s *= 2
    val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    // centre-crop to the screen shape, exactly like ContentScale.Crop does for the wallpaper itself
    val ar = rootW.toFloat() / rootH
    var cw = src.width
    var ch = (cw / ar).toInt()
    if (ch > src.height) { ch = src.height; cw = (ch * ar).toInt() }
    val crop = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw.coerceAtLeast(1), ch.coerceAtLeast(1))
    // shrink to a tiny image: when it is stretched back up it is a very strong, very cheap blur
    val tw = 48
    val th = (tw / ar).toInt().coerceAtLeast(8)
    val tiny = Bitmap.createScaledBitmap(crop, tw, th, true)
    return Backdrop(tiny.asImageBitmap(), rootW.toFloat(), rootH.toFloat())
}

@Composable
fun rememberBackdrop(wPx: Int, hPx: Int): Backdrop? {
    val ctx = LocalContext.current
    val on = Prefs.wallMode == 2 && Prefs.perf > 0
    val bd by produceState<Backdrop?>(null, on, Prefs.wallVersion, wPx, hPx) {
        value = if (!on) null else withContext(Dispatchers.IO) { loadBackdrop(ctx, wPx, hPx) }
    }
    return bd
}

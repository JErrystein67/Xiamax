package com.example.liquidglass

import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

/** One page of the app grid: 4 columns x 6 rows inside a glass card. */
@Composable
fun AppPage(items: List<AppItem>) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .glass(30.dp)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (c in 0 until 4) {
                    val a = items.getOrNull(r * 4 + c)
                    if (a != null) AppItemTile(a, light = true) else Spacer(Modifier.width(72.dp))
                }
            }
        }
    }
}

@Composable
private fun Dot(on: Boolean, onClick: () -> Unit) {
    val w by animateDpAsState(if (on) 20.dp else 6.dp, spring(dampingRatio = .85f, stiffness = 400f), label = "dot")
    val glow by animateFloatAsState(if (on) 1f else 0f, tween(300), label = "glow")
    Box(Modifier.padding(horizontal = 3.dp, vertical = 8.dp).pressable(.9f, .9f, onClick)) {
        Box(
            Modifier
                .drawBehind {
                    if (glow > .01f) {
                        val rad = size.width * .8f + 9.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(listOf(Pal.mint.copy(alpha = .55f * glow), Color.Transparent), center = center, radius = rad),
                            radius = rad, center = center
                        )
                    }
                }
                .height(6.dp)
                .width(w)
                .clip(CircleShape)
                .background(
                    if (on) Brush.horizontalGradient(listOf(Pal.mint, Pal.violet))
                    else SolidColor(Color.White.copy(alpha = .38f))
                )
        )
    }
}

/** Glass page indicator: the active page stretches into a gradient pill. Tap a dot to jump. */
@Composable
fun PageDots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.glass(16.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until count) Dot(i == current) { onSelect(i) }
    }
}

// ------------------------------------------------------------------ -1 screen

class NewsItem(val title: String, val link: String)

private const val FEED_URL = "https://feeds.bbci.co.uk/news/rss.xml" // change to any RSS feed you like

suspend fun fetchNews(): List<NewsItem> = withContext(Dispatchers.IO) {
    try {
        val conn = URL(FEED_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.inputStream.use { stream ->
            val p = Xml.newPullParser()
            p.setInput(stream, null)
            val out = ArrayList<NewsItem>()
            var inItem = false
            var title = ""
            var link = ""
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT && out.size < 12) {
                if (ev == XmlPullParser.START_TAG) {
                    val name = p.name
                    if (name == "item") {
                        inItem = true; title = ""; link = ""
                    } else if (inItem && name == "title") {
                        title = p.nextText()
                    } else if (inItem && name == "link") {
                        link = p.nextText()
                    }
                } else if (ev == XmlPullParser.END_TAG && p.name == "item") {
                    inItem = false
                    if (title.isNotBlank()) out.add(NewsItem(title.trim(), link.trim()))
                }
                ev = p.next()
            }
            out.toList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
private fun NewsCard(n: NewsItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(.98f, .98f, onClick)
            .glass(24.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("TOP STORY", color = Pal.mint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(n.title, color = Pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** The "-1" screen, left of the home page. */
@Composable
fun DiscoverPage() {
    val ctx = LocalContext.current
    var news by remember { mutableStateOf<List<NewsItem>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        news = null
        news = fetchNews()
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Discover", color = Pal.ink, fontSize = 30.sp, fontWeight = FontWeight.Light)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pressable(.98f, .98f) {
                    ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                }
                .glass(26.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("Search the web", color = Pal.ink2, fontSize = 16.sp)
        }
        val list = news
        when {
            list == null -> repeat(3) { Box(Modifier.fillMaxWidth().height(92.dp).glass(24.dp)) }
            list.isEmpty() -> Column(
                Modifier.fillMaxWidth().glass(24.dp).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Couldn't load the news. Check your connection.", color = Pal.ink2, fontSize = 14.sp)
                Box(
                    Modifier
                        .pressable(.9f, .92f) { reload++ }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .14f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Retry", color = Pal.ink, fontSize = 14.sp) }
            }
            else -> list.forEach { n ->
                NewsCard(n) { if (n.link.isNotEmpty()) ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

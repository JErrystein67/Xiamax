package com.example.liquidglass

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0920"))
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/index.html?native=1")
        }
        setContentView(web)
    }

    /**
     * JS -> Android bridge. A key is one or more candidates separated by "|":
     *   "pkg:com.whatsapp"            launch an installed app by package name
     *   "intent:camera|dial|settings|sms"   system default app for that job
     * The first candidate that resolves wins; if none do, JS shows the demo screen.
     */
    inner class Bridge {
        private fun resolve(key: String): Intent? {
            for (c in key.split("|")) {
                val intent: Intent? = when {
                    c == "intent:camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    c == "intent:dial" -> Intent(Intent.ACTION_DIAL)
                    c == "intent:settings" -> Intent(Settings.ACTION_SETTINGS)
                    c == "intent:sms" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
                    c.startsWith("pkg:") -> packageManager.getLaunchIntentForPackage(c.removePrefix("pkg:"))
                    else -> null
                }
                if (intent != null && intent.resolveActivity(packageManager) != null) {
                    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            return null
        }

        @JavascriptInterface fun canLaunch(key: String): Boolean = resolve(key) != null

        @JavascriptInterface fun launch(key: String): Boolean {
            val intent = resolve(key) ?: return false
            runOnUiThread { startActivity(intent) }
            return true
        }
    }

    // Back collapses the island / closes the open app screen
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.goBack&&window.goBack()", null)
    }

    override fun onPause() { web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
}

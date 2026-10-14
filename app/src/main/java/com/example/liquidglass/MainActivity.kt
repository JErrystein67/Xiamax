package com.example.liquidglass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        WidgetHub.init(applicationContext)
        ChargeHub.start(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { HomeScreen(this) }
    }

    override fun onStart() {
        super.onStart()
        WidgetHub.start()
    }

    override fun onStop() {
        WidgetHub.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        MediaHub.refreshAccess(applicationContext)
        OverlayControl.apply(applicationContext)
        Signals.resume++
    }

    // Home button pressed while already on the home screen: close drawer / settings / island
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) Signals.home++
    }

    // results of the widget permission dialog and widget config screens
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) WidgetHub.afterBind(this) else WidgetHub.cancel()
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK) WidgetHub.commit() else WidgetHub.cancel()
        }
    }
}

package com.example.liquidglass

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object OverlayControl {
    /** Starts or stops the always-on island to match the setting and the permission. */
    fun apply(ctx: Context) {
        try {
            val i = Intent(ctx, OverlayService::class.java)
            if (Prefs.overlay && Settings.canDrawOverlays(ctx)) {
                if (!OverlayState.running) ContextCompat.startForegroundService(ctx, i)
            } else if (OverlayState.running) {
                ctx.stopService(i)
            }
        } catch (e: Exception) {
            // not allowed to start from background right now
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Prefs.init(context)
        OverlayControl.apply(context)
    }
}

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ChargeHub.start(this)
        ssc.performAttach()
        ssc.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!Prefs.overlay || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("island", "Dynamic Island", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "island")
            .setSmallIcon(R.drawable.ic_stat_island)
            .setContentTitle("Dynamic Island is on")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun baseTopPx(): Int {
        val d = resources.displayMetrics.density
        var bar = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val ins = wm!!.currentWindowMetrics.windowInsets
            bar = ins.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
        }
        if (bar == 0) {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) bar = resources.getDimensionPixelSize(id)
        }
        return maxOf((6 * d).toInt(), (bar - (36 * d).toInt()) / 2)
    }

    private fun updatePos() {
        val lp = params ?: return
        val v = view ?: return
        val d = resources.displayMetrics.density
        lp.x = (Prefs.offX * d).toInt()
        lp.y = baseTopPx() + (Prefs.offY * d).toInt()
        try { wm?.updateViewLayout(v, lp) } catch (e: Exception) { }
    }

    private fun showOverlay() {
        if (view != null) return
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        val d = resources.displayMetrics.density
        val maxW: Dp = (resources.displayMetrics.widthPixels / d).dp - 24.dp

        val cv = ComposeView(this)
        cv.setViewTreeLifecycleOwner(this)
        cv.setViewTreeSavedStateRegistryOwner(this)
        cv.setViewTreeViewModelStoreOwner(this)
        cv.setContent { OverlayIsland(maxW) }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        params = lp
        view = cv
        updatePosValues(lp)
        registry.currentState = Lifecycle.State.RESUMED
        w.addView(cv, lp)
        OverlayState.running = true

        val s = CoroutineScope(Dispatchers.Main)
        scope = s
        s.launch { snapshotFlow { Prefs.offX to Prefs.offY }.collect { updatePos() } }
    }

    private fun updatePosValues(lp: WindowManager.LayoutParams) {
        val d = resources.displayMetrics.density
        lp.x = (Prefs.offX * d).toInt()
        lp.y = baseTopPx() + (Prefs.offY * d).toInt()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        try { view?.let { wm?.removeView(it) } } catch (e: Exception) { }
        view = null
        OverlayState.running = false
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }
}

@Composable
fun OverlayIsland(maxW: Dp) {
    var expanded by remember { mutableStateOf(false) }
    // collapse by itself so the window shrinks back and stops covering the app below
    LaunchedEffect(expanded) { if (expanded) { delay(10_000); expanded = false } }
    LaunchedEffect(MediaHub.active) { if (!MediaHub.active) expanded = false }
    Island(expanded, { expanded = it }, 0.dp, maxW, shadowOn = false)
}

package com.example.liquidglass

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

data class Note(val key: String, val title: String, val text: String, val isCall: Boolean, val open: PendingIntent?)

object Signals {
    var home by mutableIntStateOf(0)
}

/** Needs "Notification access" (granted once in system settings). Feeds the island. */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() { MediaHub.start(applicationContext) }
    override fun onNotificationPosted(sbn: StatusBarNotification) { MediaHub.onPosted(applicationContext, sbn) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { MediaHub.onRemoved(sbn) }
}

object MediaHub {
    var hasAccess by mutableStateOf(false)
    var active by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<Bitmap?>(null)
    var playing by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0L)
    var note by mutableStateOf<Note?>(null)

    private var posMs = 0L
    private var stamp = 0L
    private var speed = 1f
    private var controller: MediaController? = null
    private var started = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { readMeta(metadata) }
        override fun onPlaybackStateChanged(state: PlaybackState?) { readState(state) }
        override fun onSessionDestroyed() { controller = null; active = false; playing = false }
    }

    fun refreshAccess(ctx: Context) {
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (hasAccess) start(ctx)
    }

    fun start(ctx: Context) {
        try {
            val cn = ComponentName(ctx, MediaListener::class.java)
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            if (!started) {
                msm.addOnActiveSessionsChangedListener(
                    MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }, cn
                )
                started = true
            }
            pick(msm.getActiveSessions(cn))
            hasAccess = true
        } catch (e: SecurityException) {
            hasAccess = false
        }
    }

    private fun pick(list: List<MediaController>?) {
        controller?.unregisterCallback(callback)
        val c = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        controller = c
        if (c == null) { active = false; playing = false; return }
        c.registerCallback(callback)
        readMeta(c.metadata)
        readState(c.playbackState)
    }

    private fun readMeta(m: MediaMetadata?) {
        title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    }

    private fun readState(s: PlaybackState?) {
        playing = s?.state == PlaybackState.STATE_PLAYING
        posMs = (s?.position ?: 0L).coerceAtLeast(0L)
        stamp = SystemClock.elapsedRealtime()
        speed = s?.playbackSpeed ?: 1f
        active = controller != null && s != null &&
            s.state != PlaybackState.STATE_NONE && s.state != PlaybackState.STATE_STOPPED
    }

    fun position(): Long {
        if (!playing) return posMs
        val p = posMs + ((SystemClock.elapsedRealtime() - stamp) * speed).toLong()
        return if (durationMs > 0) p.coerceAtMost(durationMs) else p
    }

    fun toggle() {
        val t = controller?.transportControls ?: return
        if (playing) t.pause() else t.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }
    fun seek(ms: Long) {
        val v = ms.coerceAtLeast(0L)
        controller?.transportControls?.seekTo(v)
        posMs = v
        stamp = SystemClock.elapsedRealtime()
    }

    fun onPosted(ctx: Context, sbn: StatusBarNotification) {
        if (sbn.packageName == ctx.packageName) return
        val n = sbn.notification
        val incomingCall = n.category == Notification.CATEGORY_CALL && n.fullScreenIntent != null
        if (!incomingCall) {
            if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            if (n.category == Notification.CATEGORY_TRANSPORT) return
        }
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        note = Note(sbn.key, title, text, incomingCall, n.contentIntent)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (note?.key == sbn.key) note = null
    }
}

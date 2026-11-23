package com.mudita.mmd.samples.hermes

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Holds one long-lived HTTP stream to the ntfy topic Hermes publishes to
 * (`GET <server>/<topic>/json`). The Kompakt has no Google Play Services, so FCM is not
 * an option; this is the same approach the ntfy Android app uses in its "instant delivery"
 * mode.
 *
 * Each message is routed by priority: >= [HermesSettings.urgentPriority] breaks through
 * as a full-screen alert, everything else goes quietly into the [Inbox].
 */
class NtfyListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.createChannels(this)
        val notification = Notifier.listenerNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifier.LISTENER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Notifier.LISTENER_NOTIFICATION_ID, notification)
        }
        if (loop?.isActive != true) loop = scope.launch { listenForever() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun listenForever() {
        Inbox.ensureLoaded(this)
        var backoffMs = 2_000L
        while (scope.isActive) {
            val settings = SettingsStore.load(this)
            if (!settings.isNtfyConfigured) {
                stopSelf()
                return
            }
            try {
                streamOnce(settings)
                backoffMs = 2_000L
            } catch (t: Throwable) {
                Log.w(TAG, "ntfy stream dropped: ${t.message}")
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(5 * 60_000L)
        }
    }

    private fun streamOnce(s: HermesSettings) {
        // `since=<last id>` replays anything published while we were disconnected.
        val since = Inbox.lastId(this)?.let { URLEncoder.encode(it, "UTF-8") } ?: "10m"
        val url = URL("${s.ntfyServer}/${URLEncoder.encode(s.ntfyTopic, "UTF-8")}/json?since=$since")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            // ntfy sends a keepalive event every ~45 s; treat 2 min of silence as a dead link.
            readTimeout = 120_000
            if (s.ntfyToken.isNotBlank()) setRequestProperty("Authorization", "Bearer ${s.ntfyToken}")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> if (line.isNotBlank()) handle(JSONObject(line), s) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun handle(o: JSONObject, s: HermesSettings) {
        if (o.optString("event") != "message") return
        val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val item = InboxItem(
            id = o.optString("id"),
            time = o.optLong("time") * 1000,
            title = o.optString("title"),
            message = o.optString("message"),
            priority = o.optInt("priority", 3),
        )
        Inbox.add(this, item)
        if (item.priority >= s.urgentPriority || "urgent" in tags) {
            Notifier.showUrgent(this, item)
        }
    }

    companion object {
        private const val TAG = "HermesNtfy"

        fun start(context: Context) {
            if (!SettingsStore.load(context).isNtfyConfigured) return
            context.startForegroundService(Intent(context, NtfyListenerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfyListenerService::class.java))
        }
    }
}

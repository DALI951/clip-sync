package com.clipsync.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.app.Service
import androidx.core.app.NotificationCompat
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

class ClipboardService : Service() {

    private var socket: Socket? = null
    private var clipboardManager: ClipboardManager? = null
    private var lastClipText: String = ""
    private var isMonitoring = false

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!isMonitoring) return@OnPrimaryClipChangedListener
        checkClipboard()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startSync()
            "STOP" -> stopSync()
        }
        return START_STICKY
    }

    private fun startSync() {
        startForeground(1, createNotification("ClipSync is running"))
        connectSocket()
        startClipboardMonitor()
    }

    private fun stopSync() {
        stopClipboardMonitor()
        socket?.disconnect()
        socket = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun connectSocket() {
        val prefs = getSharedPreferences("clipsync", MODE_PRIVATE)
        val ip = prefs.getString("server_ip", "") ?: ""
        val port = prefs.getString("server_port", "3000") ?: "3000"
        if (ip.isEmpty()) return

        val serverUrl = "http://$ip:$port"

        try {
            val opts = IO.Options.builder()
                .setReconnection(true)
                .setReconnectionDelay(2000)
                .setReconnectionAttempts(-1)
                .build()

            socket = IO.socket(URI.create(serverUrl), opts)

            socket?.on(Socket.EVENT_CONNECT) {
                updateNotification("Connected to server")
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                updateNotification("Disconnected - reconnecting...")
            }

            socket?.on("clipboard-update") { args ->
                val data = args[0] as? JSONObject ?: return@on
                val source = data.optString("source", "")
                val text = data.optString("text", "")
                if (source != "Phone" && text.isNotEmpty()) {
                    lastClipText = text
                    isMonitoring = false
                    val clip = android.content.ClipData.newPlainText("ClipSync", text)
                    clipboardManager?.setPrimaryClip(clip)
                    isMonitoring = true
                }
            }

            socket?.connect()
        } catch (_: Exception) {
            updateNotification("Connection failed - retrying...")
        }
    }

    private fun startClipboardMonitor() {
        isMonitoring = true
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun stopClipboardMonitor() {
        isMonitoring = false
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
    }

    private fun checkClipboard() {
        val clip = clipboardManager?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text.isNotEmpty() && text != lastClipText) {
                lastClipText = text
                sendToServer(text)
            }
        }
    }

    private fun sendToServer(text: String) {
        val data = JSONObject().apply {
            put("text", text)
            put("source", "Phone")
        }
        socket?.emit("clipboard-update", data)
    }

    private fun createNotification(text: String) = NotificationCompat.Builder(this, ClipSyncApp.CHANNEL_ID)
        .setContentTitle("ClipSync")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_send)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1, createNotification(text))
    }

    override fun onDestroy() {
        stopClipboardMonitor()
        socket?.disconnect()
        super.onDestroy()
    }
}

package com.clipsync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("clipsync", Context.MODE_PRIVATE)
            val url = prefs.getString("server_url", "")
            if (!url.isNullOrEmpty()) {
                val serviceIntent = Intent(context, ClipboardService::class.java).apply {
                    action = "START"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

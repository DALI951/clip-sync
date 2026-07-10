package com.clipsync.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI

class MainActivity : AppCompatActivity() {

    private var socket: Socket? = null
    private var isServiceRunning = false
    private lateinit var statusText: TextView
    private lateinit var toggleBtn: Button
    private lateinit var clipList: RecyclerView
    private lateinit var adapter: ClipAdapter
    private lateinit var pcNameText: TextView
    private var multicastLock: MulticastLock? = null
    private var discoverySocket: DatagramSocket? = null
    private var discoveredPC: DiscoveredPC? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var deviceName = ""

    private val items = mutableListOf<ClipItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleBtn = findViewById(R.id.toggleBtn)
        clipList = findViewById(R.id.clipList)
        pcNameText = findViewById(R.id.pcNameText)

        adapter = ClipAdapter(items) { text ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("ClipSync", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }

        clipList.layoutManager = LinearLayoutManager(this)
        clipList.adapter = adapter

        deviceName = getDeviceName()

        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("clipsync-discovery")
        multicastLock?.setReferenceCounted(true)

        toggleBtn.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, ClipboardService::class.java))
                isServiceRunning = false
                toggleBtn.text = "Start Sync"
                statusText.text = "Stopped"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            } else if (discoveredPC != null) {
                startSync()
            } else {
                Toast.makeText(this, "Waiting for PC...", Toast.LENGTH_SHORT).show()
            }
        }

        requestNotificationPermission()
        startListening()
    }

    private fun startSync() {
        val pc = discoveredPC ?: return
        getSharedPreferences("clipsync", MODE_PRIVATE).edit()
            .putString("server_ip", pc.ip)
            .putString("server_port", pc.port)
            .putString("server_name", pc.name)
            .apply()

        val intent = Intent(this, ClipboardService::class.java).apply { action = "START" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        toggleBtn.text = "Stop Sync"
        statusText.text = "Syncing with ${pc.name}"
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        statusText.text = "Listening for PC..."
        multicastLock?.acquire()

        Thread {
            try {
                discoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    setSoTimeout(0)
                    bind(null)
                }

                val buffer = ByteArray(1024)
                while (isListening) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        discoverySocket?.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()

                        if (msg.startsWith("CLIPSYNC_PC:")) {
                            val pcName = msg.removePrefix("CLIPSYNC_PC:").trim()
                            val pcIp = packet.address.hostAddress ?: continue
                            val pcPort = "3000"

                            try {
                                val respData = "CLIPSYNC_PHONE:$deviceName".toByteArray(Charsets.UTF_8)
                                val respPacket = DatagramPacket(respData, respData.size, packet.address, packet.port)
                                discoverySocket?.send(respPacket)
                            } catch (_: Exception) {}

                            handler.post {
                                discoveredPC = DiscoveredPC(pcName, pcIp, pcPort)
                                pcNameText.text = "PC: $pcName ($pcIp)"
                                statusText.text = "Found $pcName — tap Start Sync"
                                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                                Toast.makeText(this, "Found PC: $pcName", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: IOException) {
                        break
                    }
                }
            } catch (_: Exception) {}

            handler.post {
                multicastLock?.release()
                isListening = false
            }
        }.start()
    }

    private fun getDeviceName(): String {
        val name = Settings.Global.getString(contentResolver, "device_name")
        if (!name.isNullOrBlank()) return name
        val model = Build.MODEL
        if (!model.isNullOrBlank()) return model
        return "Android"
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        discoverySocket?.close()
        try { multicastLock?.release() } catch (_: Exception) {}
    }

    data class DiscoveredPC(val name: String, val ip: String, val port: String)
}

data class ClipItem(val text: String, val source: String, val timestamp: String)

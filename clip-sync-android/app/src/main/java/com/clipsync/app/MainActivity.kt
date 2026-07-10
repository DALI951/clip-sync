package com.clipsync.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI

class MainActivity : AppCompatActivity() {

    private var socket: Socket? = null
    private var isServiceRunning = false
    private lateinit var statusText: TextView
    private lateinit var toggleBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var deviceList: LinearLayout
    private lateinit var clipList: RecyclerView
    private lateinit var adapter: ClipAdapter
    private lateinit var scanStatus: TextView
    private var multicastLock: MulticastLock? = null

    private val items = mutableListOf<ClipItem>()
    private val discoveredDevices = mutableListOf<DiscoveredPC>()
    private val handler = Handler(Looper.getMainLooper())
    private var discoverySocket: DatagramSocket? = null
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleBtn = findViewById(R.id.toggleBtn)
        scanBtn = findViewById(R.id.scanBtn)
        deviceList = findViewById(R.id.deviceList)
        clipList = findViewById(R.id.clipList)
        scanStatus = findViewById(R.id.scanStatus)

        adapter = ClipAdapter(items) { text ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("ClipSync", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }

        clipList.layoutManager = LinearLayoutManager(this)
        clipList.adapter = adapter

        // Acquire multicast lock for discovery
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("clipsync-discovery")
        multicastLock?.setReferenceCounted(true)

        // Check if already connected
        val savedIp = getSharedPreferences("clipsync", MODE_PRIVATE).getString("server_ip", "")
        if (!savedIp.isNullOrEmpty()) {
            statusText.text = "Connected to $savedIp"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }

        scanBtn.setOnClickListener {
            startDiscovery()
        }

        toggleBtn.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, ClipboardService::class.java))
                isServiceRunning = false
                toggleBtn.text = "Start Sync"
                statusText.text = "Stopped"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            } else {
                val ip = getSharedPreferences("clipsync", MODE_PRIVATE).getString("server_ip", "")
                if (ip.isNullOrEmpty()) {
                    Toast.makeText(this, "Scan and select a PC first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(this, ClipboardService::class.java).apply { action = "START" }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                isServiceRunning = true
                toggleBtn.text = "Stop Sync"
                statusText.text = "Running"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
        }

        requestNotificationPermission()

        // Auto-scan on first launch if no saved IP
        if (savedIp.isNullOrEmpty()) {
            startDiscovery()
        }
    }

    private fun startDiscovery() {
        if (isScanning) return
        isScanning = true
        scanBtn.isEnabled = false
        scanStatus.text = "Scanning for ClipSync PCs..."
        deviceList.removeAllViews()
        discoveredDevices.clear()

        multicastLock?.acquire()

        Thread {
            try {
                discoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    setSoTimeout(3000)
                    bind(null)
                }

                // Send discovery broadcast
                val msg = "CLIPSYNC_DISCOVER".toByteArray()
                val broadcast = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(msg, msg.size, broadcast, 3000)
                discoverySocket?.send(packet)

                // Also try subnet broadcast
                try {
                    val localIp = getLocalIPv4()
                    val parts = localIp.split(".")
                    if (parts.size == 4) {
                        val subnetBroadcast = InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
                        val subnetPacket = DatagramPacket(msg, msg.size, subnetBroadcast, 3000)
                        discoverySocket?.send(subnetPacket)
                    }
                } catch (_: Exception) {}

                // Listen for responses
                val buffer = ByteArray(1024)
                while (isScanning) {
                    try {
                        val response = DatagramPacket(buffer, buffer.size)
                        discoverySocket?.receive(response)
                        val responseStr = String(response.data, 0, response.length, Charsets.UTF_8)
                        if (responseStr.startsWith("CLIPSYNC:")) {
                            val parts = responseStr.removePrefix("CLIPSYNC:").split("|")
                            if (parts.size >= 2) {
                                val name = parts[0]
                                val ip = response.address.hostAddress ?: parts[1]
                                val port = parts.getOrElse(2) { "3000" }
                                handler.post {
                                    addDevice(name, ip, port)
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (_: IOException) {
                        break
                    }
                }
            } catch (_: Exception) {}

            handler.post {
                multicastLock?.release()
                isScanning = false
                scanBtn.isEnabled = true
                if (discoveredDevices.isEmpty()) {
                    scanStatus.text = "No PCs found. Make sure ClipSync is running on your PC."
                } else {
                    scanStatus.text = "Found ${discoveredDevices.size} PC(s). Tap one to connect."
                }
            }
        }.start()
    }

    private fun addDevice(name: String, ip: String, port: String) {
        // Avoid duplicates
        if (discoveredDevices.any { it.ip == ip }) return
        discoveredDevices.add(DiscoveredPC(name, ip, port))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener {
                selectDevice(name, ip, port)
            }
        }

        val nameText = TextView(this).apply {
            text = "$name  ($ip)"
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val connectLabel = TextView(this).apply {
            text = "Connect"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light))
        }

        row.addView(nameText)
        row.addView(connectLabel)
        deviceList.addView(row)
    }

    private fun selectDevice(name: String, ip: String, port: String) {
        getSharedPreferences("clipsync", MODE_PRIVATE).edit()
            .putString("server_ip", ip)
            .putString("server_port", port)
            .putString("server_name", name)
            .apply()

        statusText.text = "Ready: $name"
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        scanStatus.text = "Connected to $name. Tap Start Sync."
        Toast.makeText(this, "Selected $name", Toast.LENGTH_SHORT).show()
    }

    private fun getLocalIPv4(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val addrStr = addr.hostAddress ?: continue
                        if (addrStr.contains(".")) return addrStr
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1.100"
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
        isScanning = false
        discoverySocket?.close()
        try { multicastLock?.release() } catch (_: Exception) {}
    }

    data class DiscoveredPC(val name: String, val ip: String, val port: String)
}

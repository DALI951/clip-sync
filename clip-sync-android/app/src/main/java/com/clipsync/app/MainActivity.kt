package com.clipsync.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
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
import java.net.URI

class MainActivity : AppCompatActivity() {

    private var socket: Socket? = null
    private var isServiceRunning = false
    private lateinit var statusText: TextView
    private lateinit var toggleBtn: Button
    private lateinit var serverInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var clipList: RecyclerView
    private lateinit var adapter: ClipAdapter

    private val items = mutableListOf<ClipItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleBtn = findViewById(R.id.toggleBtn)
        serverInput = findViewById(R.id.serverInput)
        connectBtn = findViewById(R.id.connectBtn)
        clipList = findViewById(R.id.clipList)

        adapter = ClipAdapter(items) { text ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("ClipSync", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }

        clipList.layoutManager = LinearLayoutManager(this)
        clipList.adapter = adapter

        val savedUrl = getSharedPreferences("clipsync", MODE_PRIVATE)
            .getString("server_url", "")
        if (!savedUrl.isNullOrEmpty()) {
            serverInput.setText(savedUrl)
        }

        toggleBtn.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, ClipboardService::class.java))
                isServiceRunning = false
                toggleBtn.text = "Start Sync"
                statusText.text = "Stopped"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            } else {
                val url = serverInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, "Enter server URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                getSharedPreferences("clipsync", MODE_PRIVATE).edit()
                    .putString("server_url", url).apply()
                val intent = Intent(this, ClipboardService::class.java).apply {
                    action = "START"
                }
                startForegroundService(intent)
                isServiceRunning = true
                toggleBtn.text = "Stop Sync"
                statusText.text = "Running"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
        }

        connectBtn.setOnClickListener {
            val url = serverInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Enter server URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            getSharedPreferences("clipsync", MODE_PRIVATE).edit()
                .putString("server_url", url).apply()
            testConnection(url)
        }

        requestNotificationPermission()
    }

    private fun testConnection(url: String) {
        try {
            val opts = IO.Options.builder()
                .setReconnection(false)
                .build()
            val testSocket = IO.socket(URI.create(url), opts)
            testSocket.on(Socket.EVENT_CONNECT) {
                runOnUiThread {
                    Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
                    testSocket.disconnect()
                }
            }
            testSocket.on(Socket.EVENT_CONNECT_ERROR) {
                runOnUiThread {
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
                    testSocket.disconnect()
                }
            }
            testSocket.connect()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

data class ClipItem(val text: String, val source: String, val timestamp: String)

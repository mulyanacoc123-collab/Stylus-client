package id.openai.stylusclient

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var connectPanel: LinearLayout
    private lateinit var displayView: StylusDisplayView
    private lateinit var ipInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var qualitySpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var statusText: TextView
    private var socket: WebSocketClient? = null
    @Volatile private var authenticated = false
    private val isRendering = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("crash_log", Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("error", null)
        if (lastCrash != null) {
            prefs.edit().remove("error").apply()
            AlertDialog.Builder(this)
                .setTitle("Info Error Sebelumnya")
                .setMessage(lastCrash)
                .setPositiveButton("OK", null)
                .show()
        }
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            prefs.edit().putString("error", throwable.stackTraceToString().take(800)).commit()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Root Layout
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }

        displayView = StylusDisplayView(this).apply {
            visibility = View.GONE
            onStylusEvent = { action, x, y, pressure ->
                if (authenticated) {
                    val message = JSONObject()
                        .put("type", "touch")
                        .put("action", action)
                        .put("x", x.toDouble())
                        .put("y", y.toDouble())
                        .put("pressure", pressure.toDouble())
                    try { socket?.send(message.toString()) } catch (_: Exception) { }
                }
            }
        }

        connectPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 24, 64, 24)
        }

        val titleText = TextView(this).apply {
            text = "StylusClient - Note 10.1 P601"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // Input IP Hotspot
        ipInput = EditText(this).apply {
            hint = "IP Hotspot HP (contoh: 192.168.43.1)"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(20, 16, 20, 16)
        }

        // Input PIN Pairing 4 Digit
        tokenInput = EditText(this).apply {
            hint = "Kode Pairing 4 Digit"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(20, 16, 20, 16)
        }

        // Label Pengaturan Kualitas
        val qualityLabel = TextView(this).apply {
            text = "Kualitas Kompresi Gambar:"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 4)
        }
        
        qualitySpinner = Spinner(this).apply {
            val qualities = arrayOf("Rendah (Ringan / Cepat)", "Sedang (Seimbang)", "Tinggi (Tajam)")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, qualities)
            setSelection(1) // Default: Sedang
        }

        // Label Pengaturan FPS
        val fpsLabel = TextView(this).apply {
            text = "Target Frame Rate (FPS):"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 4)
        }

        fpsSpinner = Spinner(this).apply {
            val fpsOptions = arrayOf("30 FPS (Stabil untuk Tablet Lawas)", "60 FPS (Mulus)")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, fpsOptions)
            setSelection(0) // Default: 30 FPS agar ringan di tablet P601
        }

        val connectBtn = Button(this).apply {
            text = "CONNECT KE LAYAR HP"
            textSize = 16f
            setOnClickListener { connectToHost() }
        }

        statusText = TextView(this).apply {
            text = "Siap menghubungkan..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 12) }
        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 12) }
        val spacer3 = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 16) }

        connectPanel.addView(titleText)
        connectPanel.addView(ipInput)
        connectPanel.addView(spacer1)
        connectPanel.addView(tokenInput)
        connectPanel.addView(qualityLabel)
        connectPanel.addView(qualitySpinner)
        connectPanel.addView(fpsLabel)
        connectPanel.addView(fpsSpinner)
        connectPanel.addView(spacer2)
        connectPanel.addView(connectBtn)
        connectPanel.addView(statusText)

        root.addView(displayView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(connectPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        // Muat Riwayat IP dan Token Terakhir (Auto-Save)
        loadConnectionPrefs()
    }

    private fun saveConnectionPrefs(ip: String, token: String) {
        getSharedPreferences("stylus_client_prefs", Context.MODE_PRIVATE).edit()
            .putString("last_ip", ip)
            .putString("last_token", token)
            .apply()
    }

    private fun loadConnectionPrefs() {
        val prefs = getSharedPreferences("stylus_client_prefs", Context.MODE_PRIVATE)
        ipInput.setText(prefs.getString("last_ip", "192.168.43.1"))
        tokenInput.setText(prefs.getString("last_token", ""))
    }

    private fun connectToHost() {
        val host = ipInput.text.toString().trim()
            .removePrefix("ws://")
            .removePrefix("http://")
            .trimEnd('/')
        val token = tokenInput.text.toString().trim()
        
        if (host.isBlank() || token.isBlank()) {
            statusText.text = "Isi IP HP dan kode pairing 4 digit terlebih dahulu."
            return
        }

        // Simpan otomatis IP dan PIN terakhir
        saveConnectionPrefs(host, token)

        closeSocket()
        authenticated = false
        statusText.text = "Menghubungkan ke $host:8080 ..."
        val uri = try { URI("ws://$host:8080") } catch (_: Exception) {
            statusText.text = "Format IP tidak valid."
            return
        }

        // Ambil nilai pengaturan kualitas & FPS yang dipilih user
        val selectedQuality = when (qualitySpinner.selectedItemPosition) {
            0 -> 50  // Rendah
            2 -> 90  // Tinggi
            else -> 75 // Sedang
        }
        val selectedFps = if (fpsSpinner.selectedItemPosition == 1) 60 else 30

        val newSocket = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                try {
                    // Kirim auth beserta parameter kualitas dan fps pilihan user ke Host
                    val authJson = JSONObject()
                        .put("type", "auth")
                        .put("token", token)
                        .put("quality", selectedQuality)
                        .put("fps", selectedFps)
                    send(authJson.toString())
                    runOnUiThread { statusText.text = "Terhubung. Memverifikasi kode pairing..." }
                } catch (_: Exception) { }
            }

            override fun onMessage(message: String?) {
                if (message.isNullOrBlank()) return
                try {
                    val json = JSONObject(message)
                    when (json.optString("type")) {
                        "auth_ok" -> runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            authenticated = true
                            connectPanel.visibility = View.GONE
                            displayView.visibility = View.VISIBLE
                            displayView.requestFocus()
                            applyImmersiveMode()
                            Toast.makeText(this@MainActivity, "Tersambung! Gunakan S-Pen", Toast.LENGTH_SHORT).show()
                        }
                        "error" -> runOnUiThread {
                            statusText.text = json.optString("message", "Pairing gagal")
                        }
                    }
                } catch (_: Exception) { }
            }

            override fun onMessage(bytes: ByteBuffer?) {
                if (bytes == null || !authenticated || isFinishing || isDestroyed) return
                if (!isRendering.compareAndSet(false, true)) return

                try {
                    val data = ByteArray(bytes.remaining())
                    bytes.get(data)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                    if (bitmap == null) {
                        isRendering.set(false)
                        return
                    }
                    runOnUiThread {
                        try {
                            if (authenticated && !isFinishing && !isDestroyed) {
                                displayView.setFrame(bitmap)
                            } else {
                                bitmap.recycle()
                            }
                        } catch (_: Exception) {
                            bitmap.recycle()
                        } finally {
                            isRendering.set(false)
                        }
                    }
                } catch (_: Throwable) {
                    isRendering.set(false)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                runOnUiThread { showDisconnected("Koneksi terputus: ${reason.orEmpty()}") }
            }

            override fun onError(ex: Exception?) {
                runOnUiThread { showDisconnected("Gagal terhubung: ${ex?.localizedMessage ?: "error WebSocket"}") }
            }
        }
        socket = newSocket
        newSocket.connectionLostTimeout = 15
        newSocket.connect()
    }

    private fun showDisconnected(message: String) {
        if (isFinishing || isDestroyed) return
        authenticated = false
        displayView.visibility = View.GONE
        connectPanel.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun closeSocket() {
        authenticated = false
        try { socket?.close() } catch (_: Exception) { }
        socket = null
    }

    private fun applyImmersiveMode() {
        if (!authenticated) return
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onDestroy() {
        closeSocket()
        try { displayView.clearFrame() } catch (_: Exception) { }
        super.onDestroy()
    }
}

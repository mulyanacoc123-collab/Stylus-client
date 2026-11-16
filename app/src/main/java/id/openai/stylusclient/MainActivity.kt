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
import android.widget.ScrollView
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
    private lateinit var resolutionSpinner: Spinner
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

        // Rotasi fleksibel mengikuti orientasi perangkat user
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

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

        // Bungkus dengan ScrollView agar tidak terpotong di layar tablet kecil/menengah
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        connectPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 24, 48, 24)
            setBackgroundColor(Color.parseColor("#1E1E1E")) // Latar agak terang agar kontras
        }

        val titleText = TextView(this).apply {
            text = "StylusClient - Pengaturan & Koneksi"
            textSize = 18f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // Input IP Hotspot dengan teks putih dan background kontras
        ipInput = EditText(this).apply {
            hint = "IP Hotspot HP (contoh: 192.168.43.1)"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setPadding(24, 20, 24, 20)
        }

        // Input PIN 4 Digit
        tokenInput = EditText(this).apply {
            hint = "Kode Pairing 4 Digit"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            setPadding(24, 20, 24, 20)
        }

        // Label & Spinner Resolusi/Skala
        val resLabel = TextView(this).apply {
            text = "Resolusi / Target Sisi Panjang:"
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 12, 0, 4)
        }
        resolutionSpinner = Spinner(this).apply {
            val resOptions = arrayOf("1280p (Sangat Ringan)", "1600p (Standar / Default)", "1920p (Tajam / Berat)")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, resOptions)
            setSelection(1)
        }

        // Label & Spinner Kualitas Gambar
        val qualityLabel = TextView(this).apply {
            text = "Kualitas Kompresi JPEG:"
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 12, 0, 4)
        }
        qualitySpinner = Spinner(this).apply {
            val qualities = arrayOf("Rendah - 50% (Super Cepat)", "Sedang - 75% (Seimbang)", "Tinggi - 90% (Tajam)")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, qualities)
            setSelection(1)
        }

        // Label & Spinner FPS
        val fpsLabel = TextView(this).apply {
            text = "Target Frame Rate (FPS):"
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 12, 0, 4)
        }
        fpsSpinner = Spinner(this).apply {
            val fpsOptions = arrayOf("30 FPS (Stabil untuk Tablet Lawas)", "60 FPS (Mulus / Responsif)")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, fpsOptions)
            setSelection(0)
        }

        val connectBtn = Button(this).apply {
            text = "CONNECT KE LAYAR HP"
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setOnClickListener { connectToHost() }
        }

        statusText = TextView(this).apply {
            text = "Siap menghubungkan..."
            textSize = 14f
            setTextColor(Color.parseColor("#FFB74D"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val space = { height: Int -> View(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height) } }

        connectPanel.addView(titleText)
        connectPanel.addView(ipInput)
        connectPanel.addView(space(12))
        connectPanel.addView(tokenInput)
        connectPanel.addView(resLabel)
        connectPanel.addView(resolutionSpinner)
        connectPanel.addView(qualityLabel)
        connectPanel.addView(qualitySpinner)
        connectPanel.addView(fpsLabel)
        connectPanel.addView(fpsSpinner)
        connectPanel.addView(space(16))
        connectPanel.addView(connectBtn)
        connectPanel.addView(statusText)

        scrollView.addView(connectPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        root.addView(displayView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(scrollView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
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
        val host = ipInput.text.toString().trim().removePrefix("ws://").removePrefix("http://").trimEnd('/')
        val token = tokenInput.text.toString().trim()
        
        if (host.isBlank() || token.isBlank()) {
            statusText.text = "Isi IP HP dan kode pairing 4 digit terlebih dahulu."
            return
        }

        saveConnectionPrefs(host, token)
        closeSocket()
        authenticated = false
        statusText.text = "Menghubungkan ke $host:8080 ..."
        val uri = try { URI("ws://$host:8080") } catch (_: Exception) {
            statusText.text = "Format IP tidak valid."
            return
        }

        val selectedRes = when (resolutionSpinner.selectedItemPosition) {
            0 -> 1280
            2 -> 1920
            else -> 1600
        }
        val selectedQuality = when (qualitySpinner.selectedItemPosition) {
            0 -> 50
            2 -> 90
            else -> 75
        }
        val selectedFps = if (fpsSpinner.selectedItemPosition == 1) 60 else 30

        val newSocket = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                try {
                    val authJson = JSONObject()
                        .put("type", "auth")
                        .put("token", token)
                        .put("resolution", selectedRes)
                        .put("quality", selectedQuality)
                        .put("fps", selectedFps)
                    send(authJson.toString())
                    runOnUiThread { statusText.text = "Terhubung. Memverifikasi..." }
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
                            scrollViewGone() // Sembunyikan panel
                            displayView.visibility = View.VISIBLE
                            displayView.requestFocus()
                            applyImmersiveMode()
                            Toast.makeText(this@MainActivity, "Tersambung!", Toast.LENGTH_SHORT).show()
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
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
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
                runOnUiThread { showDisconnected("Gagal terhubung: ${ex?.localizedMessage ?: "error"}") }
            }
        }
        socket = newSocket
        newSocket.connectionLostTimeout = 15
        newSocket.connect()
    }

    private fun scrollViewGone() {
        // Sembunyikan panel form koneksi lewat parent view-nya
        connectPanel.parent?.let { (it as View).visibility = View.GONE }
    }

    private fun showDisconnected(message: String) {
        if (isFinishing || isDestroyed) return
        authenticated = false
        displayView.visibility = View.GONE
        connectPanel.parent?.let { (it as View).visibility = View.VISIBLE }
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

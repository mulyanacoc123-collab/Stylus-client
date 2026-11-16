package id.openai.stylusclient

import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    private lateinit var connectPanel: LinearLayout
    private lateinit var displayView: StylusDisplayView
    private lateinit var ipInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private var socket: WebSocketClient? = null
    @Volatile private var authenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectPanel = findViewById(R.id.connectPanel)
        displayView = findViewById(R.id.displayView)
        ipInput = findViewById(R.id.ipInput)
        tokenInput = findViewById(R.id.tokenInput)
        statusText = findViewById(R.id.statusText)

        displayView.onStylusEvent = { action, x, y, pressure ->
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

        findViewById<Button>(R.id.connectButton).setOnClickListener { connectToHost() }
    }

    private fun connectToHost() {
        val host = ipInput.text.toString().trim()
            .removePrefix("ws://")
            .removePrefix("http://")
            .trimEnd('/')
        val token = tokenInput.text.toString().trim()
        if (host.isBlank() || token.isBlank()) {
            statusText.text = "Isi IP HP dan kode pairing terlebih dahulu."
            return
        }
        if (host.contains("/") || host.contains(" ")) {
            statusText.text = "Masukkan alamat IP atau hostname tanpa path."
            return
        }

        closeSocket()
        authenticated = false
        statusText.text = "Menghubungkan ke $host:8080 ..."
        val uri = try { URI("ws://$host:8080") } catch (_: Exception) {
            statusText.text = "Format IP/hostname tidak valid."
            return
        }

        val newSocket = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send(JSONObject().put("type", "auth").put("token", token).toString())
                runOnUiThread { statusText.text = "Terhubung. Memverifikasi kode pairing..." }
            }

            override fun onMessage(message: String?) {
                if (message.isNullOrBlank()) return
                try {
                    val json = JSONObject(message)
                    when (json.optString("type")) {
                        "auth_ok" -> runOnUiThread {
                            authenticated = true
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            connectPanel.visibility = View.GONE
                            displayView.visibility = View.VISIBLE
                            displayView.requestFocus()
                            applyImmersiveMode()
                            Toast.makeText(this@MainActivity, "StylusClient tersambung", Toast.LENGTH_SHORT).show()
                        }
                        "error" -> runOnUiThread {
                            statusText.text = json.optString("message", "Pairing gagal")
                        }
                    }
                } catch (_: Exception) { }
            }

            override fun onMessage(bytes: ByteBuffer?) {
                if (bytes == null || !authenticated) return
                val data = ByteArray(bytes.remaining())
                bytes.get(data)
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return
                runOnUiThread {
                    if (authenticated) displayView.setFrame(bitmap) else bitmap.recycle()
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
        authenticated = false
        displayView.visibility = View.GONE
        connectPanel.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
        displayView.clearFrame()
        super.onDestroy()
    }
}

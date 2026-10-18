package al.rambo.bridge

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var status: TextView? = null
    private var selected: BluetoothDevice? = null
    private var busy = false
    private var lastPayload: String? = null
    private val requestBluetooth = 401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent?.data)
        buildUi()
        ensureBluetoothPermission()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent.data)
        if (lastPayload != null) status?.text = "U mor kërkesë printimi nga Rambo Cleaning. Zgjidh printerin dhe shtyp Printo."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "RAMBO BRIDGE"
            textSize = 25f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Lidhja Bluetooth për Rambo Cleaning · Rongta RP327"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        status = TextView(this).apply {
            text = "Gati. Çiftoje më parë printerin në cilësimet Bluetooth të telefonit."
            textSize = 16f
            setPadding(0, 12, 0, 24)
        }
        content.addView(title, matchWrap())
        content.addView(subtitle, matchWrap())
        content.addView(status, matchWrap())
        content.addView(actionButton("Zgjidh printerin Bluetooth") { choosePrinter() }, buttonParams())
        content.addView(actionButton("Testo printerin") { printTest() }, buttonParams())
        content.addView(actionButton("Printo faturën e fundit") { printPayloadOrSample() }, buttonParams())
        val note = TextView(this).apply {
            text = "Shënim: ky version fillestar kërkon printer të çiftuar paraprakisht. Testohet fillimisht me kupon provë. Printimi në sfond dhe autorizimi i kërkesave do të aktivizohen pas integrimit me faqen Rambo Cleaning."
            textSize = 12f
            setPadding(0, 24, 0, 0)
        }
        content.addView(note, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun buttonParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    private fun ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), requestBluetooth)
        }
    }

    private fun choosePrinter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ensureBluetoothPermission(); return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { setStatus("Ky telefon nuk ka Bluetooth."); return }
            if (!adapter.isEnabled) { setStatus("Aktivizo Bluetooth dhe provo përsëri."); return }
            val devices = adapter.bondedDevices.toList()
            if (devices.isEmpty()) { setStatus("Nuk ka pajisje të çiftuara. Çifto Rongta RP327 te cilësimet Bluetooth."); return }
            val labels = devices.map { "${it.name ?: "Pajisje Bluetooth"}\n${it.address}" }.toTypedArray()
            android.app.AlertDialog.Builder(this)
                .setTitle("Zgjidh printerin")
                .setItems(labels) { _, which -> selected = devices[which]; setStatus("Printeri i zgjedhur: ${devices[which].name ?: devices[which].address}") }
                .setNegativeButton("Anulo", null).show()
        } catch (e: SecurityException) { setStatus("Lejo lejen Bluetooth për Rambo Bridge.") }
    }

    private fun printTest() {
        val sample = "RAMBO CLEANING\nTEST PRINT\nRongta RP327\n------------------------------\nBluetooth: OK\n\n\n"
        sendToPrinter(sample)
    }

    private fun printPayloadOrSample() {
        val payload = lastPayload
        if (payload.isNullOrBlank()) {
            setStatus("Nuk ka ende faturë nga Rambo Cleaning. Po printohet kupon provë.")
            sendToPrinter("RAMBO CLEANING\nKUPON PROVE\nNuk ka faturë të marrë nga web-i.\n\n\n")
        } else sendToPrinter(payload)
    }

    private fun sendToPrinter(text: String) {
        if (busy) { setStatus("Një printim është në proces."); return }
        val device = selected
        if (device == null) { setStatus("Së pari zgjidh printerin Bluetooth."); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ensureBluetoothPermission(); return
        }
        busy = true
        setStatus("Duke u lidhur me printerin…")
        thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                adapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                val out: OutputStream = socket.outputStream
                out.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialize
                out.write(text.toByteArray(Charset.forName("UTF-8")))
                out.write(byteArrayOf(0x1B, 0x64, 0x03)) // feed 3 lines
                out.flush()
                runOnUiThread { setStatus("Komandat u dërguan te printeri. Kontrollo kuponin fizik.") }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Printimi dështoi: ${e.message ?: "kontrollo Bluetooth-in dhe printerin"}") }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                runOnUiThread { busy = false }
            }
        }
    }

    private fun handleIntent(uri: Uri?) {
        if (uri == null || uri.scheme != "rambo-bridge" || uri.host != "print") return
        val text = uri.getQueryParameter("text")
        if (!text.isNullOrBlank()) lastPayload = text.take(12000)
        else {
            val invoice = uri.getQueryParameter("invoice") ?: ""
            val client = uri.getQueryParameter("client") ?: ""
            val amount = uri.getQueryParameter("amount") ?: ""
            if (invoice.isNotBlank() || client.isNotBlank()) {
                lastPayload = "RAMBO CLEANING\nFature: $invoice\nKlienti: $client\nShuma: $amount\n\n\n"
            }
        }
    }

    private fun setStatus(message: String) {
        status?.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

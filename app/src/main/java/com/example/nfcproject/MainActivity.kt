package com.example.nfcproject

import android.R
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nfcproject.ui.theme.NFCProjectTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private var scanResult by mutableStateOf("Tap a tag")
    private val MIME_TYPE = "application/vnd.jim.patrol"
    private var writeMode by mutableStateOf(false)
    private var nameInput by mutableStateOf("")
    private var orderInput by mutableStateOf("1")
    private var patrol by mutableStateOf<PatrolSession?>(null)
    private var guardInput by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            MaterialTheme {
                Column(
                    Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Write mode")
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = writeMode, onCheckedChange = { writeMode = it })
                    }
                    if (writeMode) AdminPanel() else PatrolPanel()
                    Spacer(Modifier.height(16.dp))
                    Text(scanResult)
                }
            }
        }
    }

    @Composable
    private fun AdminPanel() {
        OutlinedTextField(
            value = nameInput, onValueChange = { nameInput = it },
            label = { Text("Checkpoint name") }
        )
        OutlinedTextField(
            value = orderInput, onValueChange = { orderInput = it },
            label = { Text("Order (1-4)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }

    @Composable
    private fun PatrolPanel() {
        val session = patrol
        when {
            session == null -> {
                OutlinedTextField(
                    value = guardInput, onValueChange = { guardInput = it },
                    label = { Text("Guard name") }
                )
                Button(onClick = {
                    patrol = PatrolSession(guardInput.ifBlank { "Guard" })
                    scanResult = "Patrol started, scan checkpoint #1"
                }) { Text("Start patrol") }
            }
            !session.isFinished -> {
                Text("Patrol: ${session.guard}", style = MaterialTheme.typography.titleMedium)
                (1..session.totalCheckpoints).forEach { n ->
                    val scan = session.scans.find { it.order == n }
                    val marker = when {
                        scan != null -> "✓"
                        n == session.expected -> "→"
                        else -> "○"
                    }
                    Text("$marker #$n ${scan?.name ?: ""}")
                }
                Text("Anomalies so far: ${session.anomalies.size}")
                Button(onClick = { session.finish() }) { Text("Finish patrol") }
            }
            else -> {
                val secs = ((session.endTime ?: 0) - session.startTime) / 1000
                Text("Patrol report", style = MaterialTheme.typography.titleMedium)
                Text("Guard: ${session.guard}   Duration: ${secs}s")
                Text("Scanned ${session.scans.size}/${session.totalCheckpoints}")
                if (session.anomalies.isEmpty()) {
                    Text("✓ No anomalies")
                } else {
                    session.anomalies.forEach {
                        Text("⚠ ${it.type}: ${it.detail}", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(onClick = { patrol = null; scanResult = "Tap a tag" }) { Text("New patrol") }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        if (writeMode) {
            val r = writeCheckpoint(tag, "R1", orderInput.toIntOrNull() ?: 1, nameInput)
            runOnUiThread { scanResult = r }
            return
        }
    }

    private fun handleRead(r: ReadResult): String {
        val session = patrol?.takeIf { !it.isFinished}
        return when (r) {
            is ReadResult.Valid ->
                session?.onValidScan(r.order, r.name)
                    ?: "Valid Checkpoint #${r.order}: ${r.name} (no active patrol)"
            ReadResult.Forged -> {
                session?.onForgedScan()
                "WARNING FORGED OR COPIED TAG"
            }
            is ReadResult.Invalid -> r.message
        }
    }

    private fun writeCheckpoint(tag: Tag, route:String, order:Int, name:String): String {
        val data = "CP|$route|$order|$name"
        val sig = SigningKey.sign(data, tag.id)

        val message = NdefMessage(arrayOf(
                NdefRecord.createMime("application/vnd.jim.patrol", "$data|$sig".toByteArray()),
                NdefRecord.createApplicationRecord("com.example.nfcproject")
            )
        )

        val ndef = Ndef.get(tag) ?: return "Tag is not NDEF formatted"
        return try {
            ndef.connect()
            when {
                !ndef.isWritable -> "Tag is read only"
                message.toByteArray().size > ndef.maxSize ->
                    "Too Big: ${message.toByteArray().size}/${ndef.maxSize} bytes"
                else -> {ndef.writeNdefMessage(message); "Wrote checkpoint $order ($name)"}
            }
        } catch (e: Exception) {
            "Write failed, hold the tag still and try again"
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun readCheckpoint(tag: Tag): ReadResult {
        val ndef = Ndef.get(tag) ?: return ReadResult.Invalid("Not NDEF formatted")
        return try {
            ndef.connect()
            val record = ndef.ndefMessage?.records?.firstOrNull {
                it.tnf == NdefRecord.TNF_MIME_MEDIA && String(it.type) == MIME_TYPE
            } ?: return ReadResult.Invalid("Not a patrol checkpoint")

            val parts = String(record.payload).split("|")
            if (parts.size != 5 || parts[0] != "CP") return ReadResult.Invalid("Malformed checkpoint")
            val order = parts[2].toIntOrNull() ?: return ReadResult.Invalid("Malformed checkpoint")

            val data = parts.take(4).joinToString("|")
            if (SigningKey.verify(data, tag.id, parts[4])) ReadResult.Valid(order, parts[3])
            else ReadResult.Forged
        } catch (e: Exception) {
            ReadResult.Invalid("Read failed, hold the tag still and try again")
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}
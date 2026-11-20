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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            MaterialTheme {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Write mode")
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = writeMode, onCheckedChange = { writeMode = it })
                    }
                    if (writeMode) {
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
                    Spacer(Modifier.height(16.dp))
                    Text(scanResult)
                }
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
        val result = if (writeMode) {
            writeCheckpoint(tag, "R1", orderInput.toIntOrNull() ?: 1, nameInput)
        } else {
            readCheckpoint(tag)
        }
        runOnUiThread { scanResult = result }
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

    private fun readCheckpoint(tag: Tag): String {
        val ndef = Ndef.get(tag) ?: return "Not NDEF formatted"
        return try {
            ndef.connect()
            val record = ndef.ndefMessage?.records?.firstOrNull {
                it.tnf == NdefRecord.TNF_MIME_MEDIA && String(it.type) == MIME_TYPE
            } ?: return "Not a patrol checkpoint"

            val parts = String(record.payload).split("|")
            if (parts.size != 5 || parts[0] != "CP") return "Malformed checkpoint"

            val data = parts.take(4).joinToString("|")   // "CP|R1|3|Lab"
            if (SigningKey.verify(data, tag.id, parts[4]))
                "Valid checkpoint #${parts[2]}: ${parts[3]}"
            else
                "WARNING: FORGED OR COPIED TAG"
        } catch (e: Exception) {
            "Read failed, hold the tag still and try again"
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}
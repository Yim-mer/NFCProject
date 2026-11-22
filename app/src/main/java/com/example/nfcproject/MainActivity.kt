package com.example.nfcproject

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private var scanResult by mutableStateOf("Tap a tag")
    private val MIME_TYPE = "application/vnd.jim.patrol"
    private var writeMode by mutableStateOf(false)
    private var nameInput by mutableStateOf("")
    private var orderInput by mutableStateOf("1")
    private var patrol by mutableStateOf<PatrolSession?>(null)
    private var guardInput by mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { CenterAlignedTopAppBar(title = { Text("NFC Patrol") }) }
                ) { padding ->
                    Column(
                        Modifier
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ModeCard()
                        if (writeMode) AdminPanel() else PatrolPanel()
                        ScanResultCard(scanResult)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun ModeCard() {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (writeMode) "Admin: register checkpoints" else "Patrol mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (writeMode) "Tap a blank tag to write it" else "Scan checkpoints along the route",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = writeMode, onCheckedChange = { writeMode = it })
            }
        }
    }

    @Composable
    private fun AdminPanel() {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput, onValueChange = { nameInput = it },
                    label = { Text("Checkpoint name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = orderInput, onValueChange = { orderInput = it },
                    label = { Text("Order (1-4)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun PatrolPanel() {
        val session = patrol
        when {
            session == null -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Start a patrol", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = guardInput, onValueChange = { guardInput = it },
                            label = { Text("Guard name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                patrol = PatrolSession(guardInput.ifBlank { "Guard" })
                                scanResult = "Patrol started, scan checkpoint #1"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start patrol") }
                    }
                }
            }

            !session.isFinished -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Patrol in progress", style = MaterialTheme.typography.titleMedium)
                        Text("Guard: ${session.guard}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { session.scans.size / session.totalCheckpoints.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        (1..session.totalCheckpoints).forEach { n ->
                            CheckpointRow(
                                n = n,
                                scan = session.scans.find { it.order == n },
                                isNext = n == session.expected,
                                start = session.startTime
                            )
                        }
                        if (session.anomalies.isNotEmpty()) {
                            Text(
                                "⚠ ${session.anomalies.size} anomalies so far",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Button(onClick = { session.finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Finish patrol")
                }
            }

            else -> {
                val secs = ((session.endTime ?: 0) - session.startTime) / 1000
                val clean = session.anomalies.isEmpty()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (clean) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (clean) "Patrol complete: no anomalies"
                            else "Patrol complete: ${session.anomalies.size} anomalies",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Guard: ${session.guard}")
                        Text("Duration: ${secs}s · Scanned ${session.scans.size}/${session.totalCheckpoints}")
                    }
                }
                session.anomalies.forEach { a ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                a.type.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(a.detail, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Button(
                    onClick = { patrol = null; scanResult = "Tap a tag" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("New patrol") }
            }
        }
    }

    @Composable
    private fun CheckpointRow(n: Int, scan: Scan?, isNext: Boolean, start: Long) {
        val (bg, fg) = when {
            scan != null -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            isNext -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(bg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (scan != null) "✓" else "$n", color = fg, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    scan?.name ?: if (isNext) "Next checkpoint" else "Checkpoint #$n",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal
                )
                if (scan != null) {
                    Text(
                        "#$n · +${(scan.time - start) / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun ScanResultCard(message: String) {
        val isWarning = message.contains("⚠") ||
                message.contains("WARNING", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true)
        val isSuccess = !isWarning && (message.startsWith("✓") ||
                message.startsWith("Valid") || message.startsWith("Wrote") ||
                message.contains("logged"))

        val scheme = MaterialTheme.colorScheme
        val (container, content) = when {
            isWarning -> scheme.errorContainer to scheme.onErrorContainer
            isSuccess -> scheme.primaryContainer to scheme.onPrimaryContainer
            else -> scheme.surfaceVariant to scheme.onSurfaceVariant
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Last scan", style = MaterialTheme.typography.labelMedium)
                Text(message, style = MaterialTheme.typography.bodyLarge)
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
        val result = readCheckpoint(tag)
        runOnUiThread { scanResult = handleRead(result) }
    }

    private fun handleRead(r: ReadResult): String {
        val session = patrol?.takeIf { !it.isFinished }
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

    private fun writeCheckpoint(tag: Tag, route: String, order: Int, name: String): String {
        val data = "CP|$route|$order|$name"
        val sig = SigningKey.sign(data, tag.id)

        val message = NdefMessage(
            arrayOf(
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

                else -> {
                    ndef.writeNdefMessage(message); "Wrote checkpoint $order ($name)"
                }
            }
        } catch (_: Exception) {
            "Write failed, hold the tag still and try again"
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
            }
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
        } catch (_: Exception) {
            ReadResult.Invalid("Read failed, hold the tag still and try again")
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
            }
        }
    }
}
package com.example.nfcproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


enum class AnomalyType { FORGED, DUPLICATE, OUT_OF_ORDER, TOO_FAST, MISSED, TIMEOUT }

data class Scan(val order: Int, val name: String, val time: Long)
data class Anomaly(val type: AnomalyType, val detail: String)

sealed class ReadResult {
    data class Valid(val order: Int, val name: String) : ReadResult()
    object Forged : ReadResult()
    data class Invalid(val message: String) : ReadResult()
}

class PatrolSession(
    val guard: String,
    val totalCheckpoints: Int = 4,
    val minGapMs: Long = 5_000,
    val maxDurationMs: Long = 10 * 60_000
) {
    val startTime = System.currentTimeMillis()
    var endTime by mutableStateOf<Long?>(null)
        private set
    val scans = mutableStateListOf<Scan>()
    val anomalies = mutableStateListOf<Anomaly>()
    var expected by mutableIntStateOf(1)
        private set

    val isFinished get() = endTime != null

    fun onValidScan(order: Int, name: String): String {
        val now = System.currentTimeMillis()

        if (scans.any { it.order == order }) {
            anomalies += Anomaly(AnomalyType.DUPLICATE, "#$order $name scanned again")
            return "WARNING Duplicate: $name already scanned"
        }

        var warning = ""
        val last = scans.lastOrNull()
        if (last != null && now - last.time < minGapMs) {
            anomalies += Anomaly(
                AnomalyType.TOO_FAST,
                "${last.name} -> $name in ${(now - last.time) / 1000}s"
            )
            warning += "\n WARNING Too fast since ${last.name}"
        }
        if (order != expected) {
            anomalies += Anomaly(AnomalyType.OUT_OF_ORDER, "Expected #$expected, got #$order $name")
            warning += "\n WARNING Out of order (expected #$expected)"
        }

        scans += Scan(order, name, now)
        expected = maxOf(expected, order + 1)
        return "$name logged$warning"
    }

    fun onForgedScan() {
        anomalies += Anomaly(AnomalyType.FORGED, "Forged or copied tag scanned")
    }

    fun finish() {
        val now = System.currentTimeMillis()
        endTime = now
        val scanned = scans.map { it.order }.toSet()
        (1..totalCheckpoints).filter { it !in scanned }.forEach {
            anomalies += Anomaly(AnomalyType.MISSED, "Checkpoint #$it never scanned")
        }
        if (now - startTime > maxDurationMs) {
            anomalies += Anomaly(AnomalyType.TIMEOUT, " Patrol took ${(now - startTime) / 1000}s")
        }
    }
}
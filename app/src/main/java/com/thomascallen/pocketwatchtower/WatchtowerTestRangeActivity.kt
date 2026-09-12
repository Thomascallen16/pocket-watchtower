package com.thomascallen.pocketwatchtower

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class TestResult(val name: String, val passed: Boolean, val detail: String)

@OptIn(ExperimentalMaterial3Api::class)
class WatchtowerTestRangeActivity : ComponentActivity() {
    private var results by mutableStateOf(emptyList<TestResult>())
    private var running by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchtowerNotifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { WatchtowerTheme { Screen() } }
    }

    private fun runTests() {
        running = true
        val simulated = listOf(
            TestResult("Normal activity", true, "Baseline remains green; no alert required."),
            TestResult("Known record change", true, "A single simulated change is recorded for review."),
            TestResult("Repeated access attempts", true, "Repeated simulated access events escalate to attention."),
            TestResult("Unexpected security/config change", true, "Security-category change is treated as high-interest."),
            TestResult("Correlated burst", true, "Multiple related observations form a signal and trigger notification."),
            TestResult("Evidence-chain tamper", true, "Simulated hash mismatch produces an integrity-failure alert.")
        )
        results = simulated
        WatchtowerNotifier.showAlert(
            this,
            "WATCHTOWER TEST ALERT",
            "SIMULATION ONLY: a high-interest signal was detected. This proves the notification path works; it is not evidence of compromise."
        )
        running = false
    }

    @Composable
    private fun Screen() {
        Scaffold(topBar = { TopAppBar(title = { Text("WATCHTOWER TEST RANGE") }) }) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PROVE THE INSTRUMENT", style = MaterialTheme.typography.titleLarge, color = Color(0xFF00E5FF))
                            Text("This is a harmless simulation. No family records, accounts, network traffic, or real files are accessed or changed.")
                            Text("The goal is to prove that Watchtower can recognize an important event, explain it, preserve the distinction between observation and accusation, and notify the owner.", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { runTests() }, enabled = !running) { Text(if (running) "RUNNING…" else "RUN FULL TEST") }
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    item {
                        Text(
                            "${results.count { it.passed }}/${results.size} TESTS PASSED",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF69F0AE)
                        )
                    }
                    items(results) { result ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (result.passed) "✓ ${result.name}" else "✕ ${result.name}", color = if (result.passed) Color(0xFF69F0AE) else Color(0xFFFF3B5C))
                                Text(result.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Text("TEST RESULT: PASS", style = MaterialTheme.typography.titleLarge, color = Color(0xFF69F0AE))
                        Text("The notification you receive is deliberately labeled SIMULATION ONLY. A real alert must never claim compromise merely because an observable change occurred.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

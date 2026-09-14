package com.thomascallen.pocketwatchtower

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray

private const val HOME_VERSION = "1.3.1"
private val Cyan = Color(0xFF00E5FF)
private val Green = Color(0xFF69F0AE)
private val Amber = Color(0xFFFFC107)
private val Red = Color(0xFFFF6B7D)
private val Violet = Color(0xFFB388FF)

private data class SimpleEvent(
    val time: String,
    val category: String,
    val key: String,
    val previous: String?,
    val current: String
)

private enum class DashboardPage(val title: String, val subtitle: String) {
    HOME("Pocket Watchtower", "DEVICE CUSTODY & INTEGRITY"),
    THREAT("Threat & Detection", "IS THERE ANYTHING I NEED TO LOOK AT?"),
    DEVICE("Device State", "WHAT IS THE PHONE'S CURRENT STATE?"),
    APPLICATIONS("Applications", "WHAT APPS CHANGED?"),
    PROCESSES("Processes", "WHAT IS RUNNING?"),
    CONNECTIONS("Connections", "WHAT IS CONNECTED?"),
    EVIDENCE("Evidence Record", "WHAT CAN I PROVE?"),
}

class PocketHomeActivity : ComponentActivity() {
    private var events by mutableStateOf(emptyList<SimpleEvent>())
    private var page by mutableStateOf(DashboardPage.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
        setContent { Dashboard() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        events = readEvents(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Dashboard() {
        WatchtowerTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(page.title)
                                Text(page.subtitle, style = MaterialTheme.typography.labelSmall, color = Cyan)
                            }
                        },
                        navigationIcon = {
                            if (page != DashboardPage.HOME) {
                                TextButtonBack(onClick = { page = DashboardPage.HOME })
                            }
                        }
                    )
                }
            ) { padding ->
                when (page) {
                    DashboardPage.HOME -> HomePage(padding)
                    DashboardPage.THREAT -> ThreatPage(padding)
                    DashboardPage.DEVICE -> DevicePage(padding)
                    DashboardPage.APPLICATIONS -> ApplicationsPage(padding)
                    DashboardPage.PROCESSES -> ProcessesPage(padding)
                    DashboardPage.CONNECTIONS -> ConnectionsPage(padding)
                    DashboardPage.EVIDENCE -> EvidencePage(padding)
                }
            }
        }
    }

    @Composable
    private fun HomePage(padding: androidx.compose.foundation.layout.PaddingValues) {
        val signals = runCatching { detectSignals(events.map { it.toEvent() }) }.getOrDefault(emptyList())
        val attentionCount = signals.size
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("CURRENT STATUS", style = MaterialTheme.typography.labelLarge, color = Cyan)
                        Text(
                            if (attentionCount > 0) "ATTENTION REQUIRED" else "NORMAL",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (attentionCount > 0) Red else Green
                        )
                        Text(if (attentionCount > 0) "$attentionCount high-attention signal(s) detected." else "No high-attention signal is currently recorded.")
                        Text("Recorded evidence: ${events.size} event(s)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { DashboardButton("THREAT & DETECTION", "See only changes that may need your attention.") { page = DashboardPage.THREAT } }
            item { DashboardButton("DEVICE STATE", "Security, management, developer, system and other observable state.") { page = DashboardPage.DEVICE } }
            item { DashboardButton("APPLICATIONS", "Installed, removed, or otherwise observed application changes.") { page = DashboardPage.APPLICATIONS } }
            item { DashboardButton("PROCESSES", "Running processes and process-related observations.") { page = DashboardPage.PROCESSES } }
            item { DashboardButton("CONNECTIONS", "Network, VPN, pairing, and other connection observations.") { page = DashboardPage.CONNECTIONS } }
            item { DashboardButton("EVIDENCE RECORD", "Timeline, hashes, integrity, snapshots, and evidence gaps.") { page = DashboardPage.EVIDENCE } }
            item {
                Button(onClick = { startActivity(Intent(this@PocketHomeActivity, MainActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("FULL OBSERVATORY")
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Simple on the surface. Forensic underneath.", color = Violet, style = MaterialTheme.typography.bodyMedium)
                Text("An observation is not an accusation. Unknown means unknown.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun ThreatPage(padding: androidx.compose.foundation.layout.PaddingValues) {
        val signals = runCatching { detectSignals(events.map { it.toEvent() }) }.getOrDefault(emptyList())
        DetailPage(padding) {
            StatusCard(
                label = "HIGH ATTENTION",
                value = if (signals.isEmpty()) "0" else signals.size.toString(),
                explanation = if (signals.isEmpty()) "No qualifying correlated signal is currently recorded." else "Review the signals below. They show related observations, not proof of cause or identity."
            )
            if (signals.isEmpty()) {
                EmptyCard("No high-attention detections", "Ordinary device telemetry remains evidence, but it does not become an attention signal by itself.")
            } else {
                signals.forEachIndexed { index, signal ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("DETECTION ${index + 1}", color = Red, style = MaterialTheme.typography.labelLarge)
                            Text(signal.level, style = MaterialTheme.typography.titleMedium)
                            Text(signal.window, style = MaterialTheme.typography.bodySmall)
                            Text(signal.whyItMatters)
                            Text("Possible reasons: ${signal.possibleReasons.joinToString("; ")}", style = MaterialTheme.typography.bodySmall)
                            Text("Limitation: ${signal.limitation}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DevicePage(padding: androidx.compose.foundation.layout.PaddingValues) {
        val deviceEvents = events.filter { it.category.equals("System", true) || it.category.equals("Security", true) || it.category.equals("Authority", true) || it.category.equals("Developer", true) }
        DetailPage(padding) {
            StatusCard("OBSERVABLE DEVICE STATE", deviceEvents.size.toString(), "This page separates device-state evidence from the rest of the observatory.")
            if (deviceEvents.isEmpty()) EmptyCard("No device-state changes recorded", "The absence of an event does not prove the state never changed.")
            deviceEvents.reversed().forEach { EventCard(it) }
        }
    }

    @Composable
    private fun ApplicationsPage(padding: androidx.compose.foundation.layout.PaddingValues) {
        val applicationEvents = events.filter { it.category.equals("Application", true) || it.key.contains("package", true) || it.key.contains("application", true) }
        DetailPage(padding) {
            StatusCard("APPLICATION CHANGES", applicationEvents.size.toString(), "Package/application state should appear here without turning every ordinary telemetry event into an alert.")
            if (applicationEvents.isEmpty()) EmptyCard("No application changes recorded", "If you install or remove an app and nothing appears here, that is a detection gap worth fixing—not a reason to increase noise elsewhere.")
            applicationEvents.reversed().forEach { EventCard(it) }
        }
    }

    @Composable
    private fun ProcessesPage(padding: androidx.compose.foundation.layout.PaddingValues) {
        val processEvents = events.filter { it.category.equals("Process", true) || it.key.contains("process", true) }
        DetailPage(padding) {
            StatusCard("PROCESS OBSERVATIONS", processEvents.size.toString(), "Process visibility is subject to Android restrictions.")
            if (processEvents.isEmpty()) EmptyCard("No process changes recorded", "Open Full Observatory for the complete process visibility view.")
            processEvents.reversed().forEach { EventCard(it) }
            Button(onClick = { startActivity(Intent(this@PocketHomeActivity, MainActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("OPEN PROCESS OBSERVATORY") }
        }
    }

    @Composable
    private fun ConnectionsPage(padding: androidx.compose.foundation.layout.PaddingValues) {
        val connectionEvents = events.filter { it.category.equals("Network", true) || it.category.equals("Connection", true) || it.key.contains("network", true) || it.key.contains("vpn", true) || it.key.contains("bluetooth", true) }
        DetailPage(padding) {
            StatusCard("CONNECTION CHANGES", connectionEvents.size.toString(), "Connection observations are shown here without assuming who initiated them.")
            if (connectionEvents.isEmpty()) EmptyCard("No connection changes recorded", "Android may restrict visibility into some connection activity.")
            connectionEvents.reversed().forEach { EventCard(it) }
        }
    }

    @Composable
    private fun EvidencePage(padding: androidx.compose.foundation.layout.PaddingValues) {
        val integrity = verifyStoredChain(this@PocketHomeActivity)
        DetailPage(padding) {
            StatusCard("EVIDENCE CHAIN", if (integrity) "VERIFIED" else "INTEGRITY FAILURE", "Stored events are chained by hash. Verification does not prove that an unobserved interval was uneventful.")
            StatusCard("RECORDED EVENTS", events.size.toString(), "Each recorded event retains its observed state transition and hash in the existing evidence store.")
            EmptyCard("EVIDENCE GAP RULE", "If Watchtower could not observe an interval, preserve the interval as unknown. Do not manufacture an explanation for it.")
            Button(onClick = { startActivity(Intent(this@PocketHomeActivity, MainActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("OPEN FULL EVIDENCE RECORD") }
        }
    }

    @Composable
    private fun DetailPage(padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }

    @Composable
    private fun DashboardButton(title: String, description: String, onClick: () -> Unit) {
        Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Cyan)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = Violet)
            }
        }
    }

    @Composable
    private fun StatusCard(label: String, value: String, explanation: String) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(label, color = Cyan, style = MaterialTheme.typography.labelLarge)
                Text(value, style = MaterialTheme.typography.headlineSmall)
                Text(explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun EmptyCard(title: String, explanation: String) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun EventCard(event: SimpleEvent) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${event.category} / ${event.key}", style = MaterialTheme.typography.titleMedium)
                Text(event.time, style = MaterialTheme.typography.bodySmall)
                Text("${event.previous ?: "(none)"} → ${event.current}")
            }
        }
    }
}

@Composable
private fun TextButtonBack(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("BACK") }
}

private fun SimpleEvent.toEvent() = Event(time, key, category, previous, current, "")

private fun verifyStoredChain(context: Context): Boolean {
    val prefs = context.getSharedPreferences("watchtower", Context.MODE_PRIVATE)
    val array = runCatching { JSONArray(prefs.getString("events_json", "[]")) }.getOrDefault(JSONArray())
    var previous = "GENESIS"
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        val event = Event(o.optString("time"), o.optString("key"), o.optString("category"), if (o.isNull("previous")) null else o.optString("previous"), o.optString("current"), o.optString("hash"))
        val expected = dashboardSha256(listOf(previous, event.time, event.key, event.category, event.previous ?: "", event.current).joinToString("|"))
        if (expected != event.hash) return false
        previous = event.hash
    }
    return previous == (prefs.getString("last_hash", "GENESIS") ?: "GENESIS")
}

private fun readEvents(context: Context): List<SimpleEvent> {
    val prefs = context.getSharedPreferences("watchtower", Context.MODE_PRIVATE)
    val array = runCatching { JSONArray(prefs.getString("events_json", "[]")) }.getOrDefault(JSONArray())
    return buildList {
        for (index in 0 until array.length()) {
            val event = array.optJSONObject(index) ?: continue
            add(
                SimpleEvent(
                    time = event.optString("time"),
                    category = event.optString("category"),
                    key = event.optString("key"),
                    previous = if (event.isNull("previous")) null else event.optString("previous"),
                    current = event.optString("current")
                )
            )
        }
    }
}

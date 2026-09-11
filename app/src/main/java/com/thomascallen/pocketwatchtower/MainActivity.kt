package com.thomascallen.pocketwatchtower

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val VERSION = "0.4.0"
private const val REFRESH_MS = 5000L

private data class Event(
    val time: String,
    val key: String,
    val category: String,
    val previous: String?,
    val current: String,
    val hash: String
)

private class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("watchtower", Context.MODE_PRIVATE)
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    fun snapshot(): Map<String, String> = prefs.all.filterKeys { it.startsWith("obs_") }
        .mapKeys { it.key.removePrefix("obs_") }.mapValues { it.value.toString() }

    fun events(): List<Event> {
        val a = runCatching { JSONArray(prefs.getString("events_json", "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                add(Event(o.optString("time"), o.optString("key"), o.optString("category"),
                    if (o.isNull("previous")) null else o.optString("previous"), o.optString("current"), o.optString("hash")))
            }
        }
    }

    fun scan(items: List<ObservatoryItem>): List<Event> {
        val old = snapshot()
        var chain = prefs.getString("last_hash", "GENESIS") ?: "GENESIS"
        val changes = items.mapNotNull { item ->
            val previous = old[item.name]
            if (previous != null && previous != item.value) {
                val base = Event(formatter.format(Date()), item.name, item.section, previous, item.value, "")
                val hash = hashEvent(base, chain)
                chain = hash
                base.copy(hash = hash)
            } else null
        }
        persist(items, events() + changes, if (changes.isEmpty()) null else chain)
        return changes
    }

    fun establish(items: List<ObservatoryItem>) = persist(items, events(), prefs.getString("last_hash", "GENESIS"))

    fun verify(): Boolean {
        var chain = "GENESIS"
        for (e in events()) {
            val expected = hashEvent(e.copy(hash = ""), chain)
            if (expected != e.hash) return false
            chain = e.hash
        }
        return chain == (prefs.getString("last_hash", "GENESIS") ?: "GENESIS")
    }

    fun snapshotHash() = sha256(snapshot().toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" })

    fun exportReport(): String = buildString {
        appendLine("POCKET WATCHTOWER v$VERSION")
        appendLine("Local-first Android device observatory")
        appendLine("Generated: ${formatter.format(Date())}")
        appendLine("Integrity: ${if (verify()) "VERIFIED" else "INTEGRITY FAILURE"}")
        appendLine("Snapshot SHA-256: ${snapshotHash()}")
        appendLine("Events: ${events().size}")
        appendLine()
        if (events().isEmpty()) appendLine("No changes recorded.")
        events().forEach { appendLine("${it.time} | ${it.category}/${it.key}\n  Previous: ${it.previous ?: "(none)"}\n  Current: ${it.current}\n  Hash: ${it.hash}\n") }
        appendLine("Visibility rule: an observation is not an accusation. Android limitations remain explicit.")
    }

    private fun persist(items: List<ObservatoryItem>, history: List<Event>, lastHash: String?) {
        val edit = prefs.edit()
        items.forEach { edit.putString("obs_${it.name}", it.value) }
        edit.putString("events_json", encode(history))
        if (lastHash != null) edit.putString("last_hash", lastHash)
        edit.apply()
    }

    private fun encode(list: List<Event>) = JSONArray().apply {
        list.forEach { e -> put(JSONObject().apply {
            put("time", e.time); put("key", e.key); put("category", e.category)
            put("previous", e.previous); put("current", e.current); put("hash", e.hash)
        }) }
    }.toString()

    private fun hashEvent(e: Event, previousHash: String) = sha256(
        listOf(previousHash, e.time, e.key, e.category, e.previous ?: "", e.current).joinToString("|")
    )
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

class MainActivity : ComponentActivity() {
    private lateinit var store: WatchStore
    private lateinit var observatory: DeviceObservatory
    private val handler = Handler(Looper.getMainLooper())
    private var items by mutableStateOf(listOf<ObservatoryItem>())
    private var events by mutableStateOf(listOf<Event>())
    private var status by mutableStateOf("Starting live observation…")
    private var integrity by mutableStateOf("NOT VERIFIED")
    private var snapshotHash by mutableStateOf("-")
    private var lastScan by mutableStateOf("-")
    private var isWatching by mutableStateOf(true)
    private var selectedEvent by mutableStateOf<Event?>(null)
    private var selectedItem by mutableStateOf<ObservatoryItem?>(null)
    private var filter by mutableStateOf("ALL")

    private val liveScan = object : Runnable {
        override fun run() {
            if (isWatching && ::store.isInitialized) scan(false)
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchStore(this)
        observatory = DeviceObservatory(this)
        setContent { App() }
        scan(true)
        handler.postDelayed(liveScan, REFRESH_MS)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) scan(false)
    }

    override fun onDestroy() {
        handler.removeCallbacks(liveScan)
        super.onDestroy()
    }

    private fun scan(initial: Boolean) {
        val collected = runCatching { observatory.collect() }.getOrElse {
            status = "Observation error — collector isolated"
            return
        }
        if (store.snapshot().isEmpty()) {
            store.establish(collected)
            status = "Baseline established — watching now"
        } else {
            val changes = store.scan(collected)
            status = if (changes.isEmpty()) "Watching — no observable changes" else "${changes.size} observable change(s) detected"
        }
        items = collected
        events = store.events()
        integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE"
        snapshotHash = store.snapshotHash()
        lastScan = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        if (initial) isWatching = true
    }

    @Composable
    private fun App() {
        MaterialTheme {
            Scaffold(topBar = {
                TopAppBar(title = {
                    Column {
                        Text("Pocket Watchtower", fontWeight = FontWeight.Bold)
                        Text("Live Observatory v$VERSION", style = MaterialTheme.typography.labelSmall)
                    }
                })
            }) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)); LiveStatusCard() }
                    item { ActionRow() }
                    item { ProcessCard() }
                    item { SectionTitle("WHAT IS HAPPENING NOW") }
                    item { CurrentReadingsCard() }
                    item { SectionTitle("CHANGE TIMELINE") }
                    item { FilterRow() }
                    val visibleEvents = when (filter) {
                        "MEMORY" -> events.filter { it.category == "Memory" }
                        "BATTERY" -> events.filter { it.category == "Battery" }
                        else -> events
                    }
                    if (visibleEvents.isEmpty()) item { EmptyTimeline() }
                    items(visibleEvents.reversed().take(30)) { event -> EventCard(event) }
                    item { SectionTitle("VISIBILITY") }
                    item { VisibilityCard() }
                    item { SectionTitle("INTEGRITY") }
                    item { IntegrityCard() }
                    item { SectionTitle("THE BOUNDARY") }
                    item { BoundaryCard() }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        selectedEvent?.let { EventDialog(it) }
        selectedItem?.let { ItemDialog(it) }
    }

    @Composable
    private fun LiveStatusCard() {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("● ${if (isWatching) "WATCHTOWER ACTIVE" else "WATCHTOWER PAUSED"}", fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.titleMedium)
                Text("Last scan: $lastScan")
                Text("Integrity: $integrity")
                Text("${events.size} recorded change(s) • ${items.size} observable values")
            }
        }
    }

    @Composable
    private fun ActionRow() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scan(false) }, modifier = Modifier.weight(1f)) { Text("Scan now") }
            OutlinedButton(onClick = { isWatching = !isWatching }, modifier = Modifier.weight(1f)) {
                Text(if (isWatching) "Pause" else "Resume")
            }
            OutlinedButton(onClick = { share() }, modifier = Modifier.weight(1f)) { Text("Export") }
        }
    }

    @Composable
    private fun ProcessCard() {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WATCHTOWER PROCESS", style = MaterialTheme.typography.titleLarge)
                Text("This is what the app is actively doing:")
                ProcessLine("Device state", "Scanning", true)
                ProcessLine("Memory", "Sampling", true)
                ProcessLine("Battery", "Sampling", true)
                ProcessLine("Network", "Reading exposed state", true)
                ProcessLine("Sensors", "Reading exposed inventory", true)
                ProcessLine("Access / authority", "Reading Android-exposed state", true)
                ProcessLine("Application visibility", "Reading what Android exposes", true)
                ProcessLine("Change detector", "Comparing against local baseline", true)
                ProcessLine("Audit chain", "Hashing recorded changes", true)
                Text("Tap any reading or timeline event for the evidence behind it.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun ProcessLine(label: String, detail: String, active: Boolean) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${if (active) "●" else "○"} $label")
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun CurrentReadingsCard() {
        val important = items.filter { it.section in setOf("Memory", "Battery", "Network", "Device") }.take(12)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                important.forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { selectedItem = item }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            Text(item.section, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(item.value, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
                Text("Tap a value for meaning, source and Android limits.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    @Composable
    private fun FilterRow() {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "MEMORY", "BATTERY").forEach { value ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value) })
            }
        }
    }

    @Composable
    private fun EventCard(event: Event) {
        Card(Modifier.fillMaxWidth().clickable { selectedEvent = event }) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${event.category} • ${event.key}", fontWeight = FontWeight.SemiBold)
                    Text(event.time.substringAfter('T').take(8), style = MaterialTheme.typography.labelSmall)
                }
                Text("${event.previous ?: "(none)"}  →  ${event.current}")
                Text("OBSERVED • tap to inspect", style = MaterialTheme.typography.labelSmall)
                Text("SHA-256 ${event.hash.take(20)}…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun EmptyTimeline() {
        Card(Modifier.fillMaxWidth()) { Text("No observable changes yet. Keep Watchtower running and it will record changes it can actually see.", Modifier.padding(16.dp)) }
    }

    @Composable
    private fun VisibilityCard() {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("What Watchtower can know", style = MaterialTheme.typography.titleMedium)
                Text("🟢 CAN SEE — normal Android APIs expose it")
                Text("🟡 NEEDS ACCESS — Android requires your permission or special access")
                Text("🟠 ANDROID LIMITS — the OS restricts the information")
                Text("⚪ CANNOT DETERMINE — the fact is outside this device")
                Text("The visibility boundary is evidence, not a failure.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun IntegrityCard() {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LOCAL AUDIT INTEGRITY", style = MaterialTheme.typography.titleMedium)
                Text("$integrity")
                Text("Snapshot SHA-256")
                Text(snapshotHash, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE" }) { Text("Verify chain now") }
            }
        }
    }

    @Composable
    private fun BoundaryCard() {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("THE BOUNDARY", style = MaterialTheme.typography.titleMedium)
                Text("Pocket Watchtower reports observable device state. It does not identify who caused a change.")
                Text("It cannot see provider-side records, carrier records, remote systems, or information Android does not expose to ordinary apps.")
                Text("An observation is not an accusation.", fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }

    @Composable
    private fun EventDialog(event: Event) {
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text("Observation detail") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${event.category} / ${event.key}", fontWeight = FontWeight.Bold)
                    Text("Time: ${event.time}")
                    Text("Previous: ${event.previous ?: "(none)"}")
                    Text("Current: ${event.current}")
                    Text("Status: OBSERVED")
                    Text("This means Android exposed a value that changed. It does not establish why it changed or who caused it.")
                    Text("Event SHA-256:")
                    Text(event.hash, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { selectedEvent = null }) { Text("Close") } }
        )
    }

    @Composable
    private fun ItemDialog(item: ObservatoryItem) {
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.value, fontWeight = FontWeight.Bold)
                    Text("Section: ${item.section}")
                    Text("Truth status: ${item.status}")
                    Text(meaning(item))
                }
            },
            confirmButton = { Button(onClick = { selectedItem = null }) { Text("Close") } }
        )
    }

    private fun meaning(item: ObservatoryItem): String = when (item.status) {
        "RESTRICTED BY ANDROID" -> "Android limits what an ordinary application can determine here. Watchtower records the boundary rather than guessing beyond it."
        "REQUIRES SPECIAL ACCESS" -> "Android exposes this information only when the user grants the relevant special access."
        "USER-GRANTED" -> "This value depends on access granted by the device owner through Android."
        else -> "Android exposed this value through a normal device API. It is an observation, not a conclusion about intent."
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, store.exportReport())
        }
        startActivity(Intent.createChooser(intent, "Export Watchtower report"))
    }
}

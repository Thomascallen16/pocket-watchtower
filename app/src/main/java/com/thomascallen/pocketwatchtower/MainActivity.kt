package com.thomascallen.pocketwatchtower

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val VERSION = "0.7.0"

internal data class Event(val time: String, val key: String, val category: String, val previous: String?, val current: String, val hash: String)

private class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("watchtower", Context.MODE_PRIVATE)
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    fun snapshot(): Map<String, String> = prefs.all.filterKeys { it.startsWith("obs_") }.mapKeys { it.key.removePrefix("obs_") }.mapValues { it.value.toString() }
    fun events(): List<Event> {
        val a = runCatching { JSONArray(prefs.getString("events_json", "[]")) }.getOrDefault(JSONArray())
        return buildList { for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; add(Event(o.optString("time"), o.optString("key"), o.optString("category"), if (o.isNull("previous")) null else o.optString("previous"), o.optString("current"), o.optString("hash"))) } }
    }
    fun scan(items: List<ObservatoryItem>): List<Event> {
        val old = snapshot(); var chain = prefs.getString("last_hash", "GENESIS") ?: "GENESIS"
        val changes = items.mapNotNull { item -> val previous = old[item.name]; if (previous != null && previous != item.value) { val base = Event(formatter.format(Date()), item.name, item.section, previous, item.value, ""); val hash = hashEvent(base, chain); chain = hash; base.copy(hash = hash) } else null }
        persist(items, events() + changes, if (changes.isEmpty()) null else chain); return changes
    }
    fun establish(items: List<ObservatoryItem>) = persist(items, events(), prefs.getString("last_hash", "GENESIS"))
    fun verify(): Boolean { var chain = "GENESIS"; for (event in events()) { val expected = hashEvent(event.copy(hash = ""), chain); if (expected != event.hash) return false; chain = event.hash }; return chain == (prefs.getString("last_hash", "GENESIS") ?: "GENESIS") }
    fun snapshotHash(): String = sha256(snapshot().toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" })
    fun exportReport(): String = buildString {
        val history = events(); appendLine("POCKET WATCHTOWER v$VERSION"); appendLine("Local-first Android device observatory"); appendLine("Generated: ${formatter.format(Date())}"); appendLine("Integrity: ${if (verify()) "VERIFIED" else "INTEGRITY FAILURE"}"); appendLine("Evidence chain: ${if (verify()) "VERIFIED" else "BROKEN"}"); appendLine("Snapshot SHA-256: ${snapshotHash()}"); appendLine("Events: ${history.size}"); appendLine()
        if (history.isEmpty()) appendLine("No changes recorded.")
        var previousHash = "GENESIS"; history.forEach { appendLine("${it.time} | ${it.category}/${it.key}"); appendLine("  Previous: ${it.previous ?: "(none)"}"); appendLine("  Current: ${it.current}"); appendLine("  Previous event hash: $previousHash"); appendLine("  Event hash: ${it.hash}"); previousHash = it.hash; appendLine() }
        appendLine("Evidence rule: an observation is not an accusation."); appendLine("Visibility rule: Android limitations remain explicit.")
    }
    private fun persist(items: List<ObservatoryItem>, history: List<Event>, lastHash: String?) { val edit = prefs.edit(); items.forEach { edit.putString("obs_${it.name}", it.value) }; edit.putString("events_json", encode(history)); if (lastHash != null) edit.putString("last_hash", lastHash); edit.apply() }
    private fun encode(list: List<Event>) = JSONArray().apply { list.forEach { event -> put(JSONObject().apply { put("time", event.time); put("key", event.key); put("category", event.category); put("previous", event.previous); put("current", event.current); put("hash", event.hash) }) } }.toString()
    private fun hashEvent(event: Event, previousHash: String) = sha256(listOf(previousHash, event.time, event.key, event.category, event.previous ?: "", event.current).joinToString("|"))
}
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var store: WatchStore; private lateinit var observatory: DeviceObservatory
    private var items by mutableStateOf(listOf<ObservatoryItem>()); private var events by mutableStateOf(listOf<Event>()); private var status by mutableStateOf("Starting device scan…"); private var integrity by mutableStateOf("Not verified"); private var snapshotHash by mutableStateOf("-"); private var selectedSection by mutableStateOf("All"); private var selectedEvent by mutableStateOf<Event?>(null); private var selectedCurrent by mutableStateOf<ObservatoryItem?>(null)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); store = WatchStore(this); observatory = DeviceObservatory(this); setContent { App() }; scan() }
    override fun onResume() { super.onResume(); if (::store.isInitialized) scan() }
    private fun scan() { val collected = observatory.collect(); if (store.snapshot().isEmpty()) { store.establish(collected); status = "Baseline established" } else { val changes = store.scan(collected); status = if (changes.isEmpty()) "No observable changes" else "${changes.size} observable change(s) detected" }; items = collected; events = store.events(); integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE"; snapshotHash = store.snapshotHash() }

    @Composable private fun App() {
        MaterialTheme {
            Scaffold(topBar = { TopAppBar(title = { Text("Pocket Watchtower V$VERSION") }) }) { padding ->
                val sections = listOf("All") + items.map { it.section }.distinct(); val visibleItems = if (selectedSection == "All") items else items.filter { it.section == selectedSection }; val observedCount = items.count { it.status == "OBSERVED" || it.status == "KNOWN" }; val restrictedCount = items.count { it.status.contains("RESTRICTED") || it.status.contains("REQUIRES") }; val bursts = activityBursts(events); val timeline = events.reversed()
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("DEVICE OBSERVATORY", style = MaterialTheme.typography.labelLarge); Text(status, style = MaterialTheme.typography.titleLarge); Text("Integrity: $integrity"); Text("$observedCount directly observed  •  $restrictedCount restricted / permission-bound"); Text("${events.size} recorded change(s)  •  ${items.size} observable items"); Spacer(Modifier.padding(2.dp)); Text("Know Your Device. Evidence before inference.") } } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { scan() }) { Text("Scan now") }; OutlinedButton(onClick = { integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE" }) { Text("Verify") }; OutlinedButton(onClick = { share() }) { Text("Export") } } }
                    item { Text("Observable areas", style = MaterialTheme.typography.titleLarge) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { sections.take(4).forEach { section -> OutlinedButton(onClick = { selectedSection = section }) { Text(if (selectedSection == section) "• $section" else section) } } } }
                    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Visibility Map", style = MaterialTheme.typography.titleMedium); Text("KNOWN / OBSERVED — Android exposed it."); Text("USER-GRANTED / REQUIRES SPECIAL ACCESS — access depends on the owner."); Text("RESTRICTED BY ANDROID — the app cannot see everything."); Text("UNKNOWN — Watchtower cannot establish the answer from this device.") } } }
                    if (bursts.isNotEmpty()) { item { Text("Activity bursts", style = MaterialTheme.typography.titleLarge) }; items(bursts) { burst -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("${burst.events.size} observations • ${burst.start}", style = MaterialTheme.typography.titleMedium); Text(if (burst.start == burst.end) "Single observation" else "${burst.start} → ${burst.end}"); Text(burst.events.take(4).joinToString(" • ") { "${it.category}/${it.key}" } + if (burst.events.size > 4) " • +${burst.events.size - 4} more" else ""); Text("A burst groups closely timed observations. Grouping shows timing, not causation.", style = MaterialTheme.typography.bodySmall) } } } }
                    item { Text("What changed since my last scan?", style = MaterialTheme.typography.titleLarge); Text(if (timeline.isEmpty()) "No changes recorded yet." else "A chronological owner-readable timeline. Tap an event for the evidence details.", style = MaterialTheme.typography.bodySmall) }
                    items(timeline) { event -> val assessment = assessEvent(event, events); Card(Modifier.fillMaxWidth().clickable { selectedEvent = event }) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text("●", style = MaterialTheme.typography.titleMedium); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("${event.category} • ${event.key}", style = MaterialTheme.typography.titleMedium); Text(event.time, style = MaterialTheme.typography.bodySmall); Text("${event.previous ?: "(none)"} → ${event.current}"); Text("${assessment.level} • ${assessment.confidence}", style = MaterialTheme.typography.bodySmall) } } } }
                    item { Text(if (selectedSection == "All") "Current device" else selectedSection, style = MaterialTheme.typography.titleLarge); Text("Tap any current item to understand what it is.", style = MaterialTheme.typography.bodySmall) }
                    items(visibleItems) { item -> Card(Modifier.fillMaxWidth().clickable { selectedCurrent = item }) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${item.section} • ${item.name}", style = MaterialTheme.typography.titleMedium); Text("ABOUT", style = MaterialTheme.typography.labelMedium) }; Text(item.value); Text(item.status, style = MaterialTheme.typography.bodySmall) } } }
                    item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Evidence history", style = MaterialTheme.typography.titleLarge); Text("Tap any recorded event to understand what it is.", style = MaterialTheme.typography.bodySmall) } }
                    if (events.isEmpty()) item { Text("No changes recorded.") }
                    items(events.reversed()) { event -> val assessment = assessEvent(event, events); Card(Modifier.fillMaxWidth().clickable { selectedEvent = event }) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${event.category} • ${event.key}", style = MaterialTheme.typography.titleMedium); Text("ABOUT", style = MaterialTheme.typography.labelMedium) }; Text("${event.previous ?: "(none)"} → ${event.current}"); Text("${assessment.level} • Confidence: ${assessment.confidence}", style = MaterialTheme.typography.bodySmall); Text("SHA-256 ${event.hash.take(24)}…", style = MaterialTheme.typography.bodySmall) } } }
                    item { Text("Snapshot SHA-256: $snapshotHash", style = MaterialTheme.typography.bodySmall) }
                    item { Text("Watchtower reports observable state. It does not prove who caused a change, cannot see provider-side records, and cannot guarantee that a device is uncompromised.", style = MaterialTheme.typography.bodySmall) }
                }
            }
            selectedEvent?.let { event -> val explanation = explainEvent(event); val assessment = assessEvent(event, events); AlertDialog(onDismissRequest = { selectedEvent = null }, title = { Text(explanation.title) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("WHAT IT IS", style = MaterialTheme.typography.labelLarge); Text(explanation.whatItIs); Text("WHAT IT MEANS", style = MaterialTheme.typography.labelLarge); Text(explanation.whatItMeans); Text("WHY IT MATTERS", style = MaterialTheme.typography.labelLarge); Text(explanation.whyItMatters); Text("WHAT IT DOES NOT MEAN", style = MaterialTheme.typography.labelLarge); Text(explanation.doesNotMean); Text("EVENT ASSESSMENT", style = MaterialTheme.typography.labelLarge); Text("${assessment.level}. ${assessment.confidence}"); Text(assessment.detail); Text("ANDROID VISIBILITY", style = MaterialTheme.typography.labelLarge); Text(explanation.visibility); Text("Observed: ${event.previous ?: "(none)"} → ${event.current}", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text("Got it") } }) }
            selectedCurrent?.let { item -> val explanation = explainCurrentItem(item); AlertDialog(onDismissRequest = { selectedCurrent = null }, title = { Text(explanation.title) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("WHAT IT IS", style = MaterialTheme.typography.labelLarge); Text(explanation.whatItIs); Text("WHAT IT MEANS", style = MaterialTheme.typography.labelLarge); Text(explanation.whatItMeans); Text("WHY IT MATTERS", style = MaterialTheme.typography.labelLarge); Text(explanation.whyItMatters); Text("WHAT IT DOES NOT MEAN", style = MaterialTheme.typography.labelLarge); Text(explanation.doesNotMean); Text("ANDROID VISIBILITY", style = MaterialTheme.typography.labelLarge); Text(explanation.visibility); Text("Current reading: ${item.value}", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { selectedCurrent = null }) { Text("Got it") } }) }
        }
    }
    private fun share() { val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, store.exportReport()) }; startActivity(Intent.createChooser(intent, "Export Watchtower report")) }
}

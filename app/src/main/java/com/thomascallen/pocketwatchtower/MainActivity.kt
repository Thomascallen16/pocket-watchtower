package com.thomascallen.pocketwatchtower

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

private const val VERSION = "0.3.0"

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
                val hash = hashEvent(base, chain); chain = hash; base.copy(hash = hash)
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
        list.forEach { e -> put(JSONObject().apply { put("time", e.time); put("key", e.key); put("category", e.category); put("previous", e.previous); put("current", e.current); put("hash", e.hash) }) }
    }.toString()

    private fun hashEvent(e: Event, previousHash: String) = sha256(listOf(previousHash, e.time, e.key, e.category, e.previous ?: "", e.current).joinToString("|"))
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var store: WatchStore
    private lateinit var observatory: DeviceObservatory
    private var items by mutableStateOf(listOf<ObservatoryItem>())
    private var events by mutableStateOf(listOf<Event>())
    private var status by mutableStateOf("Starting device scan…")
    private var integrity by mutableStateOf("Not verified")
    private var snapshotHash by mutableStateOf("-")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { scan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchStore(this)
        observatory = DeviceObservatory(this)
        setContent { App() }
        scan()
    }

    override fun onResume() { super.onResume(); if (::store.isInitialized) scan() }

    private fun scan() {
        val collected = observatory.collect()
        if (store.snapshot().isEmpty()) {
            store.establish(collected)
            status = "Baseline established"
        } else {
            val changes = store.scan(collected)
            status = if (changes.isEmpty()) "No observable changes" else "${changes.size} observable change(s) detected"
        }
        items = collected
        events = store.events()
        integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE"
        snapshotHash = store.snapshotHash()
    }

    @Composable
    private fun App() {
        MaterialTheme {
            Scaffold(topBar = { TopAppBar(title = { Text("Pocket Watchtower v$VERSION") }) }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(status, style = MaterialTheme.typography.titleMedium)
                            Text("Integrity: $integrity")
                            Text("Observable items: ${items.size}")
                            Text("Recorded changes: ${events.size}")
                            Spacer(Modifier.padding(4.dp))
                            Text("Know Your Device. Evidence before inference.")
                        }}
                    }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) else scan()
                        }) { Text("Scan") }
                        OutlinedButton(onClick = { integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE" }) { Text("Verify") }
                        OutlinedButton(onClick = { share() }) { Text("Export") }
                    }}
                    item { Text("Visibility", style = MaterialTheme.typography.titleLarge) }
                    item { Text("KNOWN / OBSERVED = Android exposed it. USER-GRANTED = you enabled it. REQUIRES SPECIAL ACCESS = Android controls it. RESTRICTED BY ANDROID = the app cannot see everything.") }
                    item { Text("Current device", style = MaterialTheme.typography.titleLarge) }
                    items(items) { item ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text("${item.section} • ${item.name}", style = MaterialTheme.typography.titleMedium)
                            Text(item.value)
                            Text(item.status, style = MaterialTheme.typography.bodySmall)
                        }}
                    }
                    item { Text("Change history", style = MaterialTheme.typography.titleLarge) }
                    if (events.isEmpty()) item { Text("No changes recorded.") }
                    items(events.reversed()) { e -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text("${e.time} • ${e.category}/${e.key}")
                        Text("${e.previous ?: "(none)"} → ${e.current}")
                        Text("SHA-256 ${e.hash.take(24)}…", style = MaterialTheme.typography.bodySmall)
                    }}}
                    item { Text("Snapshot SHA-256: $snapshotHash", style = MaterialTheme.typography.bodySmall) }
                    item { Text("Watchtower reports observable state. It does not prove who caused a change, cannot see provider-side records, and cannot guarantee that a device is uncompromised.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, store.exportReport()) }
        startActivity(Intent.createChooser(intent, "Export Watchtower report"))
    }
}

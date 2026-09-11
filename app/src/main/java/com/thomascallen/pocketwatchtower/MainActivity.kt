package com.thomascallen.pocketwatchtower

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

private const val VERSION = "0.2.0"
private const val OBS_PREFIX = "obs_"

private data class Observation(val key: String, val value: String)
private data class Event(
    val time: String,
    val key: String,
    val previous: String?,
    val current: String,
    val hash: String
)

private class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("watchtower", Context.MODE_PRIVATE)
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    fun baseline(): Map<String, String> = prefs.all
        .filterKeys { it.startsWith(OBS_PREFIX) }
        .mapKeys { it.key.removePrefix(OBS_PREFIX) }
        .mapValues { it.value.toString() }

    fun events(): List<Event> {
        val array = runCatching { JSONArray(prefs.getString("events_json", "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(Event(
                    time = item.optString("time"),
                    key = item.optString("key"),
                    previous = if (item.has("previous") && !item.isNull("previous")) item.optString("previous") else null,
                    current = item.optString("current"),
                    hash = item.optString("hash")
                ))
            }
        }
    }

    fun hasBaseline(): Boolean = baseline().isNotEmpty()

    fun scan(observations: List<Observation>): List<Event> {
        val previous = baseline()
        var chainHash = previousHash()
        val newEvents = observations.mapNotNull { obs ->
            val old = previous[obs.key]
            if (old != null && old != obs.value) {
                val event = Event(formatter.format(Date()), obs.key, old, obs.value, "")
                val hash = hashEvent(event, chainHash)
                chainHash = hash
                event.copy(hash = hash)
            } else null
        }

        val allEvents = events() + newEvents
        val editor = prefs.edit()
        observations.forEach { editor.putString(OBS_PREFIX + it.key, it.value) }
        if (newEvents.isNotEmpty()) editor.putString("last_hash", newEvents.last().hash)
        editor.putString("events_json", encodeEvents(allEvents))
        editor.apply()
        return newEvents
    }

    fun establish(observations: List<Observation>) {
        val editor = prefs.edit()
        observations.forEach { editor.putString(OBS_PREFIX + it.key, it.value) }
        editor.apply()
    }

    fun previousHash(): String = prefs.getString("last_hash", "GENESIS") ?: "GENESIS"

    fun verify(): Boolean {
        var chainHash = "GENESIS"
        for (event in events()) {
            val expected = hashEvent(event.copy(hash = ""), chainHash)
            if (expected != event.hash) return false
            chainHash = event.hash
        }
        return chainHash == previousHash()
    }

    private fun hashEvent(event: Event, previousHash: String): String {
        val raw = listOf(previousHash, event.time, event.key, event.previous ?: "", event.current).joinToString("|")
        return sha256(raw)
    }

    private fun encodeEvents(events: List<Event>): String = JSONArray().apply {
        events.forEach { event ->
            put(JSONObject().apply {
                put("time", event.time)
                put("key", event.key)
                put("previous", event.previous)
                put("current", event.current)
                put("hash", event.hash)
            })
        }
    }.toString()

    fun exportText(): String = buildString {
        appendLine("POCKET WATCHTOWER v$VERSION")
        appendLine("Local device integrity event report")
        appendLine("Generated: ${formatter.format(Date())}")
        appendLine("Integrity: ${if (verify()) "VERIFIED" else "INTEGRITY FAILURE"}")
        appendLine()
        val history = events()
        if (history.isEmpty()) appendLine("No changes recorded.")
        history.forEach {
            appendLine("${it.time} | CHANGE DETECTED | ${it.key}")
            appendLine("  Previous: ${it.previous ?: "(none)"}")
            appendLine("  Current:  ${it.current}")
            appendLine("  Hash:     ${it.hash}")
            appendLine()
        }
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

class MainActivity : ComponentActivity() {
    private lateinit var store: WatchStore
    private var events by mutableStateOf(listOf<Event>())
    private var status by mutableStateOf("No baseline yet")
    private var integrity by mutableStateOf("Not verified")
    private var observations by mutableStateOf(listOf<Observation>())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { scan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchStore(this)
        events = store.events()
        setContent { App() }
        scan()
    }

    private fun scan() {
        val obs = collectObservations()
        observations = obs
        if (!store.hasBaseline()) {
            store.establish(obs)
            status = "Baseline established"
        } else {
            val newEvents = store.scan(obs)
            events = store.events()
            status = if (newEvents.isEmpty()) "No observable changes" else "${newEvents.size} change(s) detected"
        }
        integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE"
        events = store.events()
    }

    private fun collectObservations(): List<Observation> {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
            else -> "Offline/Unknown"
        }
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val result = mutableListOf(
            Observation("android_version", Build.VERSION.RELEASE ?: "unknown"),
            Observation("security_patch", if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "unknown"),
            Observation("device_model", "${Build.MANUFACTURER} ${Build.MODEL}"),
            Observation("network_transport", transport),
            Observation("airplane_mode", Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0).toString()),
            Observation("developer_options", Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0).toString()),
            Observation("device_secure", keyguard.isDeviceSecure.toString())
        )
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            result += Observation("sim_state", tm.simState.toString())
            result += Observation("network_operator", tm.networkOperatorName.ifBlank { "unknown" })
        }
        return result
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun App() {
        MaterialTheme {
            Scaffold(topBar = { TopAppBar(title = { Text("Pocket Watchtower v$VERSION") }) }) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(status, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text("Integrity: $integrity")
                                Spacer(Modifier.height(6.dp))
                                Text("Local-first. Observable changes, not accusations.")
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                                    permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                else scan()
                            }) { Text("Run scan") }
                            OutlinedButton(onClick = {
                                integrity = if (store.verify()) "VERIFIED" else "INTEGRITY FAILURE"
                            }) { Text("Verify") }
                            OutlinedButton(onClick = { shareReport() }) { Text("Export") }
                        }
                    }
                    item { Text("Current observations", style = MaterialTheme.typography.titleLarge) }
                    items(observations) { Text("${it.key}: ${it.value}") }
                    item { Text("Saved event history", style = MaterialTheme.typography.titleLarge) }
                    if (events.isEmpty()) item { Text("No changes recorded.") }
                    items(events.reversed()) { event ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${event.time}  •  CHANGE DETECTED")
                                Text(event.key)
                                Text("${event.previous ?: "(none)"} → ${event.current}")
                                Text("SHA-256: ${event.hash.take(20)}…")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shareReport() {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, store.exportText())
        }
        startActivity(android.content.Intent.createChooser(intent, "Export Watchtower report"))
    }
}

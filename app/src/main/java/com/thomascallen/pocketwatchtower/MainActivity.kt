package com.thomascallen.pocketwatchtower

import android.Manifest
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Observation(val key: String, val value: String)
private data class Event(val time: String, val key: String, val previous: String?, val current: String, val hash: String)

private class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("watchtower", Context.MODE_PRIVATE)
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    fun baseline(): Map<String, String> = prefs.all.mapValues { it.value.toString() }

    fun scan(observations: List<Observation>): List<Event> {
        val previous = baseline()
        val events = observations.mapNotNull { obs ->
            val old = previous[obs.key]
            if (old != null && old != obs.value) Event(formatter.format(Date()), obs.key, old, obs.value, "") else null
        }.map { it.copy(hash = hashEvent(it, previousHash())) }
        val editor = prefs.edit()
        observations.forEach { editor.putString(it.key, it.value) }
        if (events.isNotEmpty()) editor.putString("last_hash", events.last().hash)
        editor.apply()
        return events
    }

    fun establish(observations: List<Observation>) {
        val editor = prefs.edit()
        observations.forEach { editor.putString(it.key, it.value) }
        editor.apply()
    }

    fun previousHash(): String = prefs.getString("last_hash", "GENESIS") ?: "GENESIS"

    private fun hashEvent(event: Event, previousHash: String): String {
        val raw = listOf(previousHash, event.time, event.key, event.previous ?: "", event.current).joinToString("|")
        return sha256(raw)
    }

    fun exportText(events: List<Event>): String = buildString {
        appendLine("POCKET WATCHTOWER v0.1.0")
        appendLine("Local device integrity event report")
        appendLine("Generated: ${formatter.format(Date())}")
        appendLine()
        if (events.isEmpty()) appendLine("No changes recorded in this session.")
        events.forEach {
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
    private var observations by mutableStateOf(listOf<Observation>())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { scan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WatchStore(this)
        setContent { App() }
        scan()
    }

    private fun scan() {
        val obs = collectObservations()
        observations = obs
        if (store.baseline().isEmpty()) {
            store.establish(obs)
            status = "Baseline established"
            events = emptyList()
        } else {
            events = store.scan(obs)
            status = if (events.isEmpty()) "No observable changes" else "${events.size} change(s) detected"
        }
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
        val result = mutableListOf(
            Observation("android_version", Build.VERSION.RELEASE ?: "unknown"),
            Observation("security_patch", if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "unknown"),
            Observation("device_model", "${Build.MANUFACTURER} ${Build.MODEL}"),
            Observation("network_transport", transport),
            Observation("airplane_mode", Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0).toString()),
            Observation("developer_options", Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0).toString())
        )
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            result += Observation("sim_state", tm.simState.toString())
            result += Observation("network_operator", tm.networkOperatorName.ifBlank { "unknown" })
        }
        return result
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        MaterialTheme {
            Scaffold(topBar = { TopAppBar(title = { Text("Pocket Watchtower") }) }) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(status, style = MaterialTheme.typography.titleMedium)
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
                            OutlinedButton(onClick = { shareReport() }) { Text("Export") }
                        }
                    }
                    item { Text("Device baseline", style = MaterialTheme.typography.titleLarge) }
                    items(observations) { Text("${it.key}: ${it.value}") }
                    item { Text("Recent events", style = MaterialTheme.typography.titleLarge) }
                    if (events.isEmpty()) item { Text("No changes detected in this session.") }
                    items(events) { event ->
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
        val text = store.exportText(events)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(intent, "Export Watchtower report"))
    }
}

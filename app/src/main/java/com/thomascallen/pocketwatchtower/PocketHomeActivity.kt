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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray

private const val HOME_VERSION = "1.3.0"
private val Cyan = Color(0xFF00E5FF)
private val Green = Color(0xFF69F0AE)
private val Amber = Color(0xFFFFC107)
private val Red = Color(0xFFFF6B7D)

private data class SimpleEvent(
    val time: String,
    val category: String,
    val key: String,
    val previous: String?,
    val current: String
)

class PocketHomeActivity : ComponentActivity() {
    private var events by mutableStateOf(emptyList<SimpleEvent>())
    private var status by mutableStateOf("Checking your device…")
    private var showDetails by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refresh()
        setContent { Home() }
    }

    override fun onResume() {
        super.onResume()
        if (events.isNotEmpty()) refresh() else refresh()
    }

    private fun refresh() {
        events = readEvents(this)
        status = when {
            events.isEmpty() -> "No recorded changes"
            else -> "${events.size} recorded change${if (events.size == 1) "" else "s"}"
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Home() {
        WatchtowerTheme {
            Scaffold(
                topBar = {
                    TopAppBar(title = {
                        Column {
                            Text("Pocket Watchtower")
                            Text("DEVICE CUSTODY & INTEGRITY", style = MaterialTheme.typography.labelSmall, color = Cyan)
                        }
                    })
                }
            ) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("WHAT CHANGED WHILE I WASN'T WATCHING?", style = MaterialTheme.typography.titleMedium, color = Cyan)
                                Text(
                                    if (events.isEmpty()) "No significant device changes are currently recorded."
                                    else "Watchtower has recorded ${events.size} observable change${if (events.size == 1) "" else "s"}. Tap below to review them.",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text("Observed changes are evidence. They do not identify who caused them.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("DEVICE STATUS", style = MaterialTheme.typography.labelLarge, color = Cyan)
                                Text(
                                    if (events.isEmpty()) "NO SIGNIFICANT CHANGES DETECTED" else "CHANGES RECORDED",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (events.isEmpty()) Green else Amber
                                )
                                Text(status)
                                Text("Integrity and technical evidence remain available in the full observatory.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("CHECK NOW") }
                            OutlinedButton(onClick = { showDetails = !showDetails }, modifier = Modifier.weight(1f)) {
                                Text(if (showDetails) "HIDE CHANGES" else "WHAT CHANGED")
                            }
                        }
                    }

                    if (showDetails) {
                        item { Text("RECENT CHANGES", style = MaterialTheme.typography.titleMedium, color = Cyan) }
                        if (events.isEmpty()) {
                            item { Text("Nothing recorded yet.") }
                        } else {
                            items(events.takeLast(8).reversed()) { event ->
                                Card(Modifier.fillMaxWidth().clickable {
                                    startActivity(Intent(this@PocketHomeActivity, MainActivity::class.java))
                                }) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${event.category} • ${event.key}", style = MaterialTheme.typography.titleMedium)
                                        Text(event.time, style = MaterialTheme.typography.bodySmall)
                                        Text("${event.previous ?: "(none)"} → ${event.current}")
                                        Text("Tap for the full evidence record.", style = MaterialTheme.typography.bodySmall, color = Cyan)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("WAS MY DEVICE OUT OF MY CONTROL?", style = MaterialTheme.typography.titleMedium, color = Cyan)
                                Text("Watchtower can preserve observable device changes, but it cannot determine from the phone alone whether law enforcement, a technician, an administrator, or someone else examined it.")
                                Text("If the device was out of your possession, record the custody interval separately and treat the interval as an evidence gap—not an accusation.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { startActivity(Intent(this@PocketHomeActivity, MainActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("OPEN FULL OBSERVATORY") }
                    }

                    item {
                        Spacer(Modifier.padding(4.dp))
                        Text("Pocket Watchtower reports observable state. Unknown means unknown. Android may restrict what the app can see.", style = MaterialTheme.typography.bodySmall, color = Red)
                    }
                }
            }
        }
    }
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

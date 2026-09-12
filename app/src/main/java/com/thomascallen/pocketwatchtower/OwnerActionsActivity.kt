package com.thomascallen.pocketwatchtower

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class OwnerAppTarget(val label: String, val packageName: String, val system: Boolean)

class OwnerActionsActivity : ComponentActivity() {
    private lateinit var actions: OwnerActions
    private var result by mutableStateOf<OwnerActionResult?>(null)
    private var targets by mutableStateOf(emptyList<OwnerAppTarget>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actions = OwnerActions(this)
        targets = loadApps()
        setContent { WatchtowerTheme { OwnerActionScreen() } }
    }

    private fun loadApps(): List<OwnerAppTarget> = packageManager.getInstalledApplications(0)
        .map { OwnerAppTarget(packageManager.getApplicationLabel(it).toString(), it.packageName, (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) }
        .sortedWith(compareBy<OwnerAppTarget> { it.system }.thenBy { it.label.lowercase() })

    private fun openDetails(packageName: String) = startActivity(actions.appDetailsIntent(packageName))

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OwnerActionScreen() {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            result = OwnerActionResult(
                "Files selected",
                "${uris.size} document(s) are selected. Deletion is intentionally a separate confirmation step so the owner can inspect the exact targets.",
                false
            )
            selectedUris = uris
        }

        Scaffold(topBar = { TopAppBar(title = { Text("Owner Actions") }) }) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("OWNER-CONTROLLED REMEDIATION", style = MaterialTheme.typography.titleLarge)
                            Text(actions.actionPolicyText())
                            Text("Observation and remediation are separate. Android remains the authority for privileged operations.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("File cleanup", style = MaterialTheme.typography.titleMedium)
                            Text("Choose exact files through Android's document picker. Watchtower never guesses which files you meant to delete.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Select files") }
                                OutlinedButton(onClick = { result = actions.clearWatchtowerCache() }) { Text("Clean WT cache") }
                            }
                        }
                    }
                }
                item { Text("Applications", style = MaterialTheme.typography.titleLarge) }
                items(targets) { target ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(target.label, style = MaterialTheme.typography.titleMedium)
                            Text(target.packageName, style = MaterialTheme.typography.bodySmall)
                            Text(if (target.system) "SYSTEM APP" else "USER / INSTALLED APP", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { result = actions.endBackgroundProcesses(target.packageName) }) { Text("End background") }
                                OutlinedButton(onClick = { openDetails(target.packageName) }) { Text("Force stop…") }
                                OutlinedButton(onClick = { result = actions.uninstallGuidance(target.packageName); openDetails(target.packageName) }) { Text("Uninstall") }
                                OutlinedButton(onClick = { result = actions.suspendIfDeviceOwner(target.packageName) }) { Text("Suspend") }
                            }
                        }
                    }
                }
            }
        }

        result?.let { current ->
            AlertDialog(
                onDismissRequest = { result = null },
                title = { Text(current.title) },
                text = { Text(current.detail) },
                confirmButton = { TextButton(onClick = { result = null }) { Text("Done") } }
            )
        }

        selectedUris?.let { uris ->
            AlertDialog(
                onDismissRequest = { selectedUris = null },
                title = { Text("Delete selected files?") },
                text = { Text("This will ask Android's document provider to delete ${uris.size} selected document(s). Only provider-supported deletions will succeed.") },
                confirmButton = {
                    TextButton(onClick = {
                        var ok = 0
                        uris.forEach { if (actions.deleteSelectedDocument(it).completed) ok++ }
                        result = OwnerActionResult("Deletion completed", "$ok of ${uris.size} selected document(s) accepted deletion.", ok > 0)
                        selectedUris = null
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { selectedUris = null }) { Text("Cancel") } }
            )
        }
    }

    companion object {
        private var selectedUris by mutableStateOf<List<Uri>?>(null)
    }
}

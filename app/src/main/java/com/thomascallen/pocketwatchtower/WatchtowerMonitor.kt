package com.thomascallen.pocketwatchtower

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

internal object WatchtowerMonitor {
    private const val ACTION_SCAN = "com.thomascallen.pocketwatchtower.MONITOR_SCAN"
    private const val REQUEST = 7301
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, WatchtowerMonitorReceiver::class.java).setAction(ACTION_SCAN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(intent)
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            intent
        )
    }
}

class WatchtowerMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.thomascallen.pocketwatchtower.MONITOR_SCAN") return
        val prefs = context.getSharedPreferences("watchtower_monitor", Context.MODE_PRIVATE)
        val current = (DeviceObservatory(context).collect() + ProcessVisibility(context).collect())
            .associate { it.section + "/" + it.name to it.value }
        val old = prefs.all.mapValues { it.value.toString() }
        if (old.isEmpty()) {
            val edit = prefs.edit()
            current.forEach { (key, value) -> edit.putString(key, value) }
            edit.apply()
            return
        }

        val changed = current.entries.filter { old[it.key] != it.value }
        if (changed.isEmpty()) return

        val securityChanges = changed.count { it.key.startsWith("Security/") || it.key.startsWith("Access/") }
        val categories = changed.map { it.key.substringBefore('/') }.distinct().size
        val large = changed.size >= 3 || securityChanges > 0
        val signal = categories >= 2

        if (large || signal) {
            val reason = buildString {
                append("${changed.size} observable change(s) detected")
                if (securityChanges > 0) append("; security/access changed")
                if (signal) append("; changes span $categories categories")
                append(". Review Watchtower before drawing conclusions.")
            }
            WatchtowerNotifier.showAlert(
                context,
                if (securityChanges > 0) "WATCHTOWER • SECURITY/ACCESS CHANGE" else "WATCHTOWER • SIGNAL DETECTED",
                reason,
                if (securityChanges > 0) 4103 else 4102
            )
        }

        val edit = prefs.edit()
        current.forEach { (key, value) -> edit.putString(key, value) }
        edit.apply()
    }
}

class WatchtowerStartupProvider : android.content.ContentProvider() {
    override fun onCreate(): Boolean {
        WatchtowerMonitor.schedule(requireContext())
        return true
    }
    override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun getType(uri: android.net.Uri) = null
    override fun insert(uri: android.net.Uri, values: android.content.ContentValues?) = null
    override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

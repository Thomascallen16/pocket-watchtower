package com.thomascallen.pocketwatchtower

import android.app.ActivityManager
import android.content.Context

/**
 * Reports only the processes Android makes visible to an ordinary application.
 * This is deliberately framed as visibility, not a complete process inventory.
 */
internal class ProcessVisibility(private val context: Context) {
    fun collect(): List<ObservatoryItem> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = runCatching { am.runningAppProcesses.orEmpty() }.getOrDefault(emptyList())
        val names = processes
            .mapNotNull { it.processName?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()

        val details = processes
            .mapNotNull { process ->
                val name = process.processName ?: return@mapNotNull null
                val importance = when (process.importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
                    else -> "other"
                }
                "$name ($importance)"
            }
            .distinct()
            .sorted()

        return listOf(
            ObservatoryItem("Processes", "Android-visible process count", names.size.toString(), "OBSERVED"),
            ObservatoryItem(
                "Processes",
                "Android-visible processes",
                details.take(30).joinToString("; ").ifBlank { "None exposed" },
                "OBSERVED"
            ),
            ObservatoryItem(
                "Visibility",
                "Process inventory",
                "Android may limit which running processes an ordinary app can see; this is not a guaranteed complete process list.",
                "RESTRICTED BY ANDROID"
            )
        )
    }
}

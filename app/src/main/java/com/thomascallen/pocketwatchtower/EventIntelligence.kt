package com.thomascallen.pocketwatchtower

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal data class EventAssessment(
    val level: String,
    val confidence: String,
    val detail: String
)

internal data class ActivityBurst(
    val events: List<Event>,
    val start: String,
    val end: String
)

internal data class CurrentExplanation(
    val title: String,
    val whatItIs: String,
    val whatItMeans: String,
    val whyItMatters: String,
    val doesNotMean: String,
    val visibility: String
)

internal fun assessEvent(event: Event, nearby: List<Event>): EventAssessment {
    val current = numeric(event.current)
    val previous = numeric(event.previous)
    val delta = if (current != null && previous != null) abs(current - previous) else null
    val level = when {
        event.key.contains("Available RAM", ignoreCase = true) && delta != null && delta >= 256.0 -> "Notable change"
        event.key.contains("Current", ignoreCase = true) && event.category.equals("Battery", true) && delta != null && delta >= 500.0 -> "Notable change"
        event.key.contains("Uptime", ignoreCase = true) && delta != null && delta >= 5.0 -> "Notable change"
        else -> "Routine observation"
    }
    val coObserved = nearby.count { it !== event && abs(timeMillis(it) - timeMillis(event)) <= 2_000L }
    val correlation = if (coObserved > 0) {
        "This observation occurred within about 2 seconds of $coObserved other recorded observation(s). Co-occurrence is a timing relationship, not proof of causation."
    } else {
        "No closely timed companion observation was recorded for this event."
    }
    return EventAssessment(level, "High — the change itself was directly observed and hash-recorded.", correlation)
}

internal fun activityBursts(events: List<Event>): List<ActivityBurst> {
    if (events.isEmpty()) return emptyList()
    val chronological = events.sortedBy(::timeMillis)
    val bursts = mutableListOf<MutableList<Event>>()
    for (event in chronological) {
        val current = bursts.lastOrNull()
        if (current == null || timeMillis(event) - timeMillis(current.last()) > 5_000L) bursts += mutableListOf(event) else current += event
    }
    return bursts.map { group -> ActivityBurst(group, group.first().time, group.last().time) }.reversed()
}

internal fun explainCurrentItem(item: ObservatoryItem): CurrentExplanation {
    val title = "${item.section} • ${item.name}"
    val normalized = item.name.lowercase()
    return when {
        normalized.contains("process") -> CurrentExplanation(title, "A list of running processes that Android chose to expose to an ordinary application at scan time.", "The list can include process names and broad importance categories such as foreground, visible, service, or cached. It is a point-in-time visibility snapshot.", "It gives the owner a concrete place to look for unexpected visible processes and to compare process visibility across scans.", "It is not a complete list of everything running on the phone, and a visible process is not evidence of spying, compromise, or wrongdoing.", "Android deliberately restricts process visibility for ordinary apps. Watchtower must treat this as partial visibility, not a forensic process inventory.")
        normalized.contains("ram") || normalized.contains("memory") -> CurrentExplanation(title, "Available RAM is the amount of system memory Android currently reports as available for apps and system work.", "It naturally moves up and down as apps start, stop, cache data, or Android reclaims memory.", "Watching it over time can reveal changes in device workload and establish a normal baseline.", "A RAM change does not identify an app, person, attack, or cause by itself.", "Android exposes an estimate of memory availability; Watchtower cannot see every internal memory decision.")
        normalized.contains("battery") || normalized.contains("current") -> CurrentExplanation(title, "Battery current is the electrical current Android reports flowing into or out of the battery at the time of the reading.", "The sign depends on the device reporting convention and whether the battery is charging or discharging.", "Repeated readings help establish how the device behaves during different workloads and charging states.", "A current spike does not prove a particular app, person, interception, or compromise caused it.", "Battery telemetry varies by Android device and hardware. Watchtower reports what the operating system exposes.")
        normalized.contains("uptime") -> CurrentExplanation(title, "System uptime is how long Android reports the device has been running since its last boot.", "It normally increases continuously while the device remains running and resets after a reboot.", "It provides a useful time anchor for interpreting other observations.", "Uptime alone does not tell Watchtower why a reboot happened or who initiated it.", "Android exposes uptime, but not a complete explanation of every reboot cause.")
        else -> CurrentExplanation(title, "This is a value Watchtower can currently observe from the Android device.", "The current value is the latest reading available to the observatory.", "Repeated observations let Watchtower compare the device with its own history.", "Observation alone does not establish intent, identity, causation, or compromise.", "Visibility depends on Android's public APIs, device hardware, permissions, and OS restrictions.")
    }
}

private fun numeric(value: String?): Double? {
    if (value == null) return null
    val normalized = value.replace(",", "")
    val number = Regex("[-+]?\\d+(?:\\.\\d+)?").find(normalized)?.value ?: return null
    return number.toDoubleOrNull()?.let {
        when {
            normalized.contains("GB", true) -> it * 1024.0
            normalized.contains("KB", true) -> it / 1024.0
            else -> it
        }
    }
}

private fun timeMillis(event: Event): Long = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(event.time)?.time ?: 0L
}.getOrDefault(0L)

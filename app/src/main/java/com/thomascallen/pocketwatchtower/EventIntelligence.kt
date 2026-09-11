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
    return EventAssessment(
        level = level,
        confidence = "High — the change itself was directly observed and hash-recorded.",
        detail = correlation
    )
}

internal fun activityBursts(events: List<Event>): List<ActivityBurst> {
    if (events.isEmpty()) return emptyList()
    val chronological = events.sortedBy(::timeMillis)
    val bursts = mutableListOf<MutableList<Event>>()
    for (event in chronological) {
        val current = bursts.lastOrNull()
        if (current == null || timeMillis(event) - timeMillis(current.last()) > 5_000L) {
            bursts += mutableListOf(event)
        } else {
            current += event
        }
    }
    return bursts.map { group ->
        ActivityBurst(group, group.first().time, group.last().time)
    }.reversed()
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

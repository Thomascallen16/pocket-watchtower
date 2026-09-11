package com.thomascallen.pocketwatchtower

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class CorrelationSignal(
    val level: String,
    val title: String,
    val window: String,
    val observations: List<Event>,
    val families: List<String>,
    val whyItMatters: String,
    val possibleReasons: List<String>,
    val whatWouldStrengthen: String,
    val limitation: String
)

private val signalTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
private const val DEFAULT_WINDOW_MS = 5_000L

internal fun signalFamily(event: Event): String {
    val text = "${event.category} ${event.key}".lowercase(Locale.US)
    return when {
        listOf("battery", "charge", "charging", "voltage", "current", "temperature", "temp", "power source", "power").any(text::contains) -> "Power"
        listOf("ram", "memory", "storage", "disk", "cpu", "load", "uptime", "resource").any(text::contains) -> "Resource"
        listOf("vpn", "network", "wifi", "wi-fi", "cellular", "mobile data", "connectivity").any(text::contains) -> "Connectivity"
        listOf("accessibility", "overlay", "draw over", "notification listener", "special access").any(text::contains) -> "Accessibility / Special Access"
        listOf("package", "application", "app", "install", "uninstall", "update").any(text::contains) -> "Application"
        listOf("process", "foreground", "background", "visibility").any(text::contains) -> "Process Visibility"
        listOf("device admin", "device owner", "security", "credential", "lock", "admin").any(text::contains) -> "Security / Administration"
        else -> "Device State"
    }
}

internal fun detectSignals(events: List<Event>, windowMs: Long = DEFAULT_WINDOW_MS): List<CorrelationSignal> {
    if (events.size < 2) return emptyList()

    val ordered = events.mapNotNull { event ->
        runCatching { event to signalTimeFormat.parse(event.time)!!.time }.getOrNull()
    }.sortedBy { it.second }
    if (ordered.size < 2) return emptyList()

    val groups = mutableListOf<List<Event>>()
    var current = mutableListOf(ordered.first().first)
    var previousTime = ordered.first().second

    ordered.drop(1).forEach { (event, time) ->
        if (time - previousTime <= windowMs) {
            current += event
        } else {
            if (current.size >= 2) groups += current.toList()
            current = mutableListOf(event)
        }
        previousTime = time
    }
    if (current.size >= 2) groups += current.toList()

    return groups.mapNotNull { group ->
        val families = group.map(::signalFamily).distinct()
        if (families.size < 2) return@mapNotNull null

        val title = when {
            group.any { it.key.lowercase(Locale.US).contains("charging") || it.key.lowercase(Locale.US).contains("power source") } && families.contains("Power") ->
                "POWER STATE TRANSITION"
            families.contains("Connectivity") && families.contains("Application") ->
                "APPLICATION + CONNECTIVITY CHANGE"
            families.contains("Accessibility / Special Access") && families.contains("Application") ->
                "APPLICATION + SPECIAL ACCESS CHANGE"
            else -> "CROSS-FAMILY DEVICE STATE CHANGE"
        }

        val level = when {
            families.size >= 4 -> "CORRELATED SIGNAL — HIGH ATTENTION"
            families.size == 3 -> "CORRELATED SIGNAL — ATTENTION"
            else -> "CORRELATED SIGNAL — REVIEW"
        }

        val familyText = families.joinToString(" + ")
        val durationMs = runCatching {
            val first = signalTimeFormat.parse(group.first().time)!!.time
            val last = signalTimeFormat.parse(group.last().time)!!.time
            last - first
        }.getOrDefault(0L)

        val reasons = mutableListOf<String>()
        if (families.contains("Power")) {
            reasons += "A charger, cable, outlet, battery state, or transient power condition changed."
            reasons += "Android briefly reported a transition between power states while measurements were being sampled."
        }
        if (families.contains("Resource")) {
            reasons += "Normal application activity, garbage collection, background work, or system activity changed resource use."
        }
        if (families.contains("Application")) {
            reasons += "The owner installed, updated, opened, or configured an application."
        }
        if (families.contains("Accessibility / Special Access")) {
            reasons += "A legitimate accessibility, automation, security, or utility tool changed a special-access state."
        }
        if (families.contains("Connectivity")) {
            reasons += "Network, Wi-Fi, cellular, or VPN state transitioned normally."
        }
        reasons += "An unexpected device-state change occurred; the observations alone cannot establish the cause."

        CorrelationSignal(
            level = level,
            title = title,
            window = "${group.first().time} → ${group.last().time} (${durationMs} ms)",
            observations = group,
            families = families,
            whyItMatters = "${group.size} observations occurred within ${durationMs} ms and span ${families.size} signal families: $familyText. The measurements are more useful as one observable transition than as unrelated individual changes.",
            possibleReasons = reasons.distinct(),
            whatWouldStrengthen = "Inspect the exact underlying observations, affected application/package state, relevant special-access state, connectivity state, and any owner action occurring in the same time window.",
            limitation = "Correlation increases review value; it does not establish causation, identity, intent, compromise, spying, or unauthorized control. Watchtower can correlate only state Android exposes to it."
        )
    }.takeLast(8).reversed()
}

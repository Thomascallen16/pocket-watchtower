package com.thomascallen.pocketwatchtower

import java.text.SimpleDateFormat
import java.util.Locale

internal data class CorrelationSignal(
    val level: String,
    val title: String,
    val window: String,
    val observations: List<Event>,
    val evidenceAreas: List<String>,
    val confidence: String,
    val whatHappened: String,
    val whyItMatters: String,
    val possibleReasons: List<String>,
    val whatWouldStrengthen: String,
    val doesNotProve: String,
    val limitation: String
)

private val signalTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

internal fun detectSignals(events: List<Event>, windowMinutes: Long = 5): List<CorrelationSignal> {
    if (events.size < 2) return emptyList()
    val ordered = events.mapNotNull { event -> runCatching { event to signalTimeFormat.parse(event.time)!!.time }.getOrNull() }.sortedBy { it.second }
    val results = mutableListOf<CorrelationSignal>()

    for (start in ordered.indices) {
        val firstTime = ordered[start].second
        val group = ordered.drop(start).takeWhile { it.second - firstTime <= windowMinutes * 60_000L }.map { it.first }
        if (group.size < 2) continue

        val text = group.joinToString(" ") { "${it.key} ${it.category} ${it.current}" }.lowercase(Locale.US)
        val families = linkedSetOf<String>()
        if (text.contains("accessibility") || text.contains("access")) families += "access"
        if (text.contains("overlay") || text.contains("draw over")) families += "overlay"
        if (text.contains("vpn") || text.contains("network")) families += "network"
        if (text.contains("process")) families += "process"
        if (text.contains("app") || text.contains("package") || text.contains("install")) families += "application"
        if (text.contains("device admin") || text.contains("device owner")) families += "authority"
        if (text.contains("adb") || text.contains("developer")) families += "developer"

        // Battery, memory, storage, notification, uptime, and similar telemetry are
        // evidence, but are never attention signals by themselves or in combination.
        val highAttentionFamilies = families.intersect(
            setOf("access", "overlay", "network", "process", "application", "authority", "developer")
        )
        val packageInventoryChanged = group.any { it.key.equals("Package inventory SHA-256", true) }

        // A signal requires at least one high-attention evidence family. Ordinary
        // device telemetry cannot manufacture an attention event anymore.
        val qualifying = highAttentionFamilies.isNotEmpty() || packageInventoryChanged
        if (!qualifying) continue

        val signature = group.map { it.key + "=" + it.current }.sorted().joinToString("|")
        if (results.any { it.observations.map { e -> e.key + "=" + e.current }.sorted().joinToString("|") == signature }) continue

        val level = "HIGH ATTENTION"
        val confidence = if (highAttentionFamilies.size >= 2) {
            "Moderate — multiple high-interest observable areas changed in one window."
        } else {
            "Preliminary — a high-interest observable state changed; the cause is unresolved."
        }
        val reasons = mutableListOf<String>()
        if (families.contains("application") || packageInventoryChanged) reasons += "An application/package was installed, removed, updated, or otherwise changed in the visible inventory."
        if (families.contains("access")) reasons += "An accessibility or privileged access state changed."
        if (families.contains("overlay")) reasons += "An overlay/draw-over-other-apps capability was observed in the change window."
        if (families.contains("network")) reasons += "A network or VPN-related state changed."
        if (families.contains("authority")) reasons += "A device-admin or device-owner authority state changed."
        if (families.contains("developer")) reasons += "A developer/ADB-related state changed."
        if (families.contains("process")) reasons += "A process-related observable state changed."
        reasons += "The observed state changed for another reason not exposed by Watchtower; additional evidence is required."

        val areas = highAttentionFamilies.map { it.replaceFirstChar { c -> c.uppercase() } }.toMutableList()
        if (packageInventoryChanged && !areas.contains("Application")) areas += "Application"
        val title = when {
            packageInventoryChanged -> "Application/package state changed"
            highAttentionFamilies.size >= 2 -> "Multiple high-interest device states changed close together"
            else -> "High-interest device state changed"
        }
        val whatHappened = "${group.size} observable change(s) occurred between ${group.first().time} and ${group.last().time}. High-interest evidence areas involved: ${areas.distinct().joinToString(", ")}. Ordinary telemetry such as battery, memory, storage, and uptime is not treated as an attention signal."
        val why = "This event is surfaced because a high-interest device state changed. Timing and correlation increase review value, but they do not establish intent, causation, compromise, or wrongdoing."
        val strengthen = "Identify the affected application or service and compare installation/update time, granted special access, accessibility state, overlay capability, VPN/network state, process visibility, device-management state, and owner actions during this window."
        val notProve = "This signal does not prove spying, compromise, unauthorized control, malicious intent, or that one observed change caused another."
        val limitation = "Watchtower can correlate only observations Android exposes to it. Android may restrict process, package, permission, network, and provider-side visibility."

        results += CorrelationSignal(
            level = level,
            title = title,
            window = "${group.first().time} → ${group.last().time}",
            observations = group,
            evidenceAreas = areas.distinct(),
            confidence = confidence,
            whatHappened = whatHappened,
            whyItMatters = why,
            possibleReasons = reasons.distinct(),
            whatWouldStrengthen = strengthen,
            doesNotProve = notProve,
            limitation = limitation
        )
    }
    return results.takeLast(8).reversed()
}

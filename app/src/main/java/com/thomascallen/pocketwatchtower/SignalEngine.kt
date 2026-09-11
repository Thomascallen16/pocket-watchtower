package com.thomascallen.pocketwatchtower

import java.text.SimpleDateFormat
import java.util.Date
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
        if (text.contains("notification")) families += "notification"
        if (text.contains("device admin") || text.contains("device owner")) families += "authority"
        if (text.contains("adb") || text.contains("developer")) families += "developer"

        val qualifying = families.size >= 2 || (families.contains("application") && families.contains("process"))
        if (!qualifying) continue
        val signature = group.map { it.key + "=" + it.current }.sorted().joinToString("|")
        if (results.any { it.observations.map { e -> e.key + "=" + e.current }.sorted().joinToString("|") == signature }) continue

        val level = when {
            families.size >= 4 -> "CORRELATED SIGNAL — HIGH ATTENTION"
            families.size >= 3 -> "CORRELATED SIGNAL — ATTENTION"
            else -> "CORRELATED SIGNAL — REVIEW"
        }
        val confidence = when {
            families.size >= 4 -> "Moderate — multiple independent observable areas changed in one window."
            families.size >= 3 -> "Moderate — several related observable areas changed in one window."
            else -> "Preliminary — timing and evidence-area diversity justify review, but the cause is unresolved."
        }
        val reasons = mutableListOf<String>(
            "The owner intentionally installed or configured an application.",
            "A legitimate accessibility, automation, security, enterprise, or networking tool changed configuration.",
            "An application or Android update changed capabilities or runtime behavior."
        )
        if (families.contains("authority") || families.contains("developer")) reasons += "A device-management or developer configuration was changed."
        reasons += "An unexpected configuration or application change occurred; the observed data alone cannot establish the cause."

        val names = group.joinToString(" • ") { "${it.category}/${it.key}" }
        val areas = families.map { it.replaceFirstChar { c -> c.uppercase() } }
        val whatHappened = "${group.size} observable change(s) occurred between ${group.first().time} and ${group.last().time}, spanning ${areas.size} evidence area(s): ${areas.joinToString(", ")}. The engine can establish the timing and recorded values; it cannot establish intent from these observations alone."
        val why = "The cluster is more worthy of review than any single observation because multiple evidence areas changed within the same window. This increases review value, not proof of causation or wrongdoing."
        val strengthen = "Identify the affected application or service and compare installation/update time, granted special access, accessibility state, overlay capability, VPN/network state, process visibility, device-management state, and owner actions during this window."
        val notProve = "This signal does not prove spying, compromise, unauthorized control, malicious intent, or that one observed change caused another."
        val limitation = "Watchtower can correlate only observations Android exposes to it. Android may restrict process, package, permission, network, and provider-side visibility."

        results += CorrelationSignal(
            level = level,
            title = "Several related device changes occurred close together",
            window = "${group.first().time} → ${group.last().time}",
            observations = group,
            evidenceAreas = areas,
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

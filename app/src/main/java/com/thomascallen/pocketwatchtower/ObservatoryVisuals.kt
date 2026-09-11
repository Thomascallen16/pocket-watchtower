package com.thomascallen.pocketwatchtower

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Evidence-first visual layer. Visuals communicate observations, not a security score. */
@Composable
internal fun ObservatoryVisualSummary(status: String, integrity: String, observedCount: Int, restrictedCount: Int, changeCount: Int, signalCount: Int, events: List<Event>) {
    val totalVisibility = (observedCount + restrictedCount).coerceAtLeast(1)
    val visibilityFraction = observedCount.toFloat() / totalVisibility.toFloat()
    val recent = events.takeLast(12)
    val signals = detectSignals(events)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VisibilityRing(visibilityFraction, Modifier.size(112.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("OBSERVATORY STATUS", style = MaterialTheme.typography.labelLarge)
                    Text(status, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (integrity == "VERIFIED") "Evidence chain verified" else "Evidence chain requires review")
                    Text("${(visibilityFraction * 100).toInt()}% of counted state is directly observable", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualMetric("CHANGES", changeCount.toString(), Modifier.weight(1f))
                VisualMetric("SIGNALS", signalCount.toString(), Modifier.weight(1f))
                VisualMetric("OBSERVED", observedCount.toString(), Modifier.weight(1f))
            }
            EvidenceFlowStrip()
            if (signals.isNotEmpty()) {
                Text("CORRELATION MAP", style = MaterialTheme.typography.labelLarge)
                Text("The strongest evidence is often the relationship between observations—not one observation by itself.", style = MaterialTheme.typography.bodySmall)
                signals.take(2).forEach { SignalCorrelationMap(it) }
            }
            if (recent.isNotEmpty()) {
                Text("RECENT EVIDENCE ACTIVITY", style = MaterialTheme.typography.labelLarge)
                EvidencePulse(recent, Modifier.fillMaxWidth().height(62.dp))
                Text("${recent.size} recent recorded observation${if (recent.size == 1) "" else "s"}. Height is an activity trace, not a risk score.", style = MaterialTheme.typography.bodySmall)
            } else Text("No recorded change activity yet.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SignalCorrelationMap(signal: CorrelationSignal) {
    val nodes = signal.observations.take(5)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("SIGNAL DETECTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(signal.title, style = MaterialTheme.typography.titleMedium)
                }
                Text("${nodes.size} nodes", style = MaterialTheme.typography.labelSmall)
            }
            Text(signal.window, style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth()) {
                nodes.forEachIndexed { index, event ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("${event.category} / ${event.key}", style = MaterialTheme.typography.labelLarge)
                            Text("${event.previous ?: "(none)"} → ${event.current}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (index < nodes.lastIndex) Box(Modifier.padding(start = 16.dp).width(2.dp).height(14.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalStat("AREAS", signal.evidenceAreas.size.toString(), Modifier.weight(1f))
                SignalStat("CONFIDENCE", if (signal.confidence.startsWith("Moderate")) "MODERATE" else "PRELIMINARY", Modifier.weight(1f))
            }
            Text("WHY IT MATTERS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(signal.whyItMatters, style = MaterialTheme.typography.bodySmall)
            Text("Correlation increases review value. It does not establish causation.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SignalStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VisibilityRing(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(MaterialTheme.colorScheme.surfaceVariant, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(MaterialTheme.colorScheme.primary, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("visible", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VisualMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EvidenceFlowStrip() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        FlowNode("OBSERVE", "1"); FlowLine(); FlowNode("CORRELATE", "2"); FlowLine(); FlowNode("EXPLAIN", "3"); FlowLine(); FlowNode("VERIFY", "4")
    }
}

@Composable
private fun FlowNode(label: String, number: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(number, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FlowLine() { Box(Modifier.height(2.dp).weight(1f).padding(horizontal = 3.dp).background(MaterialTheme.colorScheme.outlineVariant)) }

@Composable
private fun EvidencePulse(events: List<Event>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (events.isEmpty()) return@Canvas
        val maxHeight = size.height * 0.78f; val baseline = size.height * 0.86f
        val step = if (events.size == 1) size.width else size.width / (events.size - 1).toFloat()
        val points = events.mapIndexed { index, event ->
            val magnitude = 0.35f + ((event.key.length + event.category.length) % 6) / 10f
            Offset(index * step, baseline - maxHeight * magnitude.coerceAtMost(0.9f))
        }
        for (i in 0 until points.lastIndex) drawLine(MaterialTheme.colorScheme.primary, points[i], points[i + 1], 4.dp.toPx(), cap = StrokeCap.Round)
        points.forEach { drawCircle(MaterialTheme.colorScheme.primary, 5.dp.toPx(), it) }
        drawLine(MaterialTheme.colorScheme.outlineVariant, Offset(0f, baseline), Offset(size.width, baseline), 2.dp.toPx())
    }
}

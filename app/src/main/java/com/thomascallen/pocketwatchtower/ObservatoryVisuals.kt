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

/**
 * Visual layer for the observatory dashboard.
 *
 * These visuals communicate observed state; they deliberately avoid pretending
 * to be a security/risk score. Every number comes directly from the dashboard
 * evidence already collected by Pocket Watchtower.
 */
@Composable
internal fun ObservatoryVisualSummary(
    status: String,
    integrity: String,
    observedCount: Int,
    restrictedCount: Int,
    changeCount: Int,
    signalCount: Int,
    events: List<Event>
) {
    val totalVisibility = (observedCount + restrictedCount).coerceAtLeast(1)
    val visibilityFraction = observedCount.toFloat() / totalVisibility.toFloat()
    val recent = events.takeLast(12)
    val verified = integrity == "VERIFIED"

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                VisibilityRing(
                    fraction = visibilityFraction,
                    observed = observedCount,
                    restricted = restrictedCount,
                    modifier = Modifier.size(112.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("OBSERVATORY STATUS", style = MaterialTheme.typography.labelLarge)
                    Text(status, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (verified) "Evidence chain verified" else "Evidence chain requires review",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${(visibilityFraction * 100).toInt()}% of counted state is directly observable",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualMetric("CHANGES", changeCount.toString(), Modifier.weight(1f))
                VisualMetric("SIGNALS", signalCount.toString(), Modifier.weight(1f))
                VisualMetric("OBSERVED", observedCount.toString(), Modifier.weight(1f))
            }

            EvidenceFlowStrip()

            if (recent.isNotEmpty()) {
                Text("RECENT EVIDENCE ACTIVITY", style = MaterialTheme.typography.labelLarge)
                EvidencePulse(events = recent, modifier = Modifier.fillMaxWidth().height(62.dp))
                Text(
                    "${recent.size} recent recorded observation${if (recent.size == 1) "" else "s"}. Height represents no risk score; it is only a visual activity trace.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("No recorded change activity yet.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VisibilityRing(
    fraction: Float,
    observed: Int,
    restricted: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = MaterialTheme.colorScheme.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = MaterialTheme.colorScheme.primary,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("visible", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VisualMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EvidenceFlowStrip() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FlowNode("OBSERVE", "1")
        FlowLine()
        FlowNode("CORRELATE", "2")
        FlowLine()
        FlowNode("EXPLAIN", "3")
        FlowLine()
        FlowNode("VERIFY", "4")
    }
}

@Composable
private fun FlowNode(label: String, number: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FlowLine() {
    Box(
        modifier = Modifier
            .height(2.dp)
            .weight(1f)
            .padding(horizontal = 3.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun EvidencePulse(events: List<Event>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (events.isEmpty()) return@Canvas
        val maxHeight = size.height * 0.78f
        val baseline = size.height * 0.86f
        val step = if (events.size == 1) size.width else size.width / (events.size - 1).toFloat()
        val points = events.mapIndexed { index, event ->
            val magnitude = 0.35f + ((event.key.length + event.category.length) % 6) / 10f
            Offset(index * step, baseline - maxHeight * magnitude.coerceAtMost(0.9f))
        }
        for (i in 0 until points.lastIndex) {
            drawLine(
                color = MaterialTheme.colorScheme.primary,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        points.forEach { point ->
            drawCircle(MaterialTheme.colorScheme.primary, radius = 5.dp.toPx(), center = point)
        }
        drawLine(
            color = MaterialTheme.colorScheme.outlineVariant,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 2.dp.toPx()
        )
    }
}

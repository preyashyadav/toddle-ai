package com.toddleai.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toddleai.app.analysis.ReplayAnalyzer.AnalysisResult
import com.toddleai.app.data.GaitNorms
import com.toddleai.app.data.models.CaptureAssessment
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.GaitEvent
import com.toddleai.app.data.models.Observation
import com.toddleai.app.data.models.Side
import com.toddleai.app.data.models.StepMeasurement
import com.toddleai.app.data.models.TemporalMetrics
import com.toddleai.app.ui.components.MetricCard
import com.toddleai.app.ui.components.ObservationCard
import com.toddleai.app.ui.theme.SoftIvory
import com.toddleai.app.ui.theme.WarmSand
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ResultsScreen(
    result: AnalysisResult,
    childName: String,
    childAgeMonths: Int,
    inferenceBackend: String,
    onAskToddleAI: () -> Unit,
    onRecordAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val cadenceRange = GaitNorms.getCadenceRange(childAgeMonths)
    var showQualityBreakdown by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = SoftIvory,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(SoftIvory, WarmSand),
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HeaderSection(
                childName = childName,
                childAgeMonths = childAgeMonths,
                assessment = result.assessment,
                onBack = onBack,
            )

            if (result.assessment.confidence == CaptureConfidence.REJECT || result.metrics == null) {
                RejectedSection(
                    assessment = result.assessment,
                    onRecordAnother = onRecordAnother,
                )
                FooterSection(
                    analysisTimeMs = result.analysisTimeMs,
                    inferenceBackend = inferenceBackend,
                )
                return@Column
            }

            QualityBreakdownSection(
                result = result,
                expanded = showQualityBreakdown,
                onToggle = { showQualityBreakdown = !showQualityBreakdown },
            )

            MetricsSection(
                result = result,
                cadenceRangeLabel = "${format0(cadenceRange.low)}-${format0(cadenceRange.high)} steps/min",
                onAskToddleAI = onAskToddleAI,
            )

            ObservationsSection(
                observations = result.observations,
            )

            ActionsSection(
                onAskToddleAI = onAskToddleAI,
                onRecordAnother = onRecordAnother,
            )

            FooterSection(
                analysisTimeMs = result.analysisTimeMs,
                inferenceBackend = inferenceBackend,
            )
        }
    }
}

@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onAskAssistant: () -> Unit,
) {
    ResultsScreen(
        result = demoAnalysisResult(),
        childName = "Avery",
        childAgeMonths = 26,
        inferenceBackend = "MediaPipe Pose Landmarker (CPU)",
        onAskToddleAI = onAskAssistant,
        onRecordAnother = onBack,
        onBack = onBack,
    )
}

@Composable
private fun HeaderSection(
    childName: String,
    childAgeMonths: Int,
    assessment: CaptureAssessment,
    onBack: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmSand),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }

                ConfidenceBadge(confidence = assessment.confidence)
            }

            Text(
                text = childName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${formatAge(childAgeMonths)} • ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RejectedSection(
    assessment: CaptureAssessment,
    onRecordAnother: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "We couldn’t use this recording for step analysis yet.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            assessment.issues.forEach { issue ->
                Text(
                    text = issue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Button(onClick = onRecordAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Record Again")
            }
        }
    }
}

@Composable
private fun QualityBreakdownSection(
    result: AnalysisResult,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Capture Quality Breakdown",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QualityLine(
                        text = if (result.assessment.confidence == CaptureConfidence.HIGH || result.assessment.confidence == CaptureConfidence.MEDIUM) {
                            "✓ Both feet stayed visible often enough for replay analysis"
                        } else {
                            "⚠ Foot visibility was intermittent during capture"
                        },
                    )
                    QualityLine(
                        text = if (result.assessment.issues.any { it.contains("Camera movement", ignoreCase = true) }) {
                            "⚠ Camera movement was detected at points"
                        } else {
                            "✓ Camera stayed stable through the best segment"
                        },
                    )
                    QualityLine(
                        text = "✓ ${result.assessment.usableStepCount} usable steps detected",
                    )
                    QualityLine(
                        text = qualityDetailLine(result),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsSection(
    result: AnalysisResult,
    cadenceRangeLabel: String,
    onAskToddleAI: () -> Unit,
) {
    val metrics = result.metrics ?: return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Temporal Metrics")
        MetricCard(
            title = "Cadence",
            value = "${format0(metrics.cadence)} steps/min",
            supportingText = "Published context for this age is approximately $cadenceRangeLabel.",
            onExplain = onAskToddleAI,
        )
        MetricCard(
            title = "Step time",
            value = "${format2(metrics.meanStepTime)}s average",
            supportingText = "${metrics.usableStepCount} usable steps contributed to this estimate.",
            onExplain = onAskToddleAI,
        )
        MetricCard(
            title = "Left / Right timing",
            value = "Left ${format2(metrics.leftMeanStepTime)}s | Right ${format2(metrics.rightMeanStepTime)}s | Diff: ${format0(metrics.timingDifferenceMs)}ms",
            supportingText = "This compares average alternating step timing across the accepted segment.",
            onExplain = onAskToddleAI,
        )
        MetricCard(
            title = "Consistency",
            value = "Step-time variation: ${format0(normalizedCovPercent(metrics.stepTimeCoV))}%",
            supportingText = "Lower variation generally means a steadier rhythm within this recording.",
            onExplain = onAskToddleAI,
        )
    }
}

@Composable
private fun ObservationsSection(
    observations: List<Observation>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Observations")
        observations.forEach { observation ->
            ObservationCard(observation = observation)
        }
    }
}

@Composable
private fun ActionsSection(
    onAskToddleAI: () -> Unit,
    onRecordAnother: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onAskToddleAI,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ask ToddleAI")
        }
        OutlinedButton(
            onClick = onRecordAnother,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Record Another Video")
        }
    }
}

@Composable
private fun FooterSection(
    analysisTimeMs: Long,
    inferenceBackend: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Research prototype — not a clinical assessment",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Processed entirely on this device",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "No data was transmitted",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text(
                text = "Analysis time: ${analysisTimeMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Inference backend: $inferenceBackend",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QualityLine(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ConfidenceBadge(
    confidence: CaptureConfidence,
) {
    val (label, color, textColor) = when (confidence) {
        CaptureConfidence.HIGH -> Triple("HIGH", Color(0xFFD8EEDB), Color(0xFF285C31))
        CaptureConfidence.MEDIUM -> Triple("MEDIUM", Color(0xFFF6E2B8), Color(0xFF855F11))
        CaptureConfidence.LOW -> Triple("LOW", Color(0xFFF3D1B4), Color(0xFF8A4F17))
        CaptureConfidence.REJECT -> Triple("REJECT", Color(0xFFF5D1D1), Color(0xFF8F3131))
    }

    Surface(
        color = color,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun qualityDetailLine(result: AnalysisResult): String {
    val issue = result.assessment.issues.firstOrNull()
    return when {
        issue != null && issue.contains("Feet were hidden", ignoreCase = true) ->
            "⚠ Minor occlusion points were excluded from analysis"
        result.processedFrameCount > 0 ->
            "✓ ${result.processedFrameCount} frames were retained in the best-quality segment"
        else ->
            "✓ The best continuous walking segment was accepted for analysis"
    }
}

private fun formatAge(ageMonths: Int): String {
    val years = ageMonths / 12
    val months = ageMonths % 12
    return when {
        years > 0 && months > 0 -> "$years yr $months mo"
        years > 0 -> "$years yr"
        else -> "$months mo"
    }
}

private fun normalizedCovPercent(value: Float): Float {
    return if (value > 1f) value else value * 100f
}

private fun format0(value: Float): String = String.format(Locale.US, "%.0f", value)

private fun format2(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun demoAnalysisResult(): AnalysisResult {
    val metrics = TemporalMetrics(
        stepTimes = listOf(
            StepMeasurement(duration = 0.41f, endingSide = Side.LEFT, confidence = 0.92f),
            StepMeasurement(duration = 0.43f, endingSide = Side.RIGHT, confidence = 0.90f),
            StepMeasurement(duration = 0.40f, endingSide = Side.LEFT, confidence = 0.89f),
            StepMeasurement(duration = 0.44f, endingSide = Side.RIGHT, confidence = 0.93f),
            StepMeasurement(duration = 0.42f, endingSide = Side.LEFT, confidence = 0.91f),
            StepMeasurement(duration = 0.45f, endingSide = Side.RIGHT, confidence = 0.88f),
        ),
        meanStepTime = 0.425f,
        cadence = 141.2f,
        leftMeanStepTime = 0.41f,
        rightMeanStepTime = 0.44f,
        timingDifferenceMs = 30f,
        symmetryRatio = 0.93f,
        stepTimeCoV = 0.08f,
        usableStepCount = 6,
        usableCycleCount = 3,
    )

    val observations = listOf(
        Observation(
            type = "cadence",
            measurement = "141 steps/min",
            context = "Published research reports cadence ranges of approximately 120-160 steps/min for children around this age, with substantial individual variation.",
            note = "This cadence is presented as context for this recording.",
            confidence = "HIGH",
        ),
        Observation(
            type = "summary",
            measurement = "Mean step time 0.43s",
            context = "The temporal measures from this recording stayed close to the broad developmental ranges used for age-based context.",
            note = "These observations describe this recording only.",
            confidence = "HIGH",
        ),
    )

    return AnalysisResult(
        assessment = CaptureAssessment(
            confidence = CaptureConfidence.HIGH,
            issues = listOf("Minor occlusion at 2 points (excluded from analysis)"),
            bestSegmentStart = 15,
            bestSegmentEnd = 140,
            usableStepCount = 6,
        ),
        metrics = metrics,
        observations = observations,
        events = listOf(
            GaitEvent(frameIndex = 22, timeSeconds = 0.73f, side = Side.LEFT, confidence = 0.93f),
            GaitEvent(frameIndex = 35, timeSeconds = 1.16f, side = Side.RIGHT, confidence = 0.92f),
        ),
        processedFrameCount = 126,
        analysisTimeMs = 842L,
    )
}

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
import com.toddleai.app.data.models.CaptureAssessment
import com.toddleai.app.data.models.CaptureConfidence
import com.toddleai.app.data.models.GaitEvent
import com.toddleai.app.data.models.MetricStatus
import com.toddleai.app.data.models.Observation
import com.toddleai.app.data.models.Side
import com.toddleai.app.data.models.StepMeasurement
import com.toddleai.app.data.models.TemporalMetrics
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
    onAskToddleAI: () -> Unit,
    onRecordAnother: () -> Unit,
    onBack: () -> Unit,
) {
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
                onBack = onBack,
            )

            if (result.assessment.confidence == CaptureConfidence.REJECT || result.metrics == null) {
                RejectedSection(
                    assessment = result.assessment,
                    onRecordAnother = onRecordAnother,
                )
                FooterSection()
                return@Column
            }

            ObservationsSection(
                observations = result.observations,
                usableStepCount = result.metrics?.usableStepCount ?: 0,
            )

            ActionsSection(
                onAskToddleAI = onAskToddleAI,
                onRecordAnother = onRecordAnother,
            )

            FooterSection()
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
        onAskToddleAI = onAskAssistant,
        onRecordAnother = onBack,
        onBack = onBack,
    )
}

@Composable
private fun HeaderSection(
    childName: String,
    childAgeMonths: Int,
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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                )
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
private fun ObservationsSection(
    observations: List<Observation>,
    usableStepCount: Int,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Gait Metrics")
        observations.forEach { observation ->
            ObservationCard(observation = observation)
        }
        Text(
            text = "Based on $usableStepCount walking steps in this clip.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun FooterSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Research prototype — not a clinical assessment",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Processed on-device · nothing was uploaded",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
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

private fun formatAge(ageMonths: Int): String {
    val years = ageMonths / 12
    val months = ageMonths % 12
    return when {
        years > 0 && months > 0 -> "$years yr $months mo"
        years > 0 -> "$years yr"
        else -> "$months mo"
    }
}

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
        medianStepTime = 0.42f,
        cadence = 142.9f,
        leftMeanStepTime = 0.41f,
        rightMeanStepTime = 0.44f,
        timingDifferenceMs = 30f,
        symmetryRatio = 0.93f,
        stepTimeAsymmetryPct = 7f,
        stepTimeCoV = 8f,
        usableStepCount = 6,
        usableCycleCount = 3,
    )

    val observations = listOf(
        Observation(
            type = "cadence",
            measurement = "Cadence: 143 steps/min",
            context = "Typical for a 2-year-old: 120–160 steps/min.",
            note = "In the typical range.",
            confidence = "",
            status = MetricStatus.TYPICAL,
        ),
        Observation(
            type = "symmetry",
            measurement = "Left–right symmetry: 7% difference",
            context = "Even left/right step timing is typically within 10% (left 0.41 s, right 0.44 s).",
            note = "In the typical range.",
            confidence = "",
            status = MetricStatus.TYPICAL,
        ),
        Observation(
            type = "variability",
            measurement = "Step rhythm: 8% variation",
            context = "Step timing is typically within 15% variation; young children are naturally a bit higher.",
            note = "In the typical range.",
            confidence = "",
            status = MetricStatus.TYPICAL,
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

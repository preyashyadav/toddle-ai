package com.toddleai.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.toddleai.app.ToddleAISessionViewModel
import com.toddleai.app.ui.theme.SoftIvory
import com.toddleai.app.ui.theme.Terracotta
import com.toddleai.app.ui.theme.WarmSand

@Composable
fun AnalyzingScreen(
    sessionViewModel: ToddleAISessionViewModel,
    onComplete: () -> Unit,
) {
    val progress by sessionViewModel.analysisProgress.collectAsState()
    val childName by sessionViewModel.childName.collectAsState()
    val sessionMode by sessionViewModel.sessionMode.collectAsState()
    val pulseTransition = rememberInfiniteTransition(label = "analysisPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    LaunchedEffect(Unit) {
        sessionViewModel.analyzeCapture {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SoftIvory, WarmSand))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(pulseScale)
                    .background(Terracotta.copy(alpha = 0.18f), shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Terracotta, shape = MaterialTheme.shapes.extraLarge),
                )
            }

            Text(
                text = if (childName.isBlank()) {
                    "Analyzing the walking clip…"
                } else {
                    "Analyzing ${childName}'s walk…"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = when (sessionMode) {
                    com.toddleai.app.SessionMode.LiveCapture -> "Processing live capture locally on this device"
                    com.toddleai.app.SessionMode.ImportedVideo -> "Processing imported video locally on this device"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

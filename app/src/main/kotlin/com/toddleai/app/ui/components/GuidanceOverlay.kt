package com.toddleai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toddleai.app.analysis.GuidanceState
import com.toddleai.app.analysis.QualityLevel
import com.toddleai.app.data.models.FrameQuality
import java.util.Locale

@Composable
fun GuidanceOverlay(
    guidanceState: GuidanceState,
    recentQualities: List<FrameQuality>,
    recordingDuration: Float,
    modifier: Modifier = Modifier,
) {
    val stepScale = rememberStepScale(guidanceState.stepCount)
    val readyPulse = rememberReadyPulse(guidanceState.isRecordingAcceptable)

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        GuideZone(
            modifier = Modifier.matchParentSize(),
            accentColor = qualityColor(guidanceState.overallQuality, MaterialTheme.colorScheme),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Crossfade(
                    targetState = guidanceState.message,
                    animationSpec = tween(durationMillis = 250),
                    label = "guidanceMessage",
                ) { message ->
                    if (message != null) {
                        OverlayPill(
                            text = message,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f, fill = false))
                    }
                }

                OverlayPill(
                    text = formatDuration(recordingDuration),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            AnimatedVisibility(visible = guidanceState.isRecordingAcceptable) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier.scale(readyPulse),
                ) {
                    Text(
                        text = "\u2713 Ready",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QualityDot(passRatio = recentQualities.passRatio { it.bothFeetVisible }, label = "feet")
            QualityDot(passRatio = recentQualities.passRatio { it.cameraStable }, label = "stable")
            QualityDot(passRatio = recentQualities.passRatio { it.fullBodyInFrame }, label = "body")
        }

        Surface(
            shape = CircleShape,
            color = stepBadgeColor(guidanceState.stepCount, MaterialTheme.colorScheme),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .scale(stepScale),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${guidanceState.stepCount} / ${guidanceState.stepCount + guidanceState.stepsNeeded}",
                    color = badgeTextColor(guidanceState.stepCount, MaterialTheme.colorScheme),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "steps",
                    color = badgeTextColor(guidanceState.stepCount, MaterialTheme.colorScheme),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        AnimatedVisibility(
            visible = guidanceState.isRecordingAcceptable,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            ) {
                Text(
                    text = "Stop recording",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideZone(
    modifier: Modifier,
    accentColor: Color,
) {
    Canvas(modifier = modifier) {
        val width = size.width * 0.82f
        val height = size.height * 0.44f
        val left = (size.width - width) / 2f
        val top = size.height * 0.34f
        drawRoundRect(
            color = accentColor.copy(alpha = 0.55f),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(26f, 26f),
            style = Stroke(
                width = 4f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 16f)),
            ),
        )
    }
}

@Composable
private fun OverlayPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun QualityDot(
    passRatio: Float,
    label: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val color = when {
        passRatio >= 0.75f -> colorScheme.secondary
        passRatio >= 0.35f -> colorScheme.tertiary
        else -> colorScheme.error
    }

    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.48f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = color, shape = CircleShape),
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun rememberStepScale(stepCount: Int): Float {
    var targetScale by remember { mutableStateOf(1f) }
    LaunchedEffect(stepCount) {
        targetScale = 1.14f
        targetScale = 1f
    }
    return animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 340f),
        label = "stepScale",
    ).value
}

@Composable
private fun rememberReadyPulse(isReady: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "readyPulse")
    val animated by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "readyPulseValue",
    )
    return if (isReady) animated else 1f
}

private fun qualityColor(
    level: QualityLevel,
    colorScheme: androidx.compose.material3.ColorScheme,
): Color = when (level) {
    QualityLevel.EXCELLENT -> colorScheme.secondary
    QualityLevel.GOOD -> colorScheme.primary
    QualityLevel.NEEDS_ADJUSTMENT -> colorScheme.tertiary
    QualityLevel.POOR -> colorScheme.error
}

private fun stepBadgeColor(
    stepCount: Int,
    colorScheme: androidx.compose.material3.ColorScheme,
): Color = when {
    stepCount >= 5 -> colorScheme.secondaryContainer
    stepCount >= 1 -> colorScheme.tertiaryContainer
    else -> colorScheme.surfaceVariant
}

private fun badgeTextColor(
    stepCount: Int,
    colorScheme: androidx.compose.material3.ColorScheme,
): Color = when {
    stepCount >= 5 -> colorScheme.onSecondaryContainer
    stepCount >= 1 -> colorScheme.onTertiaryContainer
    else -> colorScheme.onSurfaceVariant
}

private fun formatDuration(seconds: Float): String {
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainder = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, remainder)
}

private fun List<FrameQuality>.passRatio(check: (FrameQuality) -> Boolean): Float {
    if (isEmpty()) return 0f
    return count(check).toFloat() / size.toFloat()
}

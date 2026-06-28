package com.toddleai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.toddleai.app.data.models.FrameQuality
import com.toddleai.app.data.models.FrameStatus
import com.toddleai.app.data.models.MediaPipeLandmarks
import com.toddleai.app.data.models.PoseFrame
import kotlin.math.max

@Composable
fun SkeletonOverlay(
    poseFrame: PoseFrame?,
    frameQuality: FrameQuality?,
    previewWidth: Int,
    previewHeight: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pointRadiusPx = with(density) { 6.dp.toPx() }
    val lineWidthPx = with(density) { 3.dp.toPx() }

    val overlayAlpha = when (frameQuality?.status) {
        FrameStatus.REJECTED -> 0.4f
        FrameStatus.PARTIAL -> 0.72f
        else -> 0.95f
    }

    val drawingData = remember(poseFrame, previewWidth, previewHeight, overlayAlpha) {
        buildDrawingData(
            poseFrame = poseFrame,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            overlayAlpha = overlayAlpha,
        )
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        for (connection in drawingData.connections) {
            drawLine(
                color = connection.color,
                start = connection.start,
                end = connection.end,
                strokeWidth = lineWidthPx,
                cap = StrokeCap.Round,
            )
        }

        for (landmark in drawingData.landmarks) {
            drawCircle(
                color = landmark.color,
                radius = pointRadiusPx,
                center = landmark.center,
            )
        }
    }
}

private fun buildDrawingData(
    poseFrame: PoseFrame?,
    previewWidth: Int,
    previewHeight: Int,
    overlayAlpha: Float,
): DrawingData {
    if (poseFrame == null || previewWidth <= 0 || previewHeight <= 0) {
        return DrawingData(emptyList(), emptyList())
    }

    val sourceSize = inferSourceSize(poseFrame)
    val contentScale = minOf(
        previewWidth / sourceSize.width,
        previewHeight / sourceSize.height,
    )
    val horizontalInset = ((previewWidth - (sourceSize.width * contentScale)) / 2f).coerceAtLeast(0f)
    val verticalInset = ((previewHeight - (sourceSize.height * contentScale)) / 2f).coerceAtLeast(0f)

    val mappedCenters = Array<Offset?>(poseFrame.landmarks.size) { index ->
        val landmark = poseFrame.landmarks[index]
        if (landmark.visibility < MIN_DRAW_VISIBILITY) {
            null
        } else {
            Offset(
                x = horizontalInset + (landmark.x * contentScale),
                y = verticalInset + (landmark.y * contentScale),
            )
        }
    }

    val landmarkDrawables = ArrayList<LandmarkDrawable>(poseFrame.landmarks.size)
    poseFrame.landmarks.forEachIndexed { index, landmark ->
        val center = mappedCenters.getOrNull(index) ?: return@forEachIndexed
        landmarkDrawables += LandmarkDrawable(
            center = center,
            color = landmarkColor(index, landmark.visibility, overlayAlpha),
        )
    }

    val connectionDrawables = ArrayList<ConnectionDrawable>(SKELETON_CONNECTIONS.size)
    for ((startIndex, endIndex) in SKELETON_CONNECTIONS) {
        val startLandmark = poseFrame.landmarks.getOrNull(startIndex) ?: continue
        val endLandmark = poseFrame.landmarks.getOrNull(endIndex) ?: continue
        if (startLandmark.visibility < MIN_DRAW_VISIBILITY || endLandmark.visibility < MIN_DRAW_VISIBILITY) {
            continue
        }

        val start = mappedCenters[startIndex] ?: continue
        val end = mappedCenters[endIndex] ?: continue
        val color = connectionColor(startIndex, endIndex, startLandmark.visibility, endLandmark.visibility, overlayAlpha)
        connectionDrawables += ConnectionDrawable(
            start = start,
            end = end,
            color = color,
        )
    }

    return DrawingData(
        landmarks = landmarkDrawables,
        connections = connectionDrawables,
    )
}

private fun inferSourceSize(poseFrame: PoseFrame): SourceSize {
    val maxX = poseFrame.landmarks.maxOfOrNull { it.x } ?: MODEL_REFERENCE_SIZE
    val maxY = poseFrame.landmarks.maxOfOrNull { it.y } ?: MODEL_REFERENCE_SIZE

    return when {
        maxX <= 1.5f && maxY <= 1.5f -> SourceSize(1f, 1f)
        maxX <= MODEL_REFERENCE_SIZE * 1.25f && maxY <= MODEL_REFERENCE_SIZE * 1.25f ->
            SourceSize(MODEL_REFERENCE_SIZE, MODEL_REFERENCE_SIZE)
        else -> SourceSize(
            width = max(maxX, 1f),
            height = max(maxY, 1f),
        )
    }
}

private fun landmarkColor(
    landmarkIndex: Int,
    visibility: Float,
    overlayAlpha: Float,
): Color {
    val isLowVisibility = visibility < LOW_VISIBILITY_THRESHOLD
    val baseColor = when {
        landmarkIndex in FOOT_LANDMARKS -> FootYellow
        landmarkIndex in LEFT_LANDMARKS -> LeftGreen
        landmarkIndex in RIGHT_LANDMARKS -> RightBlue
        else -> NeutralGray
    }
    return if (isLowVisibility) {
        NeutralGray.copy(alpha = LOW_VISIBILITY_ALPHA * overlayAlpha)
    } else {
        baseColor.copy(alpha = overlayAlpha)
    }
}

private fun connectionColor(
    startIndex: Int,
    endIndex: Int,
    startVisibility: Float,
    endVisibility: Float,
    overlayAlpha: Float,
): Color {
    val lowVisibility = startVisibility < LOW_VISIBILITY_THRESHOLD || endVisibility < LOW_VISIBILITY_THRESHOLD
    return when {
        lowVisibility -> NeutralGray.copy(alpha = LOW_VISIBILITY_ALPHA * overlayAlpha)
        startIndex in FOOT_LANDMARKS || endIndex in FOOT_LANDMARKS -> FootYellow.copy(alpha = overlayAlpha)
        startIndex in LEFT_LANDMARKS && endIndex in LEFT_LANDMARKS -> LeftGreen.copy(alpha = overlayAlpha)
        startIndex in RIGHT_LANDMARKS && endIndex in RIGHT_LANDMARKS -> RightBlue.copy(alpha = overlayAlpha)
        else -> NeutralGray.copy(alpha = 0.7f * overlayAlpha)
    }
}

private data class DrawingData(
    val landmarks: List<LandmarkDrawable>,
    val connections: List<ConnectionDrawable>,
)

private data class LandmarkDrawable(
    val center: Offset,
    val color: Color,
)

private data class ConnectionDrawable(
    val start: Offset,
    val end: Offset,
    val color: Color,
)

private data class SourceSize(
    val width: Float,
    val height: Float,
)

private val LEFT_LANDMARKS = setOf(
    MediaPipeLandmarks.LEFT_SHOULDER,
    MediaPipeLandmarks.LEFT_HIP,
    MediaPipeLandmarks.LEFT_KNEE,
    MediaPipeLandmarks.LEFT_ANKLE,
    MediaPipeLandmarks.LEFT_HEEL,
    MediaPipeLandmarks.LEFT_FOOT_INDEX,
)

private val RIGHT_LANDMARKS = setOf(
    MediaPipeLandmarks.RIGHT_SHOULDER,
    MediaPipeLandmarks.RIGHT_HIP,
    MediaPipeLandmarks.RIGHT_KNEE,
    MediaPipeLandmarks.RIGHT_ANKLE,
    MediaPipeLandmarks.RIGHT_HEEL,
    MediaPipeLandmarks.RIGHT_FOOT_INDEX,
)

private val FOOT_LANDMARKS = setOf(
    MediaPipeLandmarks.LEFT_ANKLE,
    MediaPipeLandmarks.RIGHT_ANKLE,
    MediaPipeLandmarks.LEFT_HEEL,
    MediaPipeLandmarks.RIGHT_HEEL,
    MediaPipeLandmarks.LEFT_FOOT_INDEX,
    MediaPipeLandmarks.RIGHT_FOOT_INDEX,
)

private val SKELETON_CONNECTIONS = arrayOf(
    MediaPipeLandmarks.LEFT_SHOULDER to MediaPipeLandmarks.LEFT_HIP,
    MediaPipeLandmarks.RIGHT_SHOULDER to MediaPipeLandmarks.RIGHT_HIP,
    MediaPipeLandmarks.LEFT_HIP to MediaPipeLandmarks.LEFT_KNEE,
    MediaPipeLandmarks.RIGHT_HIP to MediaPipeLandmarks.RIGHT_KNEE,
    MediaPipeLandmarks.LEFT_KNEE to MediaPipeLandmarks.LEFT_ANKLE,
    MediaPipeLandmarks.RIGHT_KNEE to MediaPipeLandmarks.RIGHT_ANKLE,
    MediaPipeLandmarks.LEFT_ANKLE to MediaPipeLandmarks.LEFT_HEEL,
    MediaPipeLandmarks.RIGHT_ANKLE to MediaPipeLandmarks.RIGHT_HEEL,
    MediaPipeLandmarks.LEFT_ANKLE to MediaPipeLandmarks.LEFT_FOOT_INDEX,
    MediaPipeLandmarks.RIGHT_ANKLE to MediaPipeLandmarks.RIGHT_FOOT_INDEX,
)

private val LeftGreen = Color(0xFF4E8E66)
private val RightBlue = Color(0xFF4B7AA5)
private val FootYellow = Color(0xFFF2C14E)
private val NeutralGray = Color(0xFFB4B7BC)

private const val MODEL_REFERENCE_SIZE = 256f
private const val MIN_DRAW_VISIBILITY = 0.3f
private const val LOW_VISIBILITY_THRESHOLD = 0.5f
private const val LOW_VISIBILITY_ALPHA = 0.35f

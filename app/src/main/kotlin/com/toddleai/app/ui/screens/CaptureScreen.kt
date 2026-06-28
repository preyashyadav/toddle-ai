package com.toddleai.app.ui.screens

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.toddleai.app.PoseModelStatus
import com.toddleai.app.ToddleAISessionViewModel
import com.toddleai.app.capture.CameraManager
import com.toddleai.app.ui.components.GuidanceOverlay
import com.toddleai.app.ui.components.SkeletonOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(
    sessionViewModel: ToddleAISessionViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember(context) { CameraManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val currentPose by sessionViewModel.currentPose.collectAsState()
    val currentQuality by sessionViewModel.currentQuality.collectAsState()
    val guidance by sessionViewModel.guidance.collectAsState()
    val inferenceTimeMs by sessionViewModel.inferenceTimeMs.collectAsState()
    val poseModelStatus by sessionViewModel.poseModelStatus.collectAsState()

    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var showHint by rememberSaveable { mutableStateOf(false) }
    var recordingStartedAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var recordingDurationSeconds by rememberSaveable { mutableStateOf(0f) }

    LaunchedEffect(isRecording, guidance.overallQuality, recordingStartedAtMs) {
        if (!isRecording) {
            showHint = false
            recordingDurationSeconds = 0f
            return@LaunchedEffect
        }
        while (isRecording) {
            val elapsedSeconds = ((System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)) / 1000f
            recordingDurationSeconds = elapsedSeconds
            if (elapsedSeconds >= 15f) {
                isRecording = false
                onContinue()
                break
            }
            showHint = elapsedSeconds >= 10f && guidance.overallQuality == com.toddleai.app.analysis.QualityLevel.POOR
            delay(300L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraManager.stopCamera()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val previewView = remember {
            PreviewView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // TextureView is more reliable than SurfaceView when layered under Compose overlays.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

        LaunchedEffect(lifecycleOwner, previewView) {
            previewView.post {
                cameraManager.startCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    onFrame = { imageProxy ->
                        sessionViewModel.processFrame(imageProxy)
                    },
                )
            }
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .align(Alignment.Center),
            update = {
                previewSize = androidx.compose.ui.unit.IntSize(
                    width = it.width.takeIf { width -> width > 0 } ?: constraints.maxWidth,
                    height = it.height.takeIf { height -> height > 0 } ?: constraints.maxHeight,
                )
            },
        )

        SkeletonOverlay(
            poseFrame = currentPose,
            frameQuality = currentQuality,
            previewWidth = previewSize.width,
            previewHeight = previewSize.height,
            modifier = Modifier.matchParentSize(),
        )

        GuidanceOverlay(
            guidanceState = if (poseModelStatus is PoseModelStatus.Error) {
                guidance.copy(message = (poseModelStatus as PoseModelStatus.Error).message)
            } else if (!isRecording) {
                guidance.copy(message = "Position your phone at knee height, to the side of where your child will walk")
            } else {
                guidance
            },
            recentQualities = sessionViewModel.recentQualities(),
            recordingDuration = recordingDurationSeconds,
            modifier = Modifier.matchParentSize(),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                .align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Cancel capture",
                tint = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showHint) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "Having trouble? Make sure both feet are visible and the child is walking left to right.",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = when (poseModelStatus) {
                        is PoseModelStatus.Ready -> "Pose model: ${sessionViewModel.poseModelLabel()} • ${String.format("%.1f", inferenceTimeMs)} ms"
                        is PoseModelStatus.Error -> sessionViewModel.poseModelLabel()
                        PoseModelStatus.Loading -> "Loading pose model…"
                    },
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            FloatingActionButton(
                onClick = {
                    if (!isRecording) {
                        sessionViewModel.beginCaptureSession()
                        isRecording = true
                        recordingStartedAtMs = System.currentTimeMillis()
                    } else {
                        isRecording = false
                        coroutineScope.launch { onContinue() }
                    }
                },
                containerColor = if (isRecording || guidance.isRecordingAcceptable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(76.dp),
            ) {
                Text(
                    text = if (isRecording) "Stop" else "Rec",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

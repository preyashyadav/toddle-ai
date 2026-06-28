package com.toddleai.app.data.models

data class Landmark(
    val x: Float,
    val y: Float,
    val visibility: Float,
)

data class PoseFrame(
    val frameIndex: Int,
    val timestamp: Long,
    val landmarks: List<Landmark>,
)

object MediaPipeLandmarks {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32
}

fun PoseFrame.leftShoulder(): Landmark = landmarks[MediaPipeLandmarks.LEFT_SHOULDER]

fun PoseFrame.rightShoulder(): Landmark = landmarks[MediaPipeLandmarks.RIGHT_SHOULDER]

fun PoseFrame.leftHip(): Landmark = landmarks[MediaPipeLandmarks.LEFT_HIP]

fun PoseFrame.rightHip(): Landmark = landmarks[MediaPipeLandmarks.RIGHT_HIP]

fun PoseFrame.leftKnee(): Landmark = landmarks[MediaPipeLandmarks.LEFT_KNEE]

fun PoseFrame.rightKnee(): Landmark = landmarks[MediaPipeLandmarks.RIGHT_KNEE]

fun PoseFrame.leftAnkle(): Landmark = landmarks[MediaPipeLandmarks.LEFT_ANKLE]

fun PoseFrame.rightAnkle(): Landmark = landmarks[MediaPipeLandmarks.RIGHT_ANKLE]

fun PoseFrame.leftHeel(): Landmark = landmarks[MediaPipeLandmarks.LEFT_HEEL]

fun PoseFrame.rightHeel(): Landmark = landmarks[MediaPipeLandmarks.RIGHT_HEEL]

fun PoseFrame.leftFootIndex(): Landmark = landmarks[MediaPipeLandmarks.LEFT_FOOT_INDEX]

fun PoseFrame.rightFootIndex(): Landmark = landmarks[MediaPipeLandmarks.RIGHT_FOOT_INDEX]

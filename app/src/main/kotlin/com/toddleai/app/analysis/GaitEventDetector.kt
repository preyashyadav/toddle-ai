package com.toddleai.app.analysis

import com.toddleai.app.data.models.GaitEvent
import com.toddleai.app.data.models.Landmark
import com.toddleai.app.data.models.PoseFrame
import com.toddleai.app.data.models.Side
import com.toddleai.app.data.models.leftHeel
import com.toddleai.app.data.models.leftHip
import com.toddleai.app.data.models.rightHeel
import com.toddleai.app.data.models.rightHip
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class GaitEventDetector {

    fun detectEvents(frames: List<PoseFrame>, fps: Float): List<GaitEvent> {
        if (frames.isEmpty() || fps <= 0f) return emptyList()

        // Walking direction from the robust (median) slope of hip-center x over time, so a heel strike
        // is always the *forward* extremum of the foot trajectory regardless of which way the child
        // walks across the frame (engine spec §10.3–10.4).
        val direction = estimateDirectionSign(frames)

        val minPeakDistance = max(1, ceil(0.3f * fps).toInt())
        val leftEvents = detectSideEvents(frames, fps, Side.LEFT, minPeakDistance, direction)
        val rightEvents = detectSideEvents(frames, fps, Side.RIGHT, minPeakDistance, direction)

        return (leftEvents + rightEvents)
            .sortedBy { it.frameIndex }
            .filterPhysiologicStepTimes()
    }

    private fun estimateDirectionSign(frames: List<PoseFrame>): Float {
        val deltas = ArrayList<Float>(frames.size)
        var previousHipX: Float? = null
        for (frame in frames) {
            val leftHip = frame.leftHip()
            val rightHip = frame.rightHip()
            if (leftHip.visibility < MIN_HIP_VISIBILITY || rightHip.visibility < MIN_HIP_VISIBILITY) {
                previousHipX = null
                continue
            }
            val hipCenterX = (leftHip.x + rightHip.x) / 2f
            previousHipX?.let { deltas += hipCenterX - it }
            previousHipX = hipCenterX
        }
        if (deltas.isEmpty()) return 1f
        deltas.sort()
        val medianDelta = deltas[deltas.size / 2]
        return if (medianDelta < 0f) -1f else 1f
    }

    private fun detectSideEvents(
        frames: List<PoseFrame>,
        fps: Float,
        side: Side,
        minPeakDistance: Int,
        direction: Float,
    ): List<GaitEvent> {
        val rawSignal = DoubleArray(frames.size) { index ->
            val frame = frames[index]
            val heel = frame.heelFor(side)
            if (heel.visibility < MIN_HEEL_VISIBILITY) {
                Double.NaN
            } else {
                val sacrumX = (frame.leftHip().x + frame.rightHip().x) / 2f
                (direction * (heel.x - sacrumX)).toDouble()
            }
        }

        val interpolated = interpolateNaNs(rawSignal, MAX_INTERPOLATION_GAP)
        val smoothed = movingAverage(interpolated, MOVING_AVERAGE_WINDOW)

        val events = mutableListOf<GaitEvent>()
        var segmentStart = 0
        while (segmentStart < smoothed.size) {
            while (segmentStart < smoothed.size && smoothed[segmentStart].isNaN()) {
                segmentStart++
            }
            if (segmentStart >= smoothed.size) break

            var segmentEnd = segmentStart
            while (segmentEnd < smoothed.size && !smoothed[segmentEnd].isNaN()) {
                segmentEnd++
            }

            val segment = smoothed.copyOfRange(segmentStart, segmentEnd)
            val amplitude = segment.maxOrNull()!! - segment.minOrNull()!!
            val minProminence = amplitude * MIN_PROMINENCE_RATIO

            if (segment.size >= 3 && minProminence > 0.0) {
                val peaks = findPeaks(
                    signal = segment,
                    minDistance = minPeakDistance,
                    minProminence = minProminence,
                )
                peaks.forEach { localIndex ->
                    val frameIndex = segmentStart + localIndex
                    val frame = frames[frameIndex]
                    val heel = frame.heelFor(side)
                    events += GaitEvent(
                        frameIndex = frame.frameIndex,
                        timeSeconds = frame.timestamp / 1000f,
                        side = side,
                        confidence = heel.visibility,
                    )
                }
            }

            segmentStart = segmentEnd
        }

        return events
    }

    private fun List<GaitEvent>.filterPhysiologicStepTimes(): List<GaitEvent> {
        if (size < 2) return this

        val filtered = mutableListOf(first())
        for (event in drop(1)) {
            val last = filtered.last()
            val delta = event.timeSeconds - last.timeSeconds
            if (delta in MIN_STEP_TIME_SECONDS..MAX_STEP_TIME_SECONDS) {
                filtered += event
            }
        }
        return filtered
    }

    private fun movingAverage(signal: DoubleArray, window: Int): DoubleArray {
        val radius = window / 2
        return DoubleArray(signal.size) { index ->
            if (signal[index].isNaN()) {
                Double.NaN
            } else {
                var sum = 0.0
                var count = 0
                val start = max(0, index - radius)
                val end = min(signal.lastIndex, index + radius)
                for (sampleIndex in start..end) {
                    val value = signal[sampleIndex]
                    if (!value.isNaN()) {
                        sum += value
                        count++
                    }
                }
                if (count == 0) Double.NaN else sum / count
            }
        }
    }

    private fun findPeaks(
        signal: DoubleArray,
        minDistance: Int,
        minProminence: Double,
    ): List<Int> {
        if (signal.size < 3) return emptyList()

        data class Candidate(val index: Int, val value: Double)

        val candidates = mutableListOf<Candidate>()
        for (index in 1 until signal.lastIndex) {
            val current = signal[index]
            if (current.isNaN()) continue

            val previous = signal[index - 1]
            val next = signal[index + 1]
            if (previous.isNaN() || next.isNaN()) continue

            val isLocalMaximum = current > previous && current >= next
            if (!isLocalMaximum) continue

            val leftMin = signal.sliceArray(0..index).minOrNull() ?: continue
            val rightMin = signal.sliceArray(index..signal.lastIndex).minOrNull() ?: continue
            val prominence = current - max(leftMin, rightMin)
            if (prominence >= minProminence) {
                candidates += Candidate(index = index, value = current)
            }
        }

        return candidates
            .sortedByDescending { it.value }
            .fold(mutableListOf<Candidate>()) { selected, candidate ->
                val tooClose = selected.any { abs(it.index - candidate.index) < minDistance }
                if (!tooClose) {
                    selected += candidate
                }
                selected
            }
            .sortedBy { it.index }
            .map { it.index }
    }

    private fun interpolateNaNs(signal: DoubleArray, maxGap: Int): DoubleArray {
        val interpolated = signal.copyOf()
        var index = 0
        while (index < interpolated.size) {
            if (!interpolated[index].isNaN()) {
                index++
                continue
            }

            val gapStart = index
            while (index < interpolated.size && interpolated[index].isNaN()) {
                index++
            }
            val gapEnd = index - 1
            val gapLength = gapEnd - gapStart + 1
            val leftIndex = gapStart - 1
            val rightIndex = index

            val canInterpolate = gapLength <= maxGap &&
                leftIndex >= 0 &&
                rightIndex < interpolated.size &&
                !interpolated[leftIndex].isNaN() &&
                !interpolated[rightIndex].isNaN()

            if (canInterpolate) {
                val leftValue = interpolated[leftIndex]
                val rightValue = interpolated[rightIndex]
                for (gapIndex in gapStart..gapEnd) {
                    val ratio = (gapIndex - leftIndex).toDouble() / (rightIndex - leftIndex).toDouble()
                    interpolated[gapIndex] = leftValue + ((rightValue - leftValue) * ratio)
                }
            }
        }
        return interpolated
    }

    private fun PoseFrame.heelFor(side: Side): Landmark = when (side) {
        Side.LEFT -> leftHeel()
        Side.RIGHT -> rightHeel()
    }

    private companion object {
        const val MOVING_AVERAGE_WINDOW = 5
        const val MAX_INTERPOLATION_GAP = 5
        const val MIN_HEEL_VISIBILITY = 0.5f
        const val MIN_HIP_VISIBILITY = 0.5f
        const val MIN_PROMINENCE_RATIO = 0.15
        const val MIN_STEP_TIME_SECONDS = 0.25f
        const val MAX_STEP_TIME_SECONDS = 1.5f
    }
}

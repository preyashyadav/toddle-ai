package com.toddleai.app.analysis

import com.toddleai.app.data.models.GaitEvent
import com.toddleai.app.data.models.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricComputerTest {

    private val metricComputer = MetricComputer()

    @Test
    fun symmetricInputProducesSymmetryRatioNearOne() {
        val metrics = metricComputer.computeMetrics(
            events = alternatingEvents(
                times = listOf(0.0f, 0.5f, 1.0f, 1.5f, 2.0f),
                sides = listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT),
            ),
        )

        assertEquals(1.0f, metrics.symmetryRatio, 0.0001f)
        assertEquals(0f, metrics.timingDifferenceMs, 0.0001f)
    }

    @Test
    fun asymmetricInputProducesCorrectTimingDifference() {
        val metrics = metricComputer.computeMetrics(
            events = alternatingEvents(
                times = listOf(0.0f, 0.4f, 1.0f, 1.3f, 2.0f),
                sides = listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT),
            ),
        )

        // Steps (alternating): R-ending = [0.4, 0.3] -> mean 0.35; L-ending = [0.6, 0.7] -> mean 0.65.
        assertEquals(0.65f, metrics.leftMeanStepTime, 0.0001f)
        assertEquals(0.35f, metrics.rightMeanStepTime, 0.0001f)
        assertEquals(300f, metrics.timingDifferenceMs, 0.0001f)
    }

    @Test
    fun cadenceComputationIsCorrect() {
        val metrics = metricComputer.computeMetrics(
            events = alternatingEvents(
                times = listOf(0.0f, 0.5f, 1.0f, 1.5f),
                sides = listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT),
            ),
        )

        assertEquals(0.5f, metrics.meanStepTime, 0.0001f)
        assertEquals(120f, metrics.cadence, 0.0001f)
    }

    @Test
    fun edgeCasesDoNotCrash() {
        val noData = metricComputer.computeMetrics(emptyList())
        val oneSideOnly = metricComputer.computeMetrics(
            alternatingEvents(
                times = listOf(0.0f, 0.6f, 1.2f),
                sides = listOf(Side.LEFT, Side.LEFT, Side.LEFT),
            ),
        )
        val shortSequence = metricComputer.computeMetrics(
            alternatingEvents(
                times = listOf(0.0f, 0.5f),
                sides = listOf(Side.LEFT, Side.RIGHT),
            ),
        )

        assertEquals(0, noData.usableStepCount)
        assertEquals(0, oneSideOnly.usableStepCount)
        assertEquals(1, shortSequence.usableStepCount)
        assertTrue(shortSequence.stepTimes.isNotEmpty())
    }

    private fun alternatingEvents(
        times: List<Float>,
        sides: List<Side>,
    ): List<GaitEvent> {
        return times.mapIndexed { index, time ->
            GaitEvent(
                frameIndex = index * 15,
                timeSeconds = time,
                side = sides[index],
                confidence = 0.9f,
            )
        }
    }
}

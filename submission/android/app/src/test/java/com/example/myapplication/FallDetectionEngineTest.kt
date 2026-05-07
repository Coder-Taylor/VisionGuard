package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallDetectionEngineTest {
    private var nowMillis = 0L
    private val detector = FallDetectionEngine(
        config = FallDetectionConfig.DEFAULT,
        nowMillis = { nowMillis },
    )

    @Test
    fun `process stays idle during normal motion`() {
        val outcome = detector.process(packet(0.0, 0.0, 9.8))

        assertEquals(FallDetectionState.IDLE, outcome.state)
        assertFalse(outcome.shouldTriggerEmergency)
    }

    @Test
    fun `process enters detecting on free fall candidate`() {
        val outcome = detector.process(packet(0.1, 0.2, 0.1))

        assertEquals(FallDetectionState.DETECTING, outcome.state)
        assertFalse(outcome.shouldTriggerEmergency)
    }

    @Test
    fun `process confirms fall when impact follows free fall in window`() {
        detector.process(packet(0.1, 0.2, 0.1))
        nowMillis = 250L

        val outcome = detector.process(packet(22.0, 0.0, 0.0))

        assertEquals(FallDetectionState.FALL_CONFIRMED, outcome.state)
        assertTrue(outcome.shouldTriggerEmergency)
    }

    @Test
    fun `process ignores impact after detection window expires`() {
        detector.process(packet(0.1, 0.2, 0.1))
        nowMillis = 2_000L

        val outcome = detector.process(packet(22.0, 0.0, 0.0))

        assertEquals(FallDetectionState.IDLE, outcome.state)
        assertFalse(outcome.shouldTriggerEmergency)
    }

    @Test
    fun `process suppresses duplicate triggers during cooldown and rearms after cooldown`() {
        detector.process(packet(0.1, 0.2, 0.1))
        nowMillis = 250L
        val firstTrigger = detector.process(packet(22.0, 0.0, 0.0))
        nowMillis = 500L
        val cooldownOutcome = detector.process(packet(25.0, 0.0, 0.0))
        nowMillis = 10_500L
        val idleOutcome = detector.process(packet(0.0, 0.0, 9.8))
        nowMillis = 10_700L
        detector.process(packet(0.1, 0.2, 0.1))
        nowMillis = 10_900L
        val secondTrigger = detector.process(packet(22.0, 0.0, 0.0))

        assertTrue(firstTrigger.shouldTriggerEmergency)
        assertEquals(FallDetectionState.COOLDOWN, cooldownOutcome.state)
        assertFalse(cooldownOutcome.shouldTriggerEmergency)
        assertEquals(FallDetectionState.IDLE, idleOutcome.state)
        assertTrue(secondTrigger.shouldTriggerEmergency)
    }

    private fun packet(ax: Double, ay: Double, az: Double): SensorPacket {
        return SensorPacket(
            radarDist = 0,
            imu = ImuReading(ax = ax, ay = ay, az = az),
            btnA = 0,
            btnB = 0,
        )
    }
}

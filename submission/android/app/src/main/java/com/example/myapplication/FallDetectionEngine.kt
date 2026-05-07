package com.example.myapplication

import kotlin.math.sqrt

class FallDetectionEngine(
    private val config: FallDetectionConfig = FallDetectionConfig.DEFAULT,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var state = FallDetectionState.IDLE
    private var detectionStartedAtMillis = 0L
    private var cooldownEndsAtMillis = 0L

    fun process(packet: SensorPacket): FallDetectionOutcome {
        val now = nowMillis()
        val magnitude = accelerationMagnitude(packet)

        if (state == FallDetectionState.COOLDOWN) {
            if (now < cooldownEndsAtMillis) {
                return FallDetectionOutcome(
                    state = FallDetectionState.COOLDOWN,
                    shouldTriggerEmergency = false,
                )
            }
            state = FallDetectionState.IDLE
        }

        if (state == FallDetectionState.DETECTING) {
            if (now - detectionStartedAtMillis > config.impactWindowMillis) {
                state = FallDetectionState.IDLE
            } else if (magnitude >= config.impactThreshold) {
                state = FallDetectionState.COOLDOWN
                cooldownEndsAtMillis = now + config.cooldownMillis
                return FallDetectionOutcome(
                    state = FallDetectionState.FALL_CONFIRMED,
                    shouldTriggerEmergency = true,
                )
            } else {
                return FallDetectionOutcome(
                    state = FallDetectionState.DETECTING,
                    shouldTriggerEmergency = false,
                )
            }
        }

        if (magnitude <= config.freeFallThreshold) {
            state = FallDetectionState.DETECTING
            detectionStartedAtMillis = now
            return FallDetectionOutcome(
                state = FallDetectionState.DETECTING,
                shouldTriggerEmergency = false,
            )
        }

        state = FallDetectionState.IDLE
        return FallDetectionOutcome(
            state = FallDetectionState.IDLE,
            shouldTriggerEmergency = false,
        )
    }

    private fun accelerationMagnitude(packet: SensorPacket): Double {
        return sqrt(
            packet.imu.ax * packet.imu.ax +
                packet.imu.ay * packet.imu.ay +
                packet.imu.az * packet.imu.az,
        )
    }
}

data class FallDetectionOutcome(
    val state: FallDetectionState,
    val shouldTriggerEmergency: Boolean,
)

enum class FallDetectionState {
    IDLE,
    DETECTING,
    FALL_CONFIRMED,
    COOLDOWN,
}

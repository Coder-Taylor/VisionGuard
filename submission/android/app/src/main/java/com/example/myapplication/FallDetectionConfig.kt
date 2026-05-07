package com.example.myapplication

data class FallDetectionConfig(
    val freeFallThreshold: Double,
    val impactThreshold: Double,
    val impactWindowMillis: Long,
    val cooldownMillis: Long,
) {
    companion object {
        val DEFAULT = FallDetectionConfig(
            freeFallThreshold = 2.5,
            impactThreshold = 18.0,
            impactWindowMillis = 1_000L,
            cooldownMillis = 10_000L,
        )
    }
}

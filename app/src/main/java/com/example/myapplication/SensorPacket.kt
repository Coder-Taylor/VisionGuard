package com.example.myapplication

data class ImuReading(
    val ax: Double,
    val ay: Double,
    val az: Double,
)

data class SensorPacket(
    val radarDist: Int,
    val imu: ImuReading,
    val btnA: Int,
    val btnB: Int,
    val batteryPct: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

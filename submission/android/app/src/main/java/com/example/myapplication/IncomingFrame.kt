package com.example.myapplication

sealed interface IncomingFrame {
    data class Sensor(val packet: SensorPacket) : IncomingFrame

    data class Image(val jpegBytes: ByteArray) : IncomingFrame
}

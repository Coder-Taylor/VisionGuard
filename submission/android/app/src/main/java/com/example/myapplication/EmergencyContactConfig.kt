package com.example.myapplication

data class EmergencyContactConfig(
    val emergencyNumber: String,
) {
    companion object {
        val DEFAULT = EmergencyContactConfig(
            emergencyNumber = "112",
        )
    }
}

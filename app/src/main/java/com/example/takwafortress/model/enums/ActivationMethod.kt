package com.example.takwafortress.model.enums


enum class ActivationMethod(
    val displayName: String,
    val requiresLaptop: Boolean,
    val requiresEmailRemoval: Boolean
) {
    KNOX(
        displayName = "Samsung Knox",
        requiresLaptop = false,
        requiresEmailRemoval = false
    ),
    WIRELESS_ADB(
        displayName = "Wireless ADB",
        requiresLaptop = false,
        requiresEmailRemoval = true
    ),
    DESKTOP_ADB(
        displayName = "Desktop ADB (Legacy)",
        requiresLaptop = true,
        requiresEmailRemoval = false
    );

    fun getSetupInstructions(): String {
        return when (this) {
            KNOX -> "Tap 'Activate' and accept the system prompt"
            WIRELESS_ADB -> "Enter the pairing code shown on your screen"
            DESKTOP_ADB -> "Connect your phone to laptop via USB"
        }
    }
}


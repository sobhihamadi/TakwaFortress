package com.example.takwafortress.model.enums
enum class DeviceBrand(
    val displayName: String,
    val supportsKnox: Boolean,
    val setupTime: String
) {
    SAMSUNG(
        displayName = "Samsung",
        supportsKnox = true,
        setupTime = "10 seconds"
    ),
    XIAOMI(
        displayName = "Xiaomi",
        supportsKnox = false,
        setupTime = "2 minutes"
    ),
    INFINIX(
        displayName = "Infinix",
        supportsKnox = false,
        setupTime = "2 minutes"
    ),
    TECNO(
        displayName = "Tecno",
        supportsKnox = false,
        setupTime = "2 minutes"
    ),
    OTHER(
        displayName = "Other",
        supportsKnox = false,
        setupTime = "2 minutes"
    );

    fun getActivationMethod(): ActivationMethod {
        return if (supportsKnox) {
            ActivationMethod.KNOX
        } else {
            ActivationMethod.WIRELESS_ADB
        }
    }

    companion object {
        fun fromManufacturer(manufacturer: String): DeviceBrand {
            return when (manufacturer.uppercase()) {
                "SAMSUNG" -> SAMSUNG
                "XIAOMI", "REDMI", "POCO" -> XIAOMI
                "INFINIX" -> INFINIX
                "TECNO" -> TECNO
                else -> OTHER
            }
        }
    }
}

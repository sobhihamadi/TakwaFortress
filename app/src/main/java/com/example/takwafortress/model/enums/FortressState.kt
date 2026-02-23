package com.example.takwafortress.model.enums


enum class FortressState {
    INACTIVE,       // Device Owner not active
    ACTIVATING,     // Setup in progress
    ACTIVE,         // Fortress locked and running
    UNLOCKABLE,     // Period expired, can disable
    SUSPENDED;      // Temporary pause (future feature)

    fun isLocked(): Boolean {
        return this == ACTIVE || this == ACTIVATING
    }

    fun canUnlock(): Boolean {
        return this == UNLOCKABLE
    }

    fun canStartNewPeriod(): Boolean {
        return this == INACTIVE || this == UNLOCKABLE
    }

    fun getDisplayMessage(): String {
        return when (this) {
            INACTIVE -> "Fortress not activated"
            ACTIVATING -> "Activating fortress..."
            ACTIVE -> "Fortress is active and protecting you"
            UNLOCKABLE -> "Period complete! You can now unlock"
            SUSPENDED -> "Fortress temporarily suspended"
        }
    }
}
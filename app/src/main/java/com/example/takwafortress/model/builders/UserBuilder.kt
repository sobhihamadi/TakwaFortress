package com.example.takwafortress.model.builders

import com.example.takwafortress.model.entities.IdentifierUser
import com.example.takwafortress.model.entities.User
import com.example.takwafortress.model.enums.LicenseStatus
import com.example.takwafortress.model.enums.SubscriptionStatus
import com.example.takwafortress.model.interfaces.ID
import com.google.firebase.Timestamp

/**
 * Builder for creating User instances.
 *
 * This class follows the Builder Pattern and provides a fluent API
 * for constructing User objects with validation.
 */
class UserBuilder private constructor() {

    private var email: String? = null
    private var deviceId: String? = null
    private var subscriptionStatus: SubscriptionStatus = SubscriptionStatus.PENDING
    private var hasDeviceOwner: Boolean = false
    private var selectedPlan: String = ""
    private var commitmentEndDate: Timestamp? = null
    private var commitmentStartDate: Timestamp? = null
    private var commitmentDays: Int = 0
    private var createdAt: Timestamp = Timestamp.now()
    private var updatedAt: Timestamp = Timestamp.now()

    // Legacy fields
    private var licenseKey: String? = null
    private var licenseStatus: LicenseStatus? = null

    fun setEmail(email: String): UserBuilder {
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            "Invalid email format"
        }
        this.email = email
        return this
    }

    fun setDeviceId(deviceId: String): UserBuilder {
        require(deviceId.isNotBlank()) { "Device ID cannot be blank" }
        this.deviceId = deviceId
        return this
    }

    fun setSubscriptionStatus(status: SubscriptionStatus): UserBuilder {
        this.subscriptionStatus = status
        return this
    }

    fun setHasDeviceOwner(hasDeviceOwner: Boolean): UserBuilder {
        this.hasDeviceOwner = hasDeviceOwner
        return this
    }

    fun setSelectedPlan(plan: String): UserBuilder {
        this.selectedPlan = plan
        return this
    }

    fun setCommitmentEndDate(endDate: Timestamp?): UserBuilder {
        this.commitmentEndDate = endDate
        return this
    }

    fun setCommitmentStartDate(startDate: Timestamp?): UserBuilder {
        this.commitmentStartDate = startDate
        return this
    }

    fun setCommitmentDays(days: Int): UserBuilder {
        require(days >= 0) { "Commitment days cannot be negative" }
        this.commitmentDays = days
        return this
    }

    fun setCreatedAt(timestamp: Timestamp): UserBuilder {
        this.createdAt = timestamp
        return this
    }

    fun setUpdatedAt(timestamp: Timestamp): UserBuilder {
        this.updatedAt = timestamp
        return this
    }

    // Legacy setters
    fun setLicenseKey(licenseKey: String?): UserBuilder {
        this.licenseKey = licenseKey
        return this
    }

    fun setLicenseStatus(licenseStatus: LicenseStatus?): UserBuilder {
        this.licenseStatus = licenseStatus
        return this
    }

    /**
     * Builds and returns a User instance.
     * @throws IllegalArgumentException if required fields are missing
     */
    fun build(): User {
        requireNotNull(email) { "Email is required" }
        requireNotNull(deviceId) { "Device ID is required" }

        return User(
            email = email!!,
            deviceId = deviceId!!,
            subscriptionStatus = subscriptionStatus,
            hasDeviceOwner = hasDeviceOwner,
            selectedPlan = selectedPlan,
            commitmentEndDate = commitmentEndDate,
            commitmentStartDate = commitmentStartDate,
            commitmentDays = commitmentDays,
            createdAt = createdAt,
            updatedAt = updatedAt,

        )
    }

    companion object {
        fun newBuilder(): UserBuilder {
            return UserBuilder()
        }

        /**
         * Creates a builder with default values for a new user registration.
         */
        fun forNewUser(email: String, deviceId: String): UserBuilder {
            return newBuilder()
                .setEmail(email)
                .setDeviceId(deviceId)
                .setSubscriptionStatus(SubscriptionStatus.PENDING)
                .setHasDeviceOwner(false)
                .setCreatedAt(Timestamp.now())
                .setUpdatedAt(Timestamp.now())
        }
    }
}

/**
 * Builder for creating IdentifierUser instances.
 */
class IdentifierUserBuilder private constructor() {

    private var id: ID? = null
    private var user: User? = null

    fun setId(id: ID): IdentifierUserBuilder {
        require(id.isNotEmpty()) { "ID cannot be empty" }
        this.id = id
        return this
    }

    fun setUser(user: User): IdentifierUserBuilder {
        this.user = user
        return this
    }

    fun build(): IdentifierUser {
        requireNotNull(id) { "ID is required" }
        requireNotNull(user) { "User is required" }

        val u = user!!

        return IdentifierUser(
            id = id!!,
            user = User(

            email = u.email,
            deviceId = u.deviceId,
            subscriptionStatus = u.subscriptionStatus,
            hasDeviceOwner = u.hasDeviceOwner,
            selectedPlan = u.selectedPlan,
            commitmentEndDate = u.commitmentEndDate,
            commitmentStartDate = u.commitmentStartDate,
            commitmentDays = u.commitmentDays,
            createdAt = u.createdAt,
            updatedAt = u.updatedAt
        ))
    }

    companion object {
        fun newBuilder(): IdentifierUserBuilder {
            return IdentifierUserBuilder()
        }

        /**
         * Creates a builder from an existing user with a new ID.
         */
        fun fromUser(id: ID, user: User): IdentifierUserBuilder {
            return newBuilder()
                .setId(id)
                .setUser(user)
        }
    }
}
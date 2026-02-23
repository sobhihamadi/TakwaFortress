package com.example.takwafortress.mappers

import com.example.takwafortress.model.builders.IdentifierUserBuilder
import com.example.takwafortress.model.builders.UserBuilder
import com.example.takwafortress.model.entities.IdentifierUser
import com.example.takwafortress.model.enums.LicenseStatus
import com.example.takwafortress.model.enums.SubscriptionStatus
import com.google.firebase.Timestamp
import org.json.JSONObject

/**
 * Mapper for converting between User domain objects and JSON/Firestore representations.
 *
 * This class follows the Single Responsibility Principle (SRP) by handling
 * only data transformation between different representations.
 */
class UserMapper : IMapper<JSONObject, IdentifierUser> {

    /**
     * Maps JSON object to IdentifierUser domain object.
     *
     * @param data JSON object containing user data
     * @return IdentifierUser instance
     * @throws JSONException if required fields are missing or invalid
     */
    override fun map(data: JSONObject): IdentifierUser {
        val user = UserBuilder.newBuilder()
            .setEmail(data.getString("email"))
            .setDeviceId(data.getString("deviceId"))
            .setSubscriptionStatus(
                SubscriptionStatus.valueOf(data.getString("subscriptionStatus"))
            )
            .setHasDeviceOwner(data.getBoolean("hasDeviceOwner"))
            .setSelectedPlan(data.optString("selectedPlan", ""))
            .setCommitmentDays(data.optInt("commitmentDays", 0))
            .apply {
                // Handle optional timestamp fields
                if (data.has("commitmentEndDate") && !data.isNull("commitmentEndDate")) {
                    setCommitmentEndDate(parseTimestamp(data.get("commitmentEndDate")))
                }
                if (data.has("commitmentStartDate") && !data.isNull("commitmentStartDate")) {
                    setCommitmentStartDate(parseTimestamp(data.get("commitmentStartDate")))
                }
                if (data.has("createdAt")) {
                    setCreatedAt(parseTimestamp(data.get("createdAt")))
                }
                if (data.has("updatedAt")) {
                    setUpdatedAt(parseTimestamp(data.get("updatedAt")))
                }

                // Legacy fields (optional)
                if (data.has("licenseKey") && !data.isNull("licenseKey")) {
                    setLicenseKey(data.getString("licenseKey"))
                }
                if (data.has("licenseStatus") && !data.isNull("licenseStatus")) {
                    setLicenseStatus(LicenseStatus.valueOf(data.getString("licenseStatus")))
                }
            }
            .build()

        return IdentifierUserBuilder.newBuilder()
            .setId(data.getString("id"))
            .setUser(user)
            .build()
    }

    /**
     * Maps IdentifierUser domain object to JSON object.
     *
     * @param data IdentifierUser instance
     * @return JSON object representation
     */
    override fun reverseMap(data: IdentifierUser): JSONObject {
        return JSONObject().apply {
            put("id", data.getId())
            put("email", data.email)
            put("deviceId", data.deviceId)
            put("subscriptionStatus", data.subscriptionStatus.name)
            put("hasDeviceOwner", data.hasDeviceOwner)
            put("selectedPlan", data.selectedPlan)
            put("commitmentDays", data.commitmentDays)

            // Optional timestamp fields
            data.commitmentEndDate?.let {
                put("commitmentEndDate", timestampToJson(it))
            }
            data.commitmentStartDate?.let {
                put("commitmentStartDate", timestampToJson(it))
            }
            put("createdAt", timestampToJson(data.createdAt))
            put("updatedAt", timestampToJson(data.updatedAt))


        }
    }

    /**
     * Parses a timestamp from various formats (long, JSONObject, or Timestamp).
     */
    private fun parseTimestamp(value: Any): Timestamp {
        return when (value) {
            is Long -> Timestamp(value / 1000, ((value % 1000) * 1000000).toInt())
            is JSONObject -> {
                val seconds = value.getLong("seconds")
                val nanoseconds = value.optInt("nanoseconds", 0)
                Timestamp(seconds, nanoseconds)
            }
            is Timestamp -> value
            else -> throw IllegalArgumentException("Unsupported timestamp format: ${value::class.java}")
        }
    }

    /**
     * Converts Timestamp to JSON representation.
     */
    private fun timestampToJson(timestamp: Timestamp): JSONObject {
        return JSONObject().apply {
            put("seconds", timestamp.seconds)
            put("nanoseconds", timestamp.nanoseconds)
        }
    }
}

/**
 * Extension function to convert IdentifierUser to Firestore map.
 * This is useful when directly saving to Firestore.
 */
fun IdentifierUser.toFirestoreMap(): Map<String, Any?> {
    return hashMapOf(
        "email" to email,
        "deviceId" to deviceId,
        "subscriptionStatus" to subscriptionStatus,
        "hasDeviceOwner" to hasDeviceOwner,
        "selectedPlan" to selectedPlan,
        "commitmentDays" to commitmentDays,
        "commitmentEndDate" to commitmentEndDate,
        "commitmentStartDate" to commitmentStartDate,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,

    )
}

/**
 * Extension function to create IdentifierUser from Firestore map.
 */
fun Map<String, Any?>.toIdentifierUser(id: String): IdentifierUser {
    val user = UserBuilder.newBuilder()
        .setEmail(this["email"] as String)
        .setDeviceId(this["deviceId"] as String)
        .setSubscriptionStatus(
            SubscriptionStatus.valueOf(this["subscriptionStatus"] as String)
        )
        .setHasDeviceOwner(this["hasDeviceOwner"] as Boolean)
        .setSelectedPlan((this["selectedPlan"] as? String) ?: "")
        .setCommitmentDays((this["commitmentDays"] as? Long)?.toInt() ?: 0)
        .apply {
            (this@toIdentifierUser["commitmentEndDate"] as? Timestamp)?.let {
                setCommitmentEndDate(it)
            }
            (this@toIdentifierUser["commitmentStartDate"] as? Timestamp)?.let {
                setCommitmentStartDate(it)
            }
            (this@toIdentifierUser["createdAt"] as? Timestamp)?.let {
                setCreatedAt(it)
            }
            (this@toIdentifierUser["updatedAt"] as? Timestamp)?.let {
                setUpdatedAt(it)
            }
            (this@toIdentifierUser["licenseKey"] as? String)?.let {
                setLicenseKey(it)
            }
            (this@toIdentifierUser["licenseStatus"] as? String)?.let {
                setLicenseStatus(LicenseStatus.valueOf(it))
            }
        }
        .build()

    return IdentifierUserBuilder.fromUser(id, user).build()
}
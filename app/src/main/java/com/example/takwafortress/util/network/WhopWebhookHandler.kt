package com.example.takwafortress.util.network

import org.json.JSONObject

/**
 * Handles Whop payment webhooks.
 * Whop sends webhooks when a user purchases a license.
 */
object WhopWebhookHandler {

    /**
     * Parses Whop webhook payload.
     */
    fun parseWebhook(payload: String): WhopWebhookEvent? {
        return try {
            val json = JSONObject(payload)
            val eventType = json.getString("type")

            when (eventType) {
                "payment.succeeded" -> parsePaymentSucceeded(json)
                "payment.failed" -> parsePaymentFailed(json)
                "subscription.created" -> parseSubscriptionCreated(json)
                "subscription.cancelled" -> parseSubscriptionCancelled(json)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePaymentSucceeded(json: JSONObject): WhopWebhookEvent.PaymentSucceeded {
        val data = json.getJSONObject("data")

        return WhopWebhookEvent.PaymentSucceeded(
            transactionId = data.getString("id"),
            email = data.getString("email"),
            amount = data.getDouble("amount"),
            currency = data.getString("currency"),
            timestamp = data.getLong("created_at")
        )
    }

    private fun parsePaymentFailed(json: JSONObject): WhopWebhookEvent.PaymentFailed {
        val data = json.getJSONObject("data")

        return WhopWebhookEvent.PaymentFailed(
            email = data.getString("email"),
            reason = data.optString("failure_message", "Unknown"),
            timestamp = data.getLong("created_at")
        )
    }

    private fun parseSubscriptionCreated(json: JSONObject): WhopWebhookEvent.SubscriptionCreated {
        val data = json.getJSONObject("data")

        return WhopWebhookEvent.SubscriptionCreated(
            subscriptionId = data.getString("id"),
            email = data.getString("email"),
            plan = data.getString("plan"),
            timestamp = data.getLong("created_at")
        )
    }

    private fun parseSubscriptionCancelled(json: JSONObject): WhopWebhookEvent.SubscriptionCancelled {
        val data = json.getJSONObject("data")

        return WhopWebhookEvent.SubscriptionCancelled(
            subscriptionId = data.getString("id"),
            email = data.getString("email"),
            timestamp = data.getLong("created_at")
        )
    }

    /**
     * Generates a Taqwa license key from Whop transaction.
     */
    fun generateLicenseKey(transactionId: String): String {
        // Format: TAQWA-XXXX-XXXX-XXXX
        val hash = transactionId.hashCode().toString().takeLast(12)
        val part1 = hash.substring(0, 4)
        val part2 = hash.substring(4, 8)
        val part3 = hash.substring(8, 12)

        return "TAQWA-$part1-$part2-$part3".uppercase()
    }

    /**
     * Validates Whop webhook signature (for security).
     */
    fun validateSignature(payload: String, signature: String, secret: String): Boolean {
        // TODO: Implement HMAC signature validation
        // This prevents fake webhooks
        return true
    }
}

sealed class WhopWebhookEvent {
    data class PaymentSucceeded(
        val transactionId: String,
        val email: String,
        val amount: Double,
        val currency: String,
        val timestamp: Long
    ) : WhopWebhookEvent()

    data class PaymentFailed(
        val email: String,
        val reason: String,
        val timestamp: Long
    ) : WhopWebhookEvent()

    data class SubscriptionCreated(
        val subscriptionId: String,
        val email: String,
        val plan: String,
        val timestamp: Long
    ) : WhopWebhookEvent()

    data class SubscriptionCancelled(
        val subscriptionId: String,
        val email: String,
        val timestamp: Long
    ) : WhopWebhookEvent()
}
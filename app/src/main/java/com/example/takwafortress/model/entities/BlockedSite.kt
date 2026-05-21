package com.example.takwafortress.model.entities

/**
 * Represents a user-blocked website domain.
 * Stored as the bare hostname, e.g. "reddit.com".
 */
data class BlockedSite(
    val id: String,
    val domain: String,          // e.g. "reddit.com"
    val displayLabel: String,    // e.g. "reddit.com" or user-given alias
    val addedAt: Long = System.currentTimeMillis()
)
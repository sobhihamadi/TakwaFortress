package com.example.takwafortress.util.exceptions


/**
 * Base exception for all repository/storage errors.
 */
sealed class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Item not found in repository.
     */
    class ItemNotFound(itemType: String, id: String) : RepositoryException("$itemType with ID $id not found")

    /**
     * Storage write failed.
     */
    class WriteFailed(reason: String, cause: Throwable? = null) : RepositoryException("Write failed: $reason", cause)

    /**
     * Storage read failed.
     */
    class ReadFailed(reason: String, cause: Throwable? = null) : RepositoryException("Read failed: $reason", cause)

    /**
     * Data corruption detected.
     */
    class DataCorrupted(details: String) : RepositoryException("Data corrupted: $details")

    /**
     * Encryption/decryption failed.
     */
    class EncryptionFailed(cause: Throwable) : RepositoryException("Encryption failed", cause)

    /**
     * JSON parsing failed.
     */
    class JsonParsingFailed(json: String, cause: Throwable) : RepositoryException("Failed to parse JSON: $json", cause)

    /**
     * Invalid data format.
     */
    class InvalidDataFormat(details: String) : RepositoryException("Invalid data format: $details")

    /**
     * Storage full.
     */
    class StorageFull : RepositoryException("Device storage is full")
}
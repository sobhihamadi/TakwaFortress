package com.example.takwafortress.repository.interfaces

import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.model.interfaces.IIdentifiable


interface IRepository<T : IIdentifiable> {

    /**
     * Creates a new item in the repository.
     * @param item - The item to be created.
     * @returns The ID of the created item.
     * @throws RepositoryException if there is an error during creation.
     */
    suspend fun create(item: T): ID

    /**
     * Retrieves an item by its ID from the repository.
     * @param id - The ID to be found in the repository.
     * @returns The item if found.
     * @throws ItemNotFoundException if the item with the specified ID does not exist.
     * @throws RepositoryException if there is an error during retrieval.
     */
    suspend fun get(id: ID): T

    /**
     * Retrieves all items from the repository.
     * @returns A list of all items in the repository.
     * @throws RepositoryException if there is an error during retrieval.
     */
    suspend fun getAll(): List<T>

    /**
     * Updates an existing item in the repository.
     * @param item - The item with updated values.
     * @throws ItemNotFoundException if the item does not exist.
     * @throws RepositoryException if there is an error during update.
     */
    suspend fun update(item: T)

    /**
     * Deletes an item from the repository by its ID.
     * @param id - The unique identifier of the item to delete.
     * @throws ItemNotFoundException if the item with the specified ID does not exist.
     * @throws RepositoryException if there is an error during deletion.
     */
    suspend fun delete(id: ID)

    /**
     * Checks if an item with the given ID exists.
     * @param id - The ID to check.
     * @returns True if the item exists, false otherwise.
     */
    suspend fun exists(id: ID): Boolean
}
package com.example.takwafortress.repository.interfaces

import com.example.takwafortress.model.entities.IdentifierFortressPolicy
import com.example.takwafortress.model.enums.FortressState


interface IFortressPolicyRepository : IRepository<IdentifierFortressPolicy> {

    /**
     * Gets the currently active fortress policy.
     * @returns The active policy or null if no policy exists.
     */
    suspend fun getActivePolicy(): IdentifierFortressPolicy?

    /**
     * Sets the given policy as the active policy.
     */
    suspend fun setActivePolicy(policy: IdentifierFortressPolicy)

    /**
     * Checks if there is an active fortress policy.
     */
    suspend fun hasActivePolicy(): Boolean

    /**
     * Updates the fortress state of the active policy.
     * @param state - The new state.
     */
    suspend fun updateFortressState(state: FortressState)

    /**
     * Clears the active policy (after period expires and user unlocks).
     */
    suspend fun clearActivePolicy()

    /**
     * Gets all historical policies (for tracking user's commitment history).
     */
    suspend fun getHistoricalPolicies(): List<IdentifierFortressPolicy>
}
package com.example.takwafortress.model.interfaces

import com.example.takwafortress.model.enums.CommitmentPlan
import com.example.takwafortress.model.enums.FortressState


interface IFortress {
    fun getCommitmentPlan(): CommitmentPlan
    fun getFortressState(): FortressState
    fun isUnlockEligible(): Boolean
    fun getRemainingDays(): Int
    fun getProgressPercentage(): Float
}
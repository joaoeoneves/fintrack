package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.MotionPreference
import kotlinx.coroutines.flow.Flow

interface MotionRepository {
    fun observeMotionPreference(): Flow<MotionPreference>

    suspend fun setMotionPreference(preference: MotionPreference)
}

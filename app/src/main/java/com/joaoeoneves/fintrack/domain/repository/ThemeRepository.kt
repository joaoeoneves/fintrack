package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    fun observeThemePreference(): Flow<ThemePreference>

    suspend fun setThemePreference(preference: ThemePreference)
}

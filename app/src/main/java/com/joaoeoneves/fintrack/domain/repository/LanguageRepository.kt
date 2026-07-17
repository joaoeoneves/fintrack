package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow

interface LanguageRepository {
    fun observeLanguage(): Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)
}

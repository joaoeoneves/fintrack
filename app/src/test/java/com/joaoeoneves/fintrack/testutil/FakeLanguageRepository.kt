package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimal in-memory [LanguageRepository] fake shared by ViewModel tests. */
class FakeLanguageRepository(
    initial: AppLanguage = AppLanguage.SYSTEM,
) : LanguageRepository {
    private val languageFlow = MutableStateFlow(initial)

    /** Every language passed to [setLanguage], in order. */
    val setCalls = mutableListOf<AppLanguage>()

    override fun observeLanguage(): Flow<AppLanguage> = languageFlow

    override suspend fun setLanguage(language: AppLanguage) {
        setCalls += language
        languageFlow.value = language
    }

    fun currentLanguage(): AppLanguage = languageFlow.value

    /** Simulates an external change (e.g. a synced preference). */
    fun emit(language: AppLanguage) {
        languageFlow.value = language
    }
}

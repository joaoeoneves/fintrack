package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreTest : BackupRepositoryTestBase() {
    private val doc =
        backupDoc(
            expenses = listOf(backupExpense("e1", categoryId = "gym", note = "n").copy(isRefund = true)),
            income = listOf(backupIncome()),
            budgets = listOf(LocalBudget("gym", 100)),
            categories = listOf(backupCategory("gym")),
            preferences = BackupPreferences("LIGHT", "USD", "ENGLISH"),
        )

    @Test
    fun restore_notGuest_failsAndLeavesStoreUntouched() =
        runStoreTest {
            localStore.seedCategory("keep")
            sessions.emit(Session.SignedOut)

            val result = repository().restoreAsGuest(doc)

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(
                listOf("keep"),
                localStore.data
                    .first()
                    .categories
                    .map { it.id },
            )
            assertEquals(ThemePreference.DARK, prefsStore.data.first().theme)
        }

    @Test
    fun restore_guest_replacesStore() =
        runStoreTest {
            localStore.seedCategory("old")

            val result = repository().restoreAsGuest(doc)

            assertTrue(result.isSuccess)
            val stored = localStore.data.first()
            assertEquals(doc.expenses, stored.expenses)
            assertEquals(doc.income, stored.income)
            assertEquals(doc.budgets, stored.budgets)
            assertEquals(doc.categories, stored.categories)
        }

    @Test
    fun restore_guest_keepsTheReviewFlagsAndDismissals() =
        runStoreTest {
            val flagged =
                doc
                    .copy(
                        expenses = listOf(doc.expenses.single().copy(needsReview = true)),
                        income = listOf(doc.income.single().copy(needsReview = true)),
                    ).copy(dismissedRecurringSuggestions = listOf("netflix"))

            assertTrue(repository().restoreAsGuest(flagged).isSuccess)

            val stored = localStore.data.first()
            assertTrue(stored.expenses.single().needsReview)
            assertTrue(stored.income.single().needsReview)
            assertEquals(listOf("netflix"), stored.dismissedRecurringSuggestions)
        }

    @Test
    fun restore_guest_appliesPreferences() =
        runStoreTest {
            repository().restoreAsGuest(doc)

            val prefs = prefsStore.data.first()
            assertEquals(ThemePreference.LIGHT, prefs.theme)
            assertEquals(CurrencyOption.USD, prefs.currency)
            assertEquals(AppLanguage.ENGLISH, prefs.language)
        }

    @Test
    fun restore_unknownOrNullPreferenceNames_keepCurrent() =
        runStoreTest {
            prefsStore.update { it.copy(theme = ThemePreference.SYSTEM, currency = CurrencyOption.GBP) }
            val odd = doc.copy(preferences = BackupPreferences(theme = "NEON", currency = null, language = "KLINGON"))

            repository().restoreAsGuest(odd)

            val prefs = prefsStore.data.first()
            assertEquals(ThemePreference.SYSTEM, prefs.theme)
            assertEquals(CurrencyOption.GBP, prefs.currency)
            assertEquals(AppLanguage.SYSTEM, prefs.language)
        }

    @Test
    fun restore_nullPreferences_keepsPreferences() =
        runStoreTest {
            prefsStore.update { it.copy(theme = ThemePreference.LIGHT) }

            repository().restoreAsGuest(doc.copy(preferences = null))

            assertEquals(ThemePreference.LIGHT, prefsStore.data.first().theme)
        }

    @Test
    fun restore_emptyDocument_clearsStore() =
        runStoreTest {
            localStore.seedCategory("old")

            repository().restoreAsGuest(backupDoc())

            assertEquals(false, repository().localHasData())
        }
}

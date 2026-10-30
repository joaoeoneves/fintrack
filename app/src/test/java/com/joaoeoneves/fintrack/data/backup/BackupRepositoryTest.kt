package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.BuildConfig
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlaceholderNames
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupRepositoryTest : BackupRepositoryTestBase() {
    private val at = Instant.parse(EXPORTED_AT)
    private val march = Instant.parse("2026-03-10T12:30:45.123456789Z")

    private suspend fun BackupRepository.build() = buildBackup(at).getOrThrow()

    @Test
    fun export_mirrorsRepositories() =
        runStoreTest {
            val gym = userCategory("gym", order = 7)
            val repo =
                repository(
                    expenses = listOf(testExpense("e1", 500, march, category = gym)),
                    income = listOf(testIncome("i1", 9000, march)),
                    categoryDocs = listOf(gym),
                )

            val doc = repo.build()

            assertEquals(BACKUP_FORMAT, doc.format)
            assertEquals(BACKUP_SCHEMA_VERSION, doc.schemaVersion)
            assertEquals(EXPORTED_AT, doc.exportedAt)
            assertEquals(BuildConfig.VERSION_NAME, doc.appVersion)
            assertEquals(listOf("e1"), doc.expenses.map { it.id })
            assertEquals("2026-03-10T12:30:45.123456789Z", doc.expenses.single().date)
            assertEquals(listOf("i1"), doc.income.map { it.id })
            assertEquals(listOf("gym"), doc.categories.map { it.id })
            assertEquals(0, BackupCodec.decode(BackupCodec.encode(doc)).success().skippedCount)
        }

    @Test
    fun export_includesReviewFlagsAndSortedDismissals() =
        runStoreTest {
            val repo =
                repository(
                    expenses = listOf(testExpense("e1", 500, march).copy(needsReview = true)),
                    income = listOf(testIncome("i1", 9000, march).copy(needsReview = true)),
                    dismissed = setOf("netflix", "gym"),
                )

            val doc = repo.build()

            assertTrue(doc.expenses.single().needsReview)
            assertTrue(doc.income.single().needsReview)
            assertEquals(listOf("gym", "netflix"), doc.dismissedRecurringSuggestions)
        }

    @Test
    fun export_includesCurrentPreferences() =
        runStoreTest {
            prefsStore.update { it.copy(theme = ThemePreference.LIGHT) }

            val prefs = repository().build().preferences

            assertEquals(BackupPreferences("LIGHT", AppLanguage.SYSTEM.name), prefs)
        }

    @Test
    fun export_emptyData_validEmptyDocument() =
        runStoreTest {
            val doc = repository().build()

            assertEquals(BackupCounts(0, 0, 0, 0, 0, 0), doc.counts())
        }

    @Test
    fun export_excludesUntouchedBuiltIns() =
        runStoreTest {
            val doc = repository().build()

            assertTrue(doc.categories.isEmpty())
        }

    @Test
    fun export_includesChangedBuiltIn() =
        runStoreTest {
            val renamedFood = BuiltInCategories.FOOD.copy(customName = "Groceries", archived = true)

            val doc = repository(categoryDocs = listOf(renamedFood)).build()

            val food = doc.categories.single()
            assertEquals("FOOD", food.id)
            assertEquals("Groceries", food.name)
            assertTrue(food.archived)
        }

    @Test
    fun export_excludesUnknownPlaceholderCategories() =
        runStoreTest {
            val orphan = Category.unknown(CategoryId("ghost"))
            val repo = repository(categoryDocs = listOf(orphan))

            assertTrue(repo.build().categories.isEmpty())
        }

    @Test
    fun export_orphanPlaceholders_useTheProvidedDeletedCategoryName() =
        runStoreTest {
            val ghost = Category.unknown(CategoryId("ghost"))
            val repo =
                repository(
                    expenses = listOf(testExpense("e1", 100, march, category = ghost)),
                    deletedCategoryName = "Categoria apagada",
                )

            val doc = repo.build()

            assertEquals(listOf("Categoria apagada"), doc.categories.map { it.name })
        }

    @Test
    fun export_orphanExpenseAndRecurring_getArchivedPlaceholders() =
        runStoreTest {
            val gym = userCategory("gym", order = 7)
            val ghost = Category.unknown(CategoryId("ghost"))
            val repo =
                repository(
                    expenses = listOf(testExpense("e1", 100, march, category = ghost)),
                    recurring = listOf(recurringItem(categoryId = CategoryId("ghost2"))),
                    categoryDocs = listOf(gym),
                )

            val doc = repo.build()

            assertEquals(listOf("gym", "ghost", "ghost2"), doc.categories.map { it.id })
            val placeholders = doc.categories.drop(1)
            assertTrue(placeholders.all { it.archived && it.name == PLACEHOLDER_CATEGORY_NAME })
            assertEquals(listOf(8, 9), placeholders.map { it.order })
            val decoded = BackupCodec.decode(BackupCodec.encode(doc)).success()
            assertEquals(0, decoded.skippedCount)
            assertEquals(1, decoded.document.expenses.size)
            assertEquals(1, decoded.document.recurring.size)
        }

    @Test
    fun export_orphanWithInvalidId_getsNoPlaceholder() =
        runStoreTest {
            val bad = Category.unknown(CategoryId("bad id!"))
            val repo = repository(expenses = listOf(testExpense("e1", 100, march, category = bad)))

            assertTrue(repo.build().categories.isEmpty())
        }

    @Test
    fun export_repositoryFailure_isFailure() =
        runStoreTest {
            val repo =
                BackupRepository(
                    FakeExpenseRepository(),
                    FakeIncomeRepository(),
                    PlanBackupSource(
                        FakePlannedIncomeRepository(nextObserveError = IllegalStateException("boom")),
                        FakeRecurringRepository(),
                        FakeMonthSettingsRepository(),
                        FakeEnvelopeRepository(),
                        PlanSingletonSources(
                            FakeSalaryProfileRepository(),
                            FakeAutomationSettingsRepository(),
                            FakePlaceholderNames(),
                        ),
                    ),
                    FakeCategoryRepository(),
                    prefsStore,
                    restorer,
                )

            val result = repo.buildBackup(at)

            assertTrue(result.isFailure)
            assertEquals("boom", result.exceptionOrNull()?.message)
        }

    @Test
    fun localHasData_reflectsStore() =
        runStoreTest {
            val repo = repository()
            assertEquals(false, repo.localHasData())

            localStore.seedCategory("gym")

            assertEquals(true, repo.localHasData())
            assertEquals(
                1,
                localStore.data
                    .first()
                    .categories.size,
            )
        }
}

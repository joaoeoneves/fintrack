package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Backup schema v5: category groups, category group ids and budget skipped months. */
class BackupGroupsTest : BackupRepositoryTestBase() {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val food = LocalCategoryGroup("g-food", "Food", 0)

    @Test
    fun v4File_decodesToV5_withNoGroupsAndNoSkipMonths() {
        val v4 =
            """{$header,"schemaVersion":4,
            "categories":[{"id":"gym","name":"Gym","iconKey":"home","colorKey":"teal","kind":"SPENDING","order":10}],
            "budgets":[{"categoryId":"FOOD","limitCents":500}]}"""

        val result = BackupCodec.decode(v4).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(16, result.document.schemaVersion)
        assertTrue(result.document.categoryGroups.isEmpty())
        assertEquals(
            null,
            result.document.categories
                .single()
                .groupId,
        )
        assertEquals(
            emptyList<Int>(),
            result.document.budgets
                .single()
                .skipMonths,
        )
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v16File_isUnsupported() {
        assertEquals(
            BackupDecodeResult.UnsupportedVersion(17),
            BackupCodec.decode("""{$header,"schemaVersion":17}"""),
        )
    }

    @Test
    fun v5RoundTrip_keepsGroupsGroupIdsAndSkipMonths() {
        val doc =
            backupDoc(
                budgets = listOf(LocalBudget("FOOD", 16_000, listOf(8))),
                categories = listOf(backupCategory("gym").copy(groupId = "g-food")),
            ).copy(categoryGroups = listOf(food))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(1, result.document.counts().categoryGroups)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidGroupsAndSkipMonths_areSkippedAndCounted() {
        val doc =
            backupDoc(budgets = listOf(LocalBudget("FOOD", 100, listOf(13)), LocalBudget("SHOPPING", 100, listOf(1))))
                .copy(
                    categoryGroups =
                        listOf(food, LocalCategoryGroup("bad id!", "X", 1), LocalCategoryGroup("g2", "", 2)),
                )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(food), result.document.categoryGroups)
        assertEquals(listOf("SHOPPING"), result.document.budgets.map { it.categoryId })
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun counts_totalIncludesGroups() {
        assertEquals(3, BackupCounts(1, 0, 0, 0, 0, 0, 0, 2).total)
    }

    @Test
    fun export_includesGroupsGroupIdsAndSkipMonths() =
        runStoreTest {
            val repo =
                repository(
                    budgets = listOf(Budget(BuiltInCategories.FOOD.id, 16_000, setOf(8, 2))),
                    categoryDocs = listOf(userCategory("gym", "Gym").copy(groupId = "g-food")),
                    groups = listOf(Envelope("g2", "Fun", 1), Envelope("g-food", "Food", 0)),
                )

            val doc = repo.buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertEquals(listOf(food, LocalCategoryGroup("g2", "Fun", 1)), doc.categoryGroups)
            assertEquals("g-food", doc.categories.single { it.id == "gym" }.groupId)
            assertEquals(listOf(2, 8), doc.budgets.single().skipMonths)
        }

    @Test
    fun export_builtInFiledUnderGroup_isStored() =
        runStoreTest {
            val repo = repository(categoryDocs = listOf(BuiltInCategories.FOOD.copy(groupId = "g-food")))

            val doc = repo.buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertEquals("g-food", doc.categories.single { it.id == "FOOD" }.groupId)
        }

    @Test
    fun restoreAsGuest_writesGroupsToStore() =
        runStoreTest {
            val doc =
                backupDoc(
                    budgets = listOf(LocalBudget("FOOD", 100, listOf(8))),
                    categories = listOf(backupCategory("gym").copy(groupId = "g-food")),
                ).copy(categoryGroups = listOf(food))

            assertTrue(repository().restoreAsGuest(doc).isSuccess)

            val stored = localStore.data.first()
            assertEquals(listOf(food), stored.categoryGroups)
            assertEquals("g-food", stored.categories.single().groupId)
            assertEquals(listOf(8), stored.budgets.single().skipMonths)
        }

    @Test
    fun restoreAsGuest_withoutGroups_clearsExistingGroups() =
        runStoreTest {
            repository().restoreAsGuest(backupDoc().copy(categoryGroups = listOf(food))).getOrThrow()

            repository().restoreAsGuest(backupDoc(expenses = listOf(backupExpense()))).getOrThrow()

            assertTrue(
                localStore.data
                    .first()
                    .categoryGroups
                    .isEmpty(),
            )
        }
}

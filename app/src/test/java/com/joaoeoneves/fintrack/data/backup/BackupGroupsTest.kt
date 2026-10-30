package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Backup schema v5: category groups, category group ids. */
class BackupGroupsTest : BackupRepositoryTestBase() {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val food = LocalCategoryGroup("g-food", "Food", 0)

    @Test
    fun v4File_decodesToCurrent_withNoGroups_andLegacyBudgetsDropped() {
        val v4 =
            """{$header,"schemaVersion":4,
            "categories":[{"id":"gym","name":"Gym","iconKey":"home","colorKey":"teal","kind":"SPENDING","order":10}],
            "budgets":[{"categoryId":"FOOD","limitCents":500}]}"""

        val result = BackupCodec.decode(v4).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertTrue(result.document.categoryGroups.isEmpty())
        assertEquals(
            null,
            result.document.categories
                .single()
                .groupId,
        )
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun nextVersion_isUnsupported() {
        assertEquals(
            BackupDecodeResult.UnsupportedVersion(BACKUP_SCHEMA_VERSION + 1),
            BackupCodec.decode("""{$header,"schemaVersion":${BACKUP_SCHEMA_VERSION + 1}}"""),
        )
    }

    @Test
    fun v5RoundTrip_keepsGroupsAndGroupIds() {
        val doc =
            backupDoc(
                categories = listOf(backupCategory("gym").copy(groupId = "g-food")),
            ).copy(categoryGroups = listOf(food))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(1, result.document.counts().categoryGroups)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidGroups_areSkippedAndCounted() {
        val doc =
            backupDoc()
                .copy(
                    categoryGroups =
                        listOf(food, LocalCategoryGroup("bad id!", "X", 1), LocalCategoryGroup("g2", "", 2)),
                )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(food), result.document.categoryGroups)
        assertEquals(2, result.skippedCount)
    }

    @Test
    fun counts_totalIncludesGroups() {
        assertEquals(3, BackupCounts(1, 0, 0, 0, 0, 0, 0, 2).total)
    }

    @Test
    fun export_includesGroupsAndGroupIds() =
        runStoreTest {
            val repo =
                repository(
                    categoryDocs = listOf(userCategory("gym", "Gym").copy(groupId = "g-food")),
                    groups = listOf(Envelope("g2", "Fun", 1), Envelope("g-food", "Food", 0)),
                )

            val doc = repo.buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertEquals(listOf(food, LocalCategoryGroup("g2", "Fun", 1)), doc.categoryGroups)
            assertEquals("g-food", doc.categories.single { it.id == "gym" }.groupId)
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
                    categories = listOf(backupCategory("gym").copy(groupId = "g-food")),
                ).copy(categoryGroups = listOf(food))

            assertTrue(repository().restoreAsGuest(doc).isSuccess)

            val stored = localStore.data.first()
            assertEquals(listOf(food), stored.categoryGroups)
            assertEquals("g-food", stored.categories.single().groupId)
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

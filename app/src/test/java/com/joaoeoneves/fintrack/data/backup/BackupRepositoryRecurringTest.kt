package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.toLocal
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupRepositoryRecurringTest : BackupRepositoryTestBase() {
    private val at = Instant.parse(EXPORTED_AT)

    @Test
    fun export_includesRecurringItems() =
        runStoreTest {
            val items = listOf(recurringItem("r1"), recurringItem("r2", type = RecurringType.INCOME, name = "Pay"))

            val doc = repository(recurring = items).buildBackup(at).getOrThrow()

            assertEquals(items.map { it.toLocal() }.toSet(), doc.recurring.toSet())
            assertEquals(2, doc.counts().recurring)
            assertEquals(BACKUP_SCHEMA_VERSION, doc.schemaVersion)
        }

    @Test
    fun export_recurringWithDeletedCategory_getsArchivedPlaceholder() =
        runStoreTest {
            val item = recurringItem("r1", categoryId = CategoryId("gone"))

            val doc = repository(recurring = listOf(item)).buildBackup(at).getOrThrow()

            val placeholder = doc.categories.single()
            assertEquals("gone", placeholder.id)
            assertTrue(placeholder.archived)
            assertEquals(
                1,
                BackupCodec
                    .decode(BackupCodec.encode(doc))
                    .success()
                    .document.recurring.size,
            )
        }

    @Test
    fun restoreAsGuest_writesRecurringToStore() =
        runStoreTest {
            val recurring = listOf(backupRecurring("r1"), backupRecurring("r2", type = "INCOME"))
            val doc = backupDoc().copy(recurring = recurring)

            val result = repository().restoreAsGuest(doc)

            assertTrue(result.isSuccess)
            assertEquals(doc.recurring, localStore.data.first().recurring)
        }

    @Test
    fun restoreAsGuest_withoutRecurring_clearsExisting() =
        runStoreTest {
            localStore.update { it.copy(recurring = listOf(backupRecurring("old"))) to Unit }

            repository().restoreAsGuest(backupDoc(expenses = listOf(backupExpense()))).getOrThrow()

            assertTrue(
                localStore.data
                    .first()
                    .recurring
                    .isEmpty(),
            )
        }

    @Test
    fun localHasData_trueWithOnlyRecurring() =
        runStoreTest {
            localStore.update { it.copy(recurring = listOf(backupRecurring())) to Unit }

            assertEquals(true, repository().localHasData())
        }
}

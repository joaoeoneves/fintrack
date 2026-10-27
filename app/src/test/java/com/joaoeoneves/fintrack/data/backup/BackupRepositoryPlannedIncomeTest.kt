package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupRepositoryPlannedIncomeTest : BackupRepositoryTestBase() {
    @Test
    fun export_includesPlannedIncomeLines() =
        runStoreTest {
            val lines = listOf(PlannedIncome("p2", "Rent", 500, 1), PlannedIncome("p1", "Pay", 900, 0))
            val repo = repository(plannedIncome = lines)

            val doc = repo.buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            val expected = listOf(backupPlanned("p1", "Pay", 900, 0), backupPlanned("p2", "Rent", 500, 1))
            assertEquals(expected, doc.plannedIncome)
            assertEquals(2, doc.counts().plannedIncome)
            assertEquals(BACKUP_SCHEMA_VERSION, doc.schemaVersion)
        }

    @Test
    fun restoreAsGuest_writesPlannedIncomeToStore() =
        runStoreTest {
            val doc = backupDoc().copy(plannedIncome = listOf(backupPlanned("p1"), backupPlanned("p2", "Rent", 10, 1)))

            val result = repository().restoreAsGuest(doc)

            assertTrue(result.isSuccess)
            assertEquals(doc.plannedIncome, localStore.data.first().plannedIncome)
        }

    @Test
    fun restoreAsGuest_withoutPlannedIncome_clearsExistingLines() =
        runStoreTest {
            localStore.update { it.copy(plannedIncome = listOf(backupPlanned("old"))) to Unit }

            repository().restoreAsGuest(backupDoc(expenses = listOf(backupExpense()))).getOrThrow()

            assertTrue(
                localStore.data
                    .first()
                    .plannedIncome
                    .isEmpty(),
            )
        }

    @Test
    fun localHasData_trueWithOnlyPlannedIncome() =
        runStoreTest {
            localStore.update { it.copy(plannedIncome = listOf(backupPlanned())) to Unit }

            assertEquals(true, repository().localHasData())
        }
}

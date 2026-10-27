package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupRepositorySalaryProfileTest : BackupRepositoryTestBase() {
    private val profile = SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000)

    @Test
    fun export_includesTheProfile() =
        runStoreTest {
            val doc = repository(salaryProfile = profile).buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertEquals(LocalSalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000), doc.salaryProfile)
            assertEquals(BACKUP_SCHEMA_VERSION, doc.schemaVersion)
        }

    @Test
    fun export_withoutProfile_hasNone() =
        runStoreTest {
            val doc = repository().buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertNull(doc.salaryProfile)
        }

    @Test
    fun restoreAsGuest_writesTheProfile_andClearsItWhenAbsent() =
        runStoreTest {
            val local = LocalSalaryProfile(baseGrossCents = 5_000)

            repository().restoreAsGuest(backupDoc().copy(salaryProfile = local)).getOrThrow()
            assertEquals(local, localStore.data.first().salaryProfile)

            repository().restoreAsGuest(backupDoc(expenses = listOf(backupExpense()))).getOrThrow()
            assertNull(localStore.data.first().salaryProfile)
        }

    @Test
    fun localHasData_trueWithOnlyAProfile() =
        runStoreTest {
            localStore.update { it.copy(salaryProfile = LocalSalaryProfile(baseGrossCents = 1)) to Unit }

            assertTrue(repository().localHasData())
        }
}

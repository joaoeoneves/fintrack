package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class BackupRepositoryMonthSettingsTest : BackupRepositoryTestBase() {
    private val at = Instant.parse("2026-10-27T12:00:00Z")

    @Test
    fun export_includesMonthSettings() =
        runStoreTest {
            val repo = repository(monthSettings = listOf(MonthSettings(YearMonth.of(2026, 10), 15)))

            val doc = repo.buildBackup(at).getOrThrow()

            assertEquals(listOf(LocalMonthSettings("2026-10", 15)), doc.monthSettings)
            assertEquals(1, doc.counts().monthSettings)
            assertEquals(BACKUP_SCHEMA_VERSION, doc.schemaVersion)
        }

    @Test
    fun export_withoutDaysOff_hasNoMonthSettings() =
        runStoreTest {
            assertTrue(
                repository()
                    .buildBackup(at)
                    .getOrThrow()
                    .monthSettings
                    .isEmpty(),
            )
        }

    @Test
    fun restoreAsGuest_writesMonthSettingsToStore() =
        runStoreTest {
            val doc = backupDoc().copy(monthSettings = listOf(LocalMonthSettings("2026-10", 15)))

            assertTrue(repository().restoreAsGuest(doc).isSuccess)

            assertEquals(doc.monthSettings, localStore.data.first().monthSettings)
        }

    @Test
    fun restoreAsGuest_withoutMonthSettings_clearsExisting() =
        runStoreTest {
            localStore.update { it.copy(monthSettings = listOf(LocalMonthSettings("2026-01", 3))) to Unit }

            repository().restoreAsGuest(backupDoc(expenses = listOf(backupExpense()))).getOrThrow()

            assertTrue(
                localStore.data
                    .first()
                    .monthSettings
                    .isEmpty(),
            )
        }

    @Test
    fun export_includesDaysOff_andSkipsBlankOnes() =
        runStoreTest {
            val repo =
                repository(
                    monthSettings =
                        listOf(
                            MonthSettings(YearMonth.of(2026, 10), 2),
                            MonthSettings(YearMonth.of(2026, 11), 3),
                            MonthSettings(YearMonth.of(2026, 12), 0),
                        ),
                )

            val doc = repo.buildBackup(at).getOrThrow()

            assertEquals(listOf(LocalMonthSettings("2026-10", 2), LocalMonthSettings("2026-11", 3)), doc.monthSettings)
        }

    @Test
    fun restoreAsGuest_keepsDaysOff() =
        runStoreTest {
            val doc = backupDoc().copy(monthSettings = listOf(LocalMonthSettings("2026-10", 4)))

            assertTrue(repository().restoreAsGuest(doc).isSuccess)

            assertEquals(doc.monthSettings, localStore.data.first().monthSettings)
        }
}

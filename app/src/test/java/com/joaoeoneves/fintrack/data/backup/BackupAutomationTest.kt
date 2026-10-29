package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

/** Schema v12: the automation settings in the backup document, its codec and the repository export/restore. */
class BackupAutomationTest : BackupRepositoryTestBase() {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val automation = LocalAutomation(lineThrough = mapOf("p1" to "2026-09"))

    @Test
    fun v11File_decodesWithoutAutomation() {
        val result = BackupCodec.decode("""{$header,"schemaVersion":11,"expenses":[]}""").success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertNull(result.document.automation)
        assertEquals(0, result.document.counts().automation)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun roundTrip_keepsAutomation_andCountsIt() {
        val doc = backupDoc().copy(automation = automation)

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(1, result.document.counts().automation)
        assertEquals(1, result.document.counts().total)
    }

    @Test
    fun salaryPaysNextMonthOnly_isKept() {
        val doc = backupDoc().copy(automation = LocalAutomation(salaryPaysNextMonth = true))

        assertEquals(doc, BackupCodec.decode(BackupCodec.encode(doc)).success().document)
    }

    @Test
    fun invalidCursor_isDropped_andCountedAsSkipped() {
        val text = """{$header,"schemaVersion":13,"automation":{"lineThrough":{"p1":"soon"}}}"""

        val result = BackupCodec.decode(text).success()

        assertNull(result.document.automation)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun oldPaydaySwitch_isIgnored_andTheFileStillDecodes() {
        val text = """{$header,"schemaVersion":12,"automation":{"paydayEnabled":true,"paydayThrough":"2026-09"}}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(LocalAutomation(), result.document.automation)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun decodedFile_withNewerUnknownKeys_stillWorks() {
        val text = """{$header,"schemaVersion":13,"automation":{"salaryPaysNextMonth":false,"x":1}}"""

        assertEquals(
            LocalAutomation(salaryPaysNextMonth = false),
            BackupCodec
                .decode(text)
                .success()
                .document.automation,
        )
    }

    @Test
    fun export_includesNonDefaultAutomation() =
        runStoreTest {
            val settings = AutomationSettings(mapOf("p1" to YearMonth.of(2026, 9)))

            val doc = repository(automation = settings).buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertEquals(automation, doc.automation)
        }

    @Test
    fun export_omitsDefaultAutomation() =
        runStoreTest {
            val doc = repository().buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertNull(doc.automation)
        }

    @Test
    fun restoreAsGuest_writesAutomation_andClearsItWhenAbsent() =
        runStoreTest {
            repository().restoreAsGuest(backupDoc().copy(automation = automation)).getOrThrow()
            assertEquals(automation, localStore.data.first().automation)

            repository().restoreAsGuest(backupDoc(expenses = listOf(backupExpense()))).getOrThrow()
            assertNull(localStore.data.first().automation)
        }

    @Test
    fun localHasData_trueWithOnlyAutomation() =
        runStoreTest {
            localStore.update { it.copy(automation = automation) to Unit }

            assertTrue(repository().localHasData())
        }
}

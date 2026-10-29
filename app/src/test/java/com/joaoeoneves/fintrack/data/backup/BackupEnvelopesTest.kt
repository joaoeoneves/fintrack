package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.Envelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Backup schema v16: an envelope's amount and funding line. */
class BackupEnvelopesTest : BackupRepositoryTestBase() {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v13File_withEarmarks_decodesUpgraded_earmarkIgnored_envelopeFieldsDefault() {
        val v13 =
            """{$header,"schemaVersion":13,
            "categoryGroups":[{"id":"g-food","name":"Food","order":0}],
            "plannedIncome":[{"id":"meal","label":"Meal","amountCents":100,"order":0,
            "earmarkType":"GROUP","earmarkId":"g-food"}]}"""

        val result = BackupCodec.decode(v13).success()

        assertEquals(19, result.document.schemaVersion)
        assertEquals(
            LocalCategoryGroup("g-food", "Food", 0, null, null),
            result.document.categoryGroups
                .single()
                .copy(startMonth = null),
        )
        assertEquals(
            "meal",
            result.document.plannedIncome
                .single()
                .id,
        )
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun newFields_roundTrip() {
        val doc =
            backupDoc().copy(categoryGroups = listOf(LocalCategoryGroup("g1", "Food", 0, 12_345, "line_1")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidAmountOrLineId_isSkippedAndCounted() {
        val ok = LocalCategoryGroup("g1", "Food", 0, 1, "line")
        val doc =
            backupDoc().copy(
                categoryGroups =
                    listOf(
                        ok,
                        LocalCategoryGroup("g2", "Zero", 1, 0, null),
                        LocalCategoryGroup("g3", "Neg", 2, -5, null),
                        LocalCategoryGroup("g4", "BadLine", 3, null, "bad id!"),
                        LocalCategoryGroup("g5", "Huge", 4, Long.MAX_VALUE, null),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(ok), result.document.categoryGroups)
        assertEquals(4, result.skippedCount)
    }

    @Test
    fun export_carriesAmountAndFundingLine() =
        runStoreTest {
            val repo = repository(groups = listOf(Envelope("g1", "Food", 0, 7_000, "meal")))

            val doc = repo.buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertEquals(listOf(LocalCategoryGroup("g1", "Food", 0, 7_000, "meal")), doc.categoryGroups)
            assertTrue(doc.counts().categoryGroups == 1)
        }
}

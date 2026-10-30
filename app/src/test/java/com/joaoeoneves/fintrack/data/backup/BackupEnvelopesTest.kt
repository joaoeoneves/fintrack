package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.Envelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

/** Envelopes in a backup: name and order only; the removed amount/funding/timing fields vanish. */
class BackupEnvelopesTest : BackupRepositoryTestBase() {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v13File_withEarmarks_decodesUpgraded_earmarkIgnored() {
        val v13 =
            """{$header,"schemaVersion":13,
            "categoryGroups":[{"id":"g-food","name":"Food","order":0}],
            "plannedIncome":[{"id":"meal","label":"Meal","amountCents":100,"order":0,
            "earmarkType":"GROUP","earmarkId":"g-food"}]}"""

        val result = BackupCodec.decode(v13).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(listOf(LocalCategoryGroup("g-food", "Food", 0)), result.document.categoryGroups)
        assertEquals(
            "meal",
            result.document.plannedIncome
                .single()
                .id,
        )
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v22Envelope_withAmountFundingAndVersions_decodesPlain_notSkipped() {
        val v22 =
            """{$header,"schemaVersion":22,
            "categoryGroups":[{"id":"g1","name":"Food","order":0,"amountCents":12345,"fundedByLineId":"line_1",
            "startMonth":"2026-03","versions":[{"from":"2026-03","amountCents":12345}]},
            {"id":"g2","name":"Huge","order":1,"amountCents":9223372036854775807}]}"""

        val result = BackupCodec.decode(v22).success()

        assertEquals(
            listOf(LocalCategoryGroup("g1", "Food", 0), LocalCategoryGroup("g2", "Huge", 1)),
            result.document.categoryGroups,
        )
        assertEquals(0, result.skippedCount)
        val reEncoded = BackupCodec.encode(result.document)
        listOf("fundedByLineId", "versions", "startMonth").forEach { assertFalse(reEncoded.contains(it)) }
    }

    @Test
    fun export_carriesNameAndOrderOnly() =
        runStoreTest {
            val repo = repository(groups = listOf(Envelope("g1", "Food", 0)))

            val doc = repo.buildBackup(Instant.parse(EXPORTED_AT)).getOrThrow()

            assertEquals(listOf(LocalCategoryGroup("g1", "Food", 0)), doc.categoryGroups)
            assertEquals(1, doc.counts().categoryGroups)
        }
}

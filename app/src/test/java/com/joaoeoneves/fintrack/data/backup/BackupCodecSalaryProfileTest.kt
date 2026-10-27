package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecSalaryProfileTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val profile = LocalSalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000)
    private val managed = backupPlanned("profile_net_salary").copy(managedKey = MANAGED_NET_SALARY)

    @Test
    fun v7File_decodesUpgradedToV9_withoutProfile() {
        val planned = """[{"id":"p","label":"Pay","amountCents":50000,"order":0,"frequency":"MONTHLY"}]"""
        val text = """{$header,"schemaVersion":7,"plannedIncome":$planned}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(9, result.document.schemaVersion)
        assertNull(result.document.salaryProfile)
        assertNull(
            result.document.plannedIncome
                .single()
                .managedKey,
        )
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.document.counts().salaryProfile)
    }

    @Test
    fun roundTrip_keepsProfileAndManagedLines() {
        val doc = backupDoc().copy(salaryProfile = profile, plannedIncome = listOf(managed))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(1, result.document.counts().salaryProfile)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun managedKey_isStripped_whenThereIsNoProfile() {
        val doc = backupDoc().copy(plannedIncome = listOf(managed, backupPlanned("p2")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(null, null), result.document.plannedIncome.map { it.managedKey })
        assertEquals(2, result.document.plannedIncome.size)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidProfile_isDropped_countedAsSkipped_andManagedKeysStripped() {
        val doc = backupDoc().copy(salaryProfile = profile.copy(ssPpm = 2_000_000), plannedIncome = listOf(managed))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertNull(result.document.salaryProfile)
        assertNull(
            result.document.plannedIncome
                .single()
                .managedKey,
        )
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun unknownManagedKey_dropsTheLine() {
        val doc =
            backupDoc().copy(
                salaryProfile = profile,
                plannedIncome = listOf(managed, backupPlanned("bad").copy(managedKey = "nope")),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf("profile_net_salary"), result.document.plannedIncome.map { it.id })
        assertEquals(1, result.skippedCount)
    }
}

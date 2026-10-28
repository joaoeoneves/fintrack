package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalSalaryExtra
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.data.local.toDomain
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecSalaryProfileTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val profile = LocalSalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000)
    private val managed = backupPlanned("profile_net_salary").copy(managedKey = MANAGED_NET_SALARY)

    @Test
    fun v7File_decodesUpgradedToV14_withoutProfile() {
        val planned = """[{"id":"p","label":"Pay","amountCents":50000,"order":0,"frequency":"MONTHLY"}]"""
        val text = """{$header,"schemaVersion":7,"plannedIncome":$planned}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(15, result.document.schemaVersion)
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

    @Test
    fun v9File_withLegacyProfileFields_stillDecodes_andMigratesOnToDomain() {
        val profileJson = """{"baseGrossCents":100000,"flexPpm":70000,"bonusCents":10000,"bonusMonth":5}"""
        val text = """{$header,"schemaVersion":9,"salaryProfile":$profileJson}"""

        val result = BackupCodec.decode(text).success()

        val decoded = requireNotNull(result.document.salaryProfile)
        assertEquals(15, result.document.schemaVersion)
        assertEquals(0, result.skippedCount)
        assertEquals(listOf("flex", "bonus"), decoded.toDomain().extras.map { it.id })
    }

    @Test
    fun v10RoundTrip_keepsExtras() {
        val extra = LocalSalaryExtra("e1", "Prize", "MULTIPLE_OF_BASE_PPM", 500_000, listOf(3, 9), "IRS", 2_000)
        val doc = backupDoc().copy(salaryProfile = profile.copy(extras = listOf(extra)))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(extra), result.document.salaryProfile?.extras)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun profileWithInvalidExtras_isDropped_andCountedAsSkipped() {
        val bad = LocalSalaryExtra("e1", "Prize", "FIXED_CENTS", 0, listOf(13))
        val doc = backupDoc().copy(salaryProfile = profile.copy(extras = listOf(bad)), plannedIncome = listOf(managed))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertNull(result.document.salaryProfile)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun extraManagedLines_surviveWhenTheProfileIsValid() {
        val line = backupPlanned("profile_extra_e1_3").copy(managedKey = "profile_extra_e1_3")
        val extra = LocalSalaryExtra("e1", "Prize", "FIXED_CENTS", 5_000, listOf(3))
        val doc = backupDoc().copy(salaryProfile = profile.copy(extras = listOf(extra)), plannedIncome = listOf(line))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(
            "profile_extra_e1_3",
            result.document.plannedIncome
                .single()
                .managedKey,
        )
    }

    @Test
    fun extraScope_roundTrips_andV12FileWithoutItIsTotal() {
        val perPayment =
            LocalSalaryExtra("e1", "Prize", "FIXED_CENTS", 5_000, listOf(3, 9), amountScope = "PER_PAYMENT")
        val doc = backupDoc().copy(salaryProfile = profile.copy(extras = listOf(perPayment)))

        assertEquals(
            listOf(perPayment),
            BackupCodec
                .decode(BackupCodec.encode(doc))
                .success()
                .document.salaryProfile
                ?.extras,
        )

        val extra = """{"id":"e1","name":"Prize","amountType":"FIXED_CENTS","amount":5000,"months":[3]}"""
        val old = """{$header,"schemaVersion":12,"salaryProfile":{"baseGrossCents":100000,"extras":[$extra]}}"""
        val decoded = BackupCodec.decode(old).success()

        assertEquals(
            "TOTAL",
            decoded.document.salaryProfile
                ?.extras
                ?.single()
                ?.amountScope,
        )
        assertEquals(0, decoded.skippedCount)
    }

    @Test
    fun extraWithUnknownScope_dropsTheProfile() {
        val bad = LocalSalaryExtra("e1", "Prize", "FIXED_CENTS", 5_000, listOf(3), amountScope = "WEEKLY")
        val doc = backupDoc().copy(salaryProfile = profile.copy(extras = listOf(bad)))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertNull(result.document.salaryProfile)
        assertEquals(1, result.skippedCount)
    }
}

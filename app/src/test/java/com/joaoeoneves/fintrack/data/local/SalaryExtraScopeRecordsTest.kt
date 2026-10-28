package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountScope
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The local / backup record of a salary extra's amount scope. */
class SalaryExtraScopeRecordsTest {
    private val fixed = SalaryExtraAmountType.FIXED_CENTS

    private fun profile(scope: SalaryExtraAmountScope) =
        SalaryProfile(
            baseGrossCents = 100_000,
            extras = listOf(SalaryExtra("a1", "Prize", fixed, 5_000, setOf(2, 5), amountScope = scope)),
        )

    @Test
    fun scope_roundTripsThroughTheLocalRecord() {
        for (scope in SalaryExtraAmountScope.entries) {
            val local = profile(scope).toLocal()

            assertEquals(scope.name, local.extras.single().amountScope)
            assertEquals(profile(scope), local.toDomain())
        }
    }

    @Test
    fun scope_roundTripsThroughJson() {
        val local = profile(SalaryExtraAmountScope.PER_PAYMENT).toLocal()
        val json = Json { encodeDefaults = true }

        val decoded = json.decodeFromString<LocalSalaryProfile>(json.encodeToString(local))

        assertEquals(local, decoded)
    }

    @Test
    fun v12Json_withoutTheKey_isTotal() {
        val text = """{"id":"a1","name":"Prize","amountType":"FIXED_CENTS","amount":5000,"months":[2]}"""

        val extra = Json { ignoreUnknownKeys = true }.decodeFromString<LocalSalaryExtra>(text)

        assertEquals("TOTAL", extra.amountScope)
        assertEquals(
            SalaryExtraAmountScope.TOTAL,
            LocalSalaryProfile(extras = listOf(extra))
                .toDomain()
                .extras
                .single()
                .amountScope,
        )
    }

    @Test
    fun unknownScope_isRejectedByValidation_andDroppedByTheMapping() {
        val bad = LocalSalaryExtra("a1", "Prize", "FIXED_CENTS", 5_000, listOf(2), amountScope = "WEEKLY")
        val local = LocalSalaryProfile(baseGrossCents = 100_000, extras = listOf(bad))

        assertThrows(IllegalArgumentException::class.java) { validateSalaryProfile(local) }
        assertEquals(emptyList<SalaryExtra>(), local.toDomain().extras)
    }
}

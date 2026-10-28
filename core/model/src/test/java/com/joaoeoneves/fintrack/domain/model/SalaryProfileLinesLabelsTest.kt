package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generated line labels follow the app language unless the user renamed them. */
class SalaryProfileLinesLabelsTest {
    private val en = FakeSalaryLineLabelsProvider.ENGLISH
    private val pt = FakeSalaryLineLabelsProvider.PORTUGUESE
    private val defaults = listOf(en, pt)
    private val profile =
        SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, extras = listOf(testExtra(), fixedExtra()))

    private val stored = salaryProfileLines(profile, emptyList(), en)

    private fun List<PlannedIncome>.label(key: String) = single { it.managedKey == key }.label

    @Test
    fun defaultLabelInAnotherLanguage_isRelabelled() {
        val result = salaryProfileLines(profile, stored, pt, defaults)

        assertEquals("Salário líquido", result.label(MANAGED_NET_SALARY))
        assertEquals("Bonus", result.label(managedExtraKey("bonus", 5)))
    }

    @Test
    fun withoutKnownDefaults_existingLabelIsKept() {
        assertEquals("Net salary", salaryProfileLines(profile, stored, pt).label(MANAGED_NET_SALARY))
    }

    @Test
    fun renamedLabel_isKept() {
        val renamed = stored.map { if (it.id == MANAGED_NET_SALARY) it.copy(label = "My wage") else it }

        assertEquals("My wage", salaryProfileLines(profile, renamed, pt, defaults).label(MANAGED_NET_SALARY))
    }

    @Test
    fun extras_areNamedAfterTheExtra_notRelabelled() {
        val result = salaryProfileLines(profile, stored, pt, defaults)

        assertEquals("Flexible benefits", result.label(managedExtraKey("flex", 1)))
        assertEquals("Bonus", result.label(managedExtraKey("bonus", 5)))
    }

    @Test
    fun managedLinesSync_upsertsTheRelabelledLine_thenSettles() {
        val sync = managedLinesSync(profile, stored, pt, defaults)

        assertEquals(
            setOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS, MANAGED_HOLIDAY, MANAGED_CHRISTMAS),
            sync.upserts.map { it.id }.toSet(),
        )
        assertTrue(sync.upserts.all { it.label == pt.labelFor(it.id) })
        assertTrue(
            managedLinesSync(
                profile,
                sync.upserts +
                    stored.filter { l ->
                        sync.upserts.none { it.id == l.id }
                    },
                pt,
                defaults,
            ).isEmpty,
        )
    }

    @Test
    fun labelFor_mapsFixedKeys_andNullForExtrasAndUnknown() {
        assertEquals("Meal allowance", en.labelFor(MANAGED_MEAL))
        assertEquals("Subsidy", en.labelFor(MANAGED_HOLIDAY))
        assertEquals("Subsidy", en.labelFor(MANAGED_CHRISTMAS))
        assertEquals(null, en.labelFor(managedExtraKey("bonus", 5)))
        assertEquals(null, en.labelFor("nope"))
    }
}

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
        assertEquals("Holiday subsidy", en.labelFor(MANAGED_HOLIDAY))
        assertEquals("Christmas subsidy", en.labelFor(MANAGED_CHRISTMAS))
        assertEquals(null, en.labelFor(managedExtraKey("bonus", 5)))
        assertEquals(null, en.labelFor("nope"))
    }

    @Test
    fun holidayAndChristmasLines_haveDistinctLabels() {
        assertEquals("Holiday subsidy", stored.label(MANAGED_HOLIDAY))
        assertEquals("Christmas subsidy", stored.label(MANAGED_CHRISTMAS))
        val result = salaryProfileLines(profile, stored, pt, defaults)
        assertEquals("Subsídio de férias", result.label(MANAGED_HOLIDAY))
        assertEquals("Subsídio de Natal", result.label(MANAGED_CHRISTMAS))
    }

    @Test
    fun legacySharedSubsidyLabel_isRelabelledByTheSync() {
        val legacy = FakeSalaryLineLabelsProvider.LEGACY_ENGLISH.holidaySubsidy
        listOf(legacy, FakeSalaryLineLabelsProvider.LEGACY_PORTUGUESE.holidaySubsidy).forEach { old ->
            val existing =
                stored.map { if (it.managedKey in setOf(MANAGED_HOLIDAY, MANAGED_CHRISTMAS)) it.copy(label = old) else it }
            val sync = managedLinesSync(profile, existing, en, FakeSalaryLineLabelsProvider().knownDefaults())

            val byId = sync.upserts.associateBy { it.id }
            assertEquals("Holiday subsidy", byId.getValue(MANAGED_HOLIDAY).label)
            assertEquals("Christmas subsidy", byId.getValue(MANAGED_CHRISTMAS).label)
        }
    }

    @Test
    fun userEditedSubsidyLabel_isKept() {
        val existing = stored.map { if (it.managedKey == MANAGED_HOLIDAY) it.copy(label = "My 13th") else it }
        val result = salaryProfileLines(profile, existing, pt, FakeSalaryLineLabelsProvider().knownDefaults())

        assertEquals("My 13th", result.label(MANAGED_HOLIDAY))
        assertEquals("Subsídio de Natal", result.label(MANAGED_CHRISTMAS))
    }

    @Test
    fun secondSyncAfterLegacyRelabel_isANoOp() {
        val all = FakeSalaryLineLabelsProvider().knownDefaults()
        val legacy = stored.map { if (it.managedKey == MANAGED_HOLIDAY) it.copy(label = "Subsidy") else it }
        val first = managedLinesSync(profile, legacy, en, all)
        val settled = first.upserts + legacy.filter { l -> first.upserts.none { it.id == l.id } }

        assertTrue(first.upserts.isNotEmpty())
        assertTrue(managedLinesSync(profile, settled, en, all).isEmpty)
    }
}

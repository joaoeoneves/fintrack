package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.LegacyExtraNames
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.repository.LegacyExtraNamesProvider
import com.joaoeoneves.fintrack.testutil.bonusExtra
import com.joaoeoneves.fintrack.testutil.flexExtra
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSalaryProfileRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val names =
        object : LegacyExtraNamesProvider {
            override fun names() = LegacyExtraNames("Flexivel", "Lucros", "Premio")
        }
    private val repo by lazy { LocalSalaryProfileRepository(store, names) }

    private suspend fun current() = repo.observeSalaryProfile().first()

    @Test
    fun observe_empty_isNull() =
        runStoreTest {
            assertNull(current())
        }

    @Test
    fun save_thenObserve_roundTripsTheExtras() =
        runStoreTest {
            val profile = SalaryProfile(baseGrossCents = 100_000, extras = listOf(flexExtra(), bonusExtra()))

            assertTrue(repo.saveSalaryProfile(profile).isSuccess)

            assertEquals(profile, current())
            assertNull(diskDoc().salaryProfile?.flexPpm)
            assertEquals(2, diskDoc().salaryProfile?.extras?.size)
        }

    @Test
    fun save_invalidExtras_failsAndStoresNothing() =
        runStoreTest {
            val bad = SalaryProfile(baseGrossCents = 100_000, extras = listOf(bonusExtra(name = " ")))

            assertTrue(repo.saveSalaryProfile(bad).isFailure)

            assertNull(current())
        }

    @Test
    fun legacyV9File_isMigratedOnRead_withLocalizedNames() =
        runStoreTest {
            dataFile.writeText(
                """{"schemaVersion":9,"revision":1,"salaryProfile":""" +
                    """{"baseGrossCents":100000,"flexPpm":70000,"bonusCents":10000,"bonusMonth":5}}""",
            )

            val extras = current()!!.extras

            assertEquals(listOf("flex", "bonus"), extras.map { it.id })
            assertEquals(listOf("Flexivel", "Premio"), extras.map { it.name })
        }

    @Test
    fun delete_removesTheProfile() =
        runStoreTest {
            repo.saveSalaryProfile(SalaryProfile(baseGrossCents = 1))

            assertTrue(repo.deleteSalaryProfile().isSuccess)

            assertNull(current())
        }
}

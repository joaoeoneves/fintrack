package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The group-related behavior of [FakeEnvelopeRepository], [FakeCategoryRepository] and [FakeBudgetRepository]. */
class FakeEnvelopeRepositoryTest {
    private fun edit(
        id: String?,
        name: String,
    ) = EnvelopeEdit(id, name, null, null, emptySet(), emptyMap())

    @Test
    fun add_rename_reorder_roundTrip() =
        runBlocking {
            val repo = FakeEnvelopeRepository()
            val a = repo.addEnvelope("A").getOrThrow()
            val b = repo.addEnvelope("B").getOrThrow()

            repo.saveEnvelope(edit(a, "Alpha"))
            repo.reorderEnvelopes(listOf(b, a))

            assertEquals(
                listOf(Envelope(b, "B", 0), Envelope(a, "Alpha", 1)),
                repo.observeEnvelopes().first(),
            )
        }

    @Test
    fun validation_rejectsEmptyAndTooLongNames_andUnknownIds() =
        runBlocking {
            val repo = FakeEnvelopeRepository()
            val id = repo.addEnvelope("A").getOrThrow()

            assertTrue(repo.addEnvelope("").isFailure)
            assertTrue(repo.addEnvelope("x".repeat(MAX_GROUP_NAME_LENGTH + 1)).isFailure)
            assertTrue(repo.saveEnvelope(edit(id, "")).isFailure)
            assertTrue(repo.reorderEnvelopes(listOf("nope")).isFailure)
            assertEquals(listOf("A"), repo.envelopes.map { it.name })
        }

    @Test
    fun delete_ungroupsLinkedCategories() =
        runBlocking {
            val categories = FakeCategoryRepository()
            val repo = FakeEnvelopeRepository(categories = categories)
            val g = repo.addEnvelope("G").getOrThrow()
            val kept = repo.addEnvelope("Kept").getOrThrow()
            val custom = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, g).getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, g)
            categories.setGroup(BuiltInCategories.SHOPPING.id, kept)

            assertTrue(repo.deleteEnvelope(g).isSuccess)

            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertNull(catalog.getValue(custom).groupId)
            assertNull(catalog.getValue(BuiltInCategories.FOOD.id).groupId)
            assertEquals(kept, catalog.getValue(BuiltInCategories.SHOPPING.id).groupId)
            assertEquals(listOf(kept), repo.envelopes.map { it.id })
        }

    @Test
    fun writeError_failsOnce_thenRecovers() =
        runBlocking {
            val boom = IllegalStateException("boom")
            val repo = FakeEnvelopeRepository(nextWriteError = boom)

            assertEquals(boom, repo.addEnvelope("A").exceptionOrNull())
            assertTrue(repo.addEnvelope("A").isSuccess)
        }

    @Test
    fun categoryFake_setGroup_unknownCategoryFails_andAddCategoryKeepsGroup() =
        runBlocking {
            val categories = FakeCategoryRepository()

            assertTrue(categories.setGroup(CategoryId("ghost"), "g").isFailure)
            val id = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, "g").getOrThrow()
            assertEquals(
                "g",
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == id }
                    .groupId,
            )
        }

    @Test
    fun budgetFake_setBudget_replacesSkipMonths() =
        runBlocking {
            val budgets = FakeBudgetRepository()
            val food = BuiltInCategories.FOOD.id

            budgets.setBudget(food, 100, setOf(8))
            assertEquals(
                setOf(8),
                budgets
                    .observeBudgets()
                    .first()
                    .single()
                    .skipMonths,
            )

            budgets.setBudget(food, 200)
            val budget = budgets.observeBudgets().first().single()
            assertEquals(200L, budget.limitCents)
            assertEquals(emptySet<Int>(), budget.skipMonths)
        }
}

package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEnvelopeRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val groups by lazy { LocalEnvelopeRepository(store, SequenceIds("g")) }
    private val categories by lazy { LocalCategoryRepository(store, SequenceIds("c")) }
    private val budgets by lazy { LocalBudgetRepository(store) }

    private suspend fun observed() = groups.observeEnvelopes().first().untimedEnvelopes()

    private suspend fun LocalEnvelopeRepository.addEnvelope(name: String) = saveEnvelope(edit(null, name))

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun edit(
        id: String?,
        name: String,
        amount: Long? = null,
        fundedBy: String? = null,
        members: Set<CategoryId> = emptySet(),
        limits: Map<CategoryId, Budget?> = emptyMap(),
    ) = EnvelopeEdit(id, name, amount, fundedBy, members, limits)

    @Test
    fun empty_hasNoGroups() =
        runStoreTest {
            assertEquals(emptyList<Envelope>(), observed())
        }

    @Test
    fun add_appendsWithIncreasingOrder() =
        runStoreTest {
            assertEquals("g1", groups.addEnvelope("Food").getOrThrow())
            groups.addEnvelope("Fun")

            assertEquals(listOf(Envelope("g1", "Food", 0), Envelope("g2", "Fun", 1)), observed())
        }

    @Test
    fun add_emptyOrTooLongName_failsAndWritesNothing() =
        runStoreTest {
            assertTrue(groups.addEnvelope("").exceptionOrNull() is IllegalArgumentException)
            assertTrue(groups.addEnvelope("x".repeat(MAX_GROUP_NAME_LENGTH + 1)).isFailure)

            assertFalse(dataFile.exists())
        }

    @Test
    fun add_nameAtLimit_isAccepted() =
        runStoreTest {
            assertTrue(groups.addEnvelope("x".repeat(MAX_GROUP_NAME_LENGTH)).isSuccess)
        }

    @Test
    fun save_existingId_renamesKeepingOrder() =
        runStoreTest {
            groups.addEnvelope("Food")
            groups.addEnvelope("Fun")

            assertEquals("g1", groups.saveEnvelope(edit("g1", "Groceries")).getOrThrow())

            assertEquals(listOf(Envelope("g1", "Groceries", 0), Envelope("g2", "Fun", 1)), observed())
        }

    @Test
    fun save_unknownIdOrInvalid_failsAndKeepsData() =
        runStoreTest {
            groups.addEnvelope("Food")

            assertTrue(groups.saveEnvelope(edit("g1", "")).isFailure)
            assertTrue(groups.saveEnvelope(edit("g1", "ok", amount = 0)).isFailure)
            assertTrue(groups.saveEnvelope(edit("g1", "ok", amount = -5)).isFailure)
            assertTrue(groups.saveEnvelope(edit("g1", "ok", amount = MAX_AMOUNT_CENTS + 1)).isFailure)
            assertTrue(groups.saveEnvelope(edit("g1", "ok", fundedBy = "bad id!")).isFailure)
            assertTrue(groups.saveEnvelope(edit("g1", "ok", fundedBy = "")).isFailure)
            assertEquals(listOf(Envelope("g1", "Food", 0)), observed())
        }

    @Test
    fun save_amountAndFundingAtTheLimits_areStored() =
        runStoreTest {
            groups.saveEnvelope(edit(null, "Max", amount = MAX_AMOUNT_CENTS, fundedBy = "a".repeat(64))).getOrThrow()
            groups.saveEnvelope(edit(null, "Min", amount = 1, fundedBy = "line_1-x")).getOrThrow()

            assertEquals(
                listOf(
                    Envelope("g1", "Max", 0, MAX_AMOUNT_CENTS, "a".repeat(64)),
                    Envelope("g2", "Min", 1, 1, "line_1-x"),
                ),
                observed(),
            )
        }

    @Test
    fun save_filesAddedCategories_movingThemFromAnotherEnvelope_andUnfilesRemoved() =
        runStoreTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            val fun1 = groups.addEnvelope("Fun").getOrThrow()
            val a = categories.addCategory("A", "home", "teal", CategoryKind.SPENDING, food).getOrThrow()
            val b = categories.addCategory("B", "home", "teal", CategoryKind.SPENDING, food).getOrThrow()
            val c = categories.addCategory("C", "home", "teal", CategoryKind.SPENDING, fun1).getOrThrow()

            groups.saveEnvelope(edit(food, "Food", members = setOf(a, c)))

            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertEquals(food, catalog.getValue(a).groupId)
            assertNull(catalog.getValue(b).groupId)
            assertEquals(food, catalog.getValue(c).groupId)
        }

    @Test
    fun save_limits_setAndClearBudgetsInTheSameWrite() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id
            val shopping = BuiltInCategories.SHOPPING.id
            budgets.setBudget(shopping, 500)
            val revisionBefore = store.data.first().revision

            val id =
                groups
                    .saveEnvelope(
                        edit(
                            null,
                            "Mix",
                            members = setOf(food, shopping),
                            limits =
                                mapOf(
                                    food to Budget(food, 900, setOf(2)),
                                    shopping to null,
                                ),
                        ),
                    ).getOrThrow()

            assertEquals(listOf(Budget(food, 900, setOf(2))), budgets.observeBudgets().first().untimed())
            assertEquals(revisionBefore + 1, store.data.first().revision)
        }

    @Test
    fun save_filesABuiltInCategoryWithoutAStoredRecord() =
        runStoreTest {
            val id = groups.saveEnvelope(edit(null, "Mix", members = setOf(BuiltInCategories.FOOD.id))).getOrThrow()

            assertEquals(
                id,
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == BuiltInCategories.FOOD.id }
                    .groupId,
            )
        }

    @Test
    fun save_fixedAmount_clearsTheMembersBudgets() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id
            val shopping = BuiltInCategories.SHOPPING.id
            budgets.setBudget(food, 100)
            budgets.setBudget(shopping, 200)

            groups.saveEnvelope(edit(null, "Fixed", amount = 5_000, members = setOf(food)))

            assertEquals(listOf(shopping), budgets.observeBudgets().first().map { it.categoryId })
        }

    @Test
    fun save_invalidLimit_failsAndWritesNothingElse() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id

            val result =
                groups.saveEnvelope(
                    edit(null, "Bad", members = setOf(food), limits = mapOf(food to Budget(food, 0))),
                )

            assertTrue(result.isFailure)
            assertEquals(emptyList<Envelope>(), observed())
        }

    @Test
    fun save_fundingLine_isClearedOnAnyOtherEnvelopeUsingIt() =
        runStoreTest {
            groups.saveEnvelope(edit(null, "A", fundedBy = "meal"))
            groups.saveEnvelope(edit(null, "B", fundedBy = "other"))

            groups.saveEnvelope(edit(null, "C", fundedBy = "meal"))

            assertEquals(listOf(null, "other", "meal"), observed().map { it.fundedByLineId })
        }

    @Test
    fun save_sameEnvelopeKeepsItsOwnFundingLine() =
        runStoreTest {
            groups.saveEnvelope(edit(null, "A", fundedBy = "meal"))

            groups.saveEnvelope(edit("g1", "A2", fundedBy = "meal"))

            assertEquals(listOf(Envelope("g1", "A2", 0, null, "meal")), observed())
        }

    @Test
    fun reorder_persistsPositions_andObserveSortsByOrder() =
        runStoreTest {
            groups.addEnvelope("A")
            groups.addEnvelope("B")
            groups.addEnvelope("C")

            assertTrue(groups.reorderEnvelopes(listOf("g3", "g1", "g2")).isSuccess)

            assertEquals(listOf("g3", "g1", "g2"), observed().map { it.id })
            assertEquals(listOf(0, 1, 2), observed().map { it.order })
        }

    @Test
    fun reorder_unknownId_failsAndKeepsOrder() =
        runStoreTest {
            groups.addEnvelope("A")
            groups.addEnvelope("B")

            assertTrue(groups.reorderEnvelopes(listOf("g2", "zzz")).isFailure)

            assertEquals(listOf("g1", "g2"), observed().map { it.id })
        }

    @Test
    fun delete_removesGroup_andUngroupsItsCategories() =
        runStoreTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            val other = groups.addEnvelope("Other").getOrThrow()
            val gym = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, food).getOrThrow()
            val book = categories.addCategory("Books", "home", "teal", CategoryKind.SPENDING, other).getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)

            assertTrue(groups.deleteEnvelope(food).isSuccess)

            assertEquals(listOf(other), observed().map { it.id })
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertNull(catalog.getValue(gym).groupId)
            assertNull(catalog.getValue(BuiltInCategories.FOOD.id).groupId)
            assertEquals(other, catalog.getValue(book).groupId)
        }

    @Test
    fun delete_alsoDeletesTheBudgetsOfItsCategories_only() =
        runStoreTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            budgets.setBudget(BuiltInCategories.FOOD.id, 100)
            budgets.setBudget(BuiltInCategories.SHOPPING.id, 200)

            assertTrue(groups.deleteEnvelope(food).isSuccess)

            assertEquals(listOf(BuiltInCategories.SHOPPING.id), budgets.observeBudgets().first().map { it.categoryId })
        }

    @Test
    fun delete_unfilesABuiltInMember_andDropsItsBudget() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id
            val limits = mapOf(food to Budget(food, 300))
            val id = groups.saveEnvelope(edit(null, "Mix", members = setOf(food), limits = limits)).getOrThrow()
            assertEquals(
                id,
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == food }
                    .groupId,
            )

            assertTrue(groups.deleteEnvelope(id).isSuccess)

            assertNull(
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == food }
                    .groupId,
            )
            assertEquals(emptyList<Budget>(), budgets.observeBudgets().first().untimed())
        }

    @Test
    fun delete_unknownGroup_isHarmless() =
        runStoreTest {
            groups.addEnvelope("Food")

            assertTrue(groups.deleteEnvelope("nope").isSuccess)
            assertEquals(1, observed().size)
        }

    // ---- category side ----

    @Test
    fun setGroup_onUserAndBuiltInCategory_andClear() =
        runStoreTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            val gym = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING).getOrThrow()

            assertTrue(categories.setGroup(gym, food).isSuccess)
            assertTrue(categories.setGroup(BuiltInCategories.SHOPPING.id, food).isSuccess)
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertEquals(food, catalog.getValue(gym).groupId)
            assertEquals(food, catalog.getValue(BuiltInCategories.SHOPPING.id).groupId)

            assertTrue(categories.setGroup(gym, null).isSuccess)
            assertNull(
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == gym }
                    .groupId,
            )
        }

    @Test
    fun setGroup_unknownCategory_fails() =
        runStoreTest {
            assertTrue(categories.setGroup(CategoryId("ghost"), "g1").exceptionOrNull() is IllegalArgumentException)
        }

    @Test
    fun setGroup_invalidGroupIdShape_fails() =
        runStoreTest {
            assertTrue(categories.setGroup(BuiltInCategories.FOOD.id, "bad id!").isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun addCategory_withGroup_isStored() =
        runStoreTest {
            val food = groups.addEnvelope("Food").getOrThrow()

            val id = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, food).getOrThrow()

            assertEquals(
                food,
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == id }
                    .groupId,
            )
        }

    // ---- budget skip months ----

    @Test
    fun setBudget_storesSkipMonths_andReplacesThemOnNextSet() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id
            budgets.setBudget(food, 100, setOf(8, 1))
            assertEquals(
                setOf(1, 8),
                budgets
                    .observeBudgets()
                    .first()
                    .single()
                    .skipMonths,
            )
            assertEquals(
                listOf(1, 8),
                store.data
                    .first()
                    .budgets
                    .single()
                    .skipMonths,
            )

            budgets.setBudget(food, 100)
            assertEquals(
                emptySet<Int>(),
                budgets
                    .observeBudgets()
                    .first()
                    .single()
                    .skipMonths,
            )
        }

    @Test
    fun setBudget_invalidSkipMonths_fails() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id

            assertTrue(budgets.setBudget(food, 100, setOf(0)).isFailure)
            assertTrue(budgets.setBudget(food, 100, setOf(13)).isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun legacyBudgetWithoutSkipMonths_decodesWithEmptySet() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":4,"revision":1,"budgets":[{"categoryId":"FOOD","limitCents":5}]}""")

            assertEquals(
                emptySet<Int>(),
                newStore()
                    .data
                    .first()
                    .budgets
                    .single()
                    .skipMonths
                    .toSet(),
            )
        }
}

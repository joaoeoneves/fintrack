package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Envelope
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

    private suspend fun observed() = groups.observeEnvelopes().first()

    private suspend fun LocalEnvelopeRepository.addEnvelope(name: String) = saveEnvelope(edit(null, name))

    private fun edit(
        id: String?,
        name: String,
        members: Set<CategoryId> = emptySet(),
    ) = EnvelopeEdit(id, name, members)

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
    fun save_invalidName_failsAndKeepsData() =
        runStoreTest {
            groups.addEnvelope("Food")

            assertTrue(groups.saveEnvelope(edit("g1", "")).isFailure)
            assertEquals(listOf(Envelope("g1", "Food", 0)), observed())
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
}

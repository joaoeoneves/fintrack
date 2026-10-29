package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.google.firestore.v1.Value
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.time.Instant

/** `toCategoryOrNull` / `toFirestoreMap` (internal in CategoryFirestoreMapping.kt) and `importChunks`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CategoryFirestoreMappingTest {
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        ShadowLog.clear()
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun doc(
        id: String,
        fields: Map<String, Value>,
    ): DocumentSnapshot = TestDocumentSnapshots.found(firestore, id = id, fields = fields, collection = "categories")

    private fun str(value: String) = TestDocumentSnapshots.stringValue(value)

    // ---- toCategoryOrNull ----

    @Test
    fun fullUserDoc_isMapped() {
        val category =
            doc(
                "abc",
                mapOf(
                    "name" to str("Gym"),
                    "iconKey" to str("fitness_center"),
                    "colorKey" to str("teal"),
                    "kind" to str("SAVING"),
                    "order" to TestDocumentSnapshots.longValue(7L),
                    "archived" to TestDocumentSnapshots.booleanValue(true),
                ),
            ).toCategoryOrNull()

        assertEquals(CategoryId("abc"), category?.id)
        assertEquals("Gym", category?.customName)
        assertEquals("fitness_center", category?.iconKey)
        assertEquals("teal", category?.colorKey)
        assertEquals(CategoryKind.SAVING, category?.kind)
        assertEquals(7, category?.order)
        assertEquals(true, category?.archived)
        assertEquals(false, category?.builtIn)
    }

    @Test
    fun userDoc_missingFields_fallBackToDefaults() {
        val category = doc("abc", mapOf("name" to str("Gym"), "kind" to str("BOGUS"))).toCategoryOrNull()

        assertEquals("label", category?.iconKey)
        assertEquals("slate", category?.colorKey)
        assertEquals(CategoryKind.SPENDING, category?.kind)
        assertEquals(Int.MAX_VALUE, category?.order)
        assertEquals(false, category?.archived)
    }

    @Test
    fun builtInOverrideDoc_missingFields_fallBackToTheBuiltInsOwnValues() {
        val category =
            doc(
                "INVESTMENTS",
                mapOf("archived" to TestDocumentSnapshots.booleanValue(true)),
            ).toCategoryOrNull()

        assertEquals(BuiltInCategories.INVESTMENTS.iconKey, category?.iconKey)
        assertEquals(BuiltInCategories.INVESTMENTS.colorKey, category?.colorKey)
        assertEquals(CategoryKind.SAVING, category?.kind)
        assertNull(category?.customName)
        assertEquals(true, category?.builtIn)
        assertEquals(true, category?.archived)
    }

    @Test
    fun recurringOverrideDoc_withoutArchivedField_staysArchivedByDefault() {
        val category =
            doc("RECURRING", mapOf("order" to TestDocumentSnapshots.longValue(2L))).toCategoryOrNull()

        assertEquals(true, category?.archived)
        assertEquals(true, category?.builtIn)
    }

    @Test
    fun recurringOverrideDoc_withArchivedFalse_unarchivesIt() {
        val category =
            doc("RECURRING", mapOf("archived" to TestDocumentSnapshots.booleanValue(false))).toCategoryOrNull()

        assertEquals(false, category?.archived)
    }

    @Test
    fun otherBuiltInOverrideDoc_withoutArchivedField_isActive() {
        val category = doc("FOOD", mapOf("order" to TestDocumentSnapshots.longValue(2L))).toCategoryOrNull()

        assertEquals(false, category?.archived)
    }

    @Test
    fun builtInFlagInDoc_isNotTrusted() {
        val spoofed =
            doc("gym1", mapOf("name" to str("Gym"), "builtIn" to TestDocumentSnapshots.booleanValue(true)))
                .toCategoryOrNull()

        assertEquals(false, spoofed?.builtIn)
    }

    @Test
    fun namelessUserDoc_isDropped_andLogged() {
        assertNull(doc("abc", mapOf("iconKey" to str("home"))).toCategoryOrNull())
        assertNull(doc("abd", mapOf("name" to str("   "))).toCategoryOrNull())
        assertTrue(ShadowLog.getLogsForTag("CategoryMapping").isNotEmpty())
    }

    // ---- toFirestoreMap ----

    @Test
    fun toFirestoreMap_writesEveryField_withDerivedBuiltInFlag() {
        val custom = userCategory("abc", "Gym", order = 3, kind = CategoryKind.SAVING).toFirestoreMap()
        val override = BuiltInCategories.FOOD.copy(customName = null).toFirestoreMap()

        assertEquals("Gym", custom["name"])
        assertEquals("SAVING", custom["kind"])
        assertEquals(3, custom["order"])
        assertEquals(false, custom["archived"])
        assertEquals(false, custom["builtIn"])
        assertNotNull(custom["updatedAt"])
        assertTrue(override.containsKey("name"))
        assertNull(override["name"])
        assertEquals(true, override["builtIn"])
        assertEquals(
            setOf("name", "iconKey", "colorKey", "kind", "order", "archived", "builtIn", "groupId", "mealCardByDefault", "updatedAt"),
            override.keys,
        )
    }

    // ---- importChunks ----

    private fun expenseIn(
        index: Int,
        categoryId: String,
    ) = Expense(
        id = "$index",
        name = "e$index",
        amountCents = 1L,
        category =
            if (BuiltInCategories.isBuiltIn(CategoryId(categoryId))) {
                BuiltInCategories.byId(CategoryId(categoryId))!!
            } else {
                userCategory(categoryId)
            },
        date = Instant.EPOCH,
        note = null,
    )

    @Test
    fun importChunks_empty_isEmpty() {
        assertTrue(emptyList<Expense>().importChunks().isEmpty())
    }

    @Test
    fun importChunks_atMost400PerChunk_orderPreserved_nothingLost() {
        val expenses = (0 until 1_001).map { expenseIn(it, "SHOPPING") }

        val chunks = expenses.importChunks()

        assertEquals(listOf(400, 400, 201), chunks.map { it.size })
        assertEquals(expenses, chunks.flatten())
    }

    @Test
    fun importChunks_exactly400_isOneChunk() {
        assertEquals(1, (0 until 400).map { expenseIn(it, "FOOD") }.importChunks().size)
    }

    @Test
    fun importChunks_atMost15DistinctCustomCategories_builtInsDontCount() {
        val expenses =
            (0 until 40).map { expenseIn(it, "cat${it % 20}") } +
                (40 until 60).map { expenseIn(it, "SHOPPING") }

        val chunks = expenses.importChunks()

        assertEquals(expenses, chunks.flatten())
        chunks.forEach { chunk ->
            val custom = chunk.map { it.category.id }.filterNot { BuiltInCategories.isBuiltIn(it) }.toSet()
            assertTrue(custom.size <= 15)
        }
        assertTrue(chunks.size >= 2)
        assertFalse(chunks.any { it.isEmpty() })
    }

    @Test
    fun importChunks_repeatedCustomCategory_staysInOneChunk() {
        val expenses = (0 until 100).map { expenseIn(it, "cat${it % 15}") }

        assertEquals(1, expenses.importChunks().size)
    }
}

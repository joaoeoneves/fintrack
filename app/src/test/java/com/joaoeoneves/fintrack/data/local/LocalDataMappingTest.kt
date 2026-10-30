package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.data.DEFAULT_CATEGORY_COLOR_KEY
import com.joaoeoneves.fintrack.data.DEFAULT_CATEGORY_ICON_KEY
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Mapping paths log via android.util.Log when dropping malformed records, hence Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataMappingTest {
    @Test
    fun expense_malformedDate_isDropped() {
        assertNull(LocalExpense("e", "n", 1, "FOOD", "not-a-date").toDomainOrNull())
        assertNull(LocalExpense("e", "n", 1, "FOOD", "").toDomainOrNull())
    }

    @Test
    fun income_malformedDate_isDropped() {
        assertNull(LocalIncome("i", "s", 1, "2026-13-45").toDomainOrNull())
    }

    @Test
    fun expense_roundTripsNanosRefundAndNote() {
        val raw = LocalExpense("e", "n", 7, "SHOPPING", "2026-01-31T10:15:30.123456789Z", "note", true)
        val domain = raw.toDomainOrNull()!!
        assertEquals(Instant.parse("2026-01-31T10:15:30.123456789Z"), domain.date)
        assertTrue(domain.isRefund)
        assertEquals(BuiltInCategories.SHOPPING, domain.category)
        assertEquals(raw, domain.toLocal())
    }

    @Test
    fun category_customWithoutName_orBlankName_isDropped() {
        assertNull(LocalCategory("x", null, "home", "teal", "SPENDING", 1).toDomainOrNull())
        assertNull(LocalCategory("x", "  ", "home", "teal", "SPENDING", 1).toDomainOrNull())
    }

    @Test
    fun category_builtInWithBlankFieldsAndBadKind_fallsBackToBuiltIn() {
        val c = LocalCategory("INVESTMENTS", null, "", " ", "BOGUS", 3).toDomainOrNull()!!
        assertTrue(c.builtIn)
        assertEquals(BuiltInCategories.INVESTMENTS.iconKey, c.iconKey)
        assertEquals(BuiltInCategories.INVESTMENTS.colorKey, c.colorKey)
        assertEquals(CategoryKind.SAVING, c.kind)
        assertEquals(3, c.order)
    }

    @Test
    fun category_customWithBlankKeysAndBadKind_usesDefaults() {
        val c = LocalCategory("x", "Gym", "", "", "BOGUS", 1).toDomainOrNull()!!
        assertEquals(DEFAULT_CATEGORY_ICON_KEY, c.iconKey)
        assertEquals(DEFAULT_CATEGORY_COLOR_KEY, c.colorKey)
        assertEquals(CategoryKind.SPENDING, c.kind)
        assertFalse(c.builtIn)
        assertEquals("Gym", c.customName)
    }

    @Test
    fun catalog_dropsMalformed_andStillContainsBuiltIns() {
        val doc = LocalDataDocument(categories = listOf(LocalCategory("x", null, "home", "teal", "SPENDING", 1)))
        assertEquals(BuiltInCategories.all, doc.catalog())
    }

    @Test
    fun documentIsEmpty() {
        assertTrue(LocalDataDocument().isEmpty)
        assertFalse(LocalDataDocument(dismissedRecurringSuggestions = listOf("x")).isEmpty)
    }
}

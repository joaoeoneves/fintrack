package com.joaoeoneves.fintrack.data.local

import org.junit.Assert.assertThrows
import org.junit.Test

class RecordValidationTest {
    private val known = setOf("FOOD", "custom_1")
    private val date = "2026-03-10T12:00:00Z"

    private fun exp(
        id: String = "e1",
        name: String = "Lunch",
        amount: Long = 1,
        category: String = "FOOD",
    ) = LocalExpense(id, name, amount, category, date)

    private fun invalid(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    private val emoji = "😀" // one code point, two UTF-16 chars

    @Test
    fun expense_valid_passes() = validateExpense(exp(), known)

    @Test
    fun expense_blankId_rejected() = invalid { validateExpense(exp(id = " "), known) }

    @Test
    fun expense_nameLengthBoundary_countsCodePoints() {
        validateExpense(exp(name = "a".repeat(200)), known)
        validateExpense(exp(name = emoji.repeat(200)), known)
        invalid { validateExpense(exp(name = "a".repeat(201)), known) }
        invalid { validateExpense(exp(name = emoji.repeat(201)), known) }
        invalid { validateExpense(exp(name = ""), known) }
    }

    @Test
    fun expense_amountBoundary() {
        validateExpense(exp(amount = 1), known)
        invalid { validateExpense(exp(amount = 0), known) }
        invalid { validateExpense(exp(amount = -1), known) }
        invalid { validateExpense(exp(amount = Long.MIN_VALUE), known) }
    }

    @Test
    fun expense_noteBoundary() {
        validateExpense(exp().copy(note = "n".repeat(2000)), known)
        validateExpense(exp().copy(note = emoji.repeat(2000)), known)
        validateExpense(exp().copy(note = ""), known)
        invalid { validateExpense(exp().copy(note = "n".repeat(2001)), known) }
        invalid { validateExpense(exp().copy(note = emoji.repeat(2001)), known) }
    }

    @Test
    fun expense_categoryRules() {
        validateExpense(exp(category = "custom_1"), known)
        invalid { validateExpense(exp(category = "OTHER"), known) }
        invalid { validateExpense(exp(category = ""), known) }
        invalid { validateExpense(exp(category = "bad id!"), setOf("bad id!")) }
    }

    @Test
    fun expense_dateMustBeIsoInstant() {
        invalid { validateExpense(exp().copy(date = "yesterday"), known) }
        invalid { validateExpense(exp().copy(date = "2026-03-10"), known) }
        invalid { validateExpense(exp().copy(date = ""), known) }
        validateExpense(exp().copy(date = "2026-03-10T12:00:00.123456789Z"), known)
    }

    @Test
    fun income_rules() {
        validateIncome(LocalIncome("i", "s", 1, date))
        invalid { validateIncome(LocalIncome("", "s", 1, date)) }
        invalid { validateIncome(LocalIncome("i", "", 1, date)) }
        invalid { validateIncome(LocalIncome("i", "s".repeat(201), 1, date)) }
        invalid { validateIncome(LocalIncome("i", "s", 0, date)) }
        invalid { validateIncome(LocalIncome("i", "s", -5, date)) }
        invalid { validateIncome(LocalIncome("i", "s", 1, "nope")) }
        invalid { validateIncome(LocalIncome("i", "s", 1, date, "n".repeat(2001))) }
        validateIncome(LocalIncome("i", "s".repeat(200), 1, date, "n".repeat(2000)))
    }

    private fun cat(
        id: String = "c1",
        name: String? = "Gym",
        icon: String = "home",
        color: String = "teal",
        kind: String = "SPENDING",
    ) = LocalCategory(id, name, icon, color, kind, 0)

    @Test
    fun category_valid_passes() {
        validateCategory(cat())
        validateCategory(cat(id = "A-b_9"))
        validateCategory(cat(id = "FOOD", name = null))
    }

    @Test
    fun category_idShape() {
        validateCategory(cat(id = "a".repeat(64)))
        invalid { validateCategory(cat(id = "a".repeat(65))) }
        invalid { validateCategory(cat(id = "")) }
        invalid { validateCategory(cat(id = "has space")) }
        invalid { validateCategory(cat(id = "dot.dot")) }
        invalid { validateCategory(cat(id = "slash/x")) }
    }

    @Test
    fun category_nameBoundary() {
        validateCategory(cat(name = "a".repeat(50)))
        validateCategory(cat(name = "😀".repeat(50)))
        invalid { validateCategory(cat(name = "a".repeat(51))) }
        invalid { validateCategory(cat(name = "😀".repeat(51))) }
        invalid { validateCategory(cat(name = "")) }
    }

    @Test
    fun category_userCategoryNeedsName_builtInDoesNot() {
        invalid { validateCategory(cat(id = "custom", name = null)) }
        invalid { validateCategory(cat(id = "custom", name = "   ")) }
        invalid { validateCategory(cat(id = "custom", name = " \t\n")) }
        validateCategory(cat(id = "SHOPPING", name = null))
    }

    @Test
    fun category_keyShapes() {
        validateCategory(cat(icon = "a".repeat(32), color = "b".repeat(32)))
        validateCategory(cat(icon = "swap_horiz1"))
        invalid { validateCategory(cat(icon = "a".repeat(33))) }
        invalid { validateCategory(cat(color = "b".repeat(33))) }
        invalid { validateCategory(cat(icon = "")) }
        invalid { validateCategory(cat(icon = "Home")) }
        invalid { validateCategory(cat(icon = "a-b")) }
        invalid { validateCategory(cat(color = "Teal")) }
        invalid { validateCategory(cat(color = "te al")) }
    }

    @Test
    fun category_kindMustBeKnown() {
        validateCategory(cat(kind = "SAVING"))
        invalid { validateCategory(cat(kind = "spending")) }
        invalid { validateCategory(cat(kind = "BOGUS")) }
    }

    @Test
    fun knownCategoryIds_includesBuiltInsAndStored() {
        val ids = knownCategoryIds(LocalDataDocument(categories = listOf(cat(id = "c9"))))
        assert(ids.containsAll(listOf("TRANSFER", "INVESTMENTS", "SHOPPING", "FOOD", "RECURRING", "c9")))
    }
}

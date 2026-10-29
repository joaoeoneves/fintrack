package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.ui.common.FALLBACK_COLOR_KEY
import com.joaoeoneves.fintrack.ui.common.categoryColorKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeColorsTest {
    private fun cat(
        id: String,
        color: String,
        group: String?,
    ) = Category(CategoryId(id), id, "i", color, CategoryKind.SPENDING, 0, false, false, group)

    private val palette = categoryColorKeys.filter { it != FALLBACK_COLOR_KEY }

    @Test
    fun colorKeyOf_usesFirstCategoryOfTheEnvelope() {
        val categories = listOf(cat("x", "red", "other"), cat("a", "violet", "g1"), cat("b", "blue", "g1"))
        assertEquals("violet", envelopeColorKeyOf("g1", categories))
    }

    @Test
    fun colorKeyOf_noEnvelope_emptyOrUnknownEnvelope_isSlate() {
        val categories = listOf(cat("a", "violet", null), cat("b", "blue", "g1"))
        assertEquals(FALLBACK_COLOR_KEY, envelopeColorKeyOf(NO_ENVELOPE, categories))
        assertEquals(FALLBACK_COLOR_KEY, envelopeColorKeyOf("empty", categories))
        assertEquals(FALLBACK_COLOR_KEY, envelopeColorKeyOf("g1", emptyList()))
    }

    @Test
    fun colorKeysOf_distinctColours_keepOwn() {
        val categories = listOf(cat("a", palette[0], "g1"), cat("b", palette[1], "g2"))
        assertEquals(listOf(palette[0], palette[1]), envelopeColorKeysOf(listOf("g1", "g2"), categories))
    }

    @Test
    fun colorKeysOf_collision_secondGetsFirstUnusedPaletteKey() {
        val categories = listOf(cat("a", palette[0], "g1"), cat("b", palette[0], "g2"), cat("c", palette[0], "g3"))
        val keys = envelopeColorKeysOf(listOf("g1", "g2", "g3"), categories)
        assertEquals(palette[0], keys[0])
        assertEquals(palette[1], keys[1])
        assertEquals(palette[2], keys[2])
    }

    @Test
    fun colorKeysOf_collisionSkipsKeysAlreadyTaken() {
        val categories = listOf(cat("a", palette[0], "g1"), cat("b", palette[0], "g2"), cat("c", palette[1], "g3"))
        val keys = envelopeColorKeysOf(listOf("g1", "g2", "g3"), categories)
        // g2 takes palette[1] first (unused at that point); g3 then collides with it and moves on.
        assertEquals(listOf(palette[0], palette[1], palette[2]), keys)
    }

    @Test
    fun colorKeysOf_slateNeverCollides() {
        val keys = envelopeColorKeysOf(listOf(NO_ENVELOPE, "empty", "none"), emptyList())
        assertEquals(listOf(FALLBACK_COLOR_KEY, FALLBACK_COLOR_KEY, FALLBACK_COLOR_KEY), keys)
        val mixed = envelopeColorKeysOf(listOf("g1", NO_ENVELOPE), listOf(cat("a", palette[0], "g1")))
        assertEquals(FALLBACK_COLOR_KEY, mixed[1])
        assertNotEquals(FALLBACK_COLOR_KEY, mixed[0])
    }

    @Test
    fun colorKeysOf_emptyInput_isEmpty() {
        assertTrue(envelopeColorKeysOf(emptyList(), emptyList()).isEmpty())
    }
}

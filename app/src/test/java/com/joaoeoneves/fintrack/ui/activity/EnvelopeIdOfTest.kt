package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [envelopeIdOf]: a category's envelope, only while that envelope still exists. */
class EnvelopeIdOfTest {
    private val filed = BuiltInCategories.FOOD.copy(groupId = "env1")

    @Test
    fun existingEnvelope_isReturned() {
        assertEquals("env1", envelopeIdOf(filed, setOf("env1", "env2")))
    }

    @Test
    fun deletedEnvelope_meansNone() {
        assertNull(envelopeIdOf(filed, setOf("env2")))
        assertNull(envelopeIdOf(filed, emptySet()))
    }

    @Test
    fun ungroupedCategory_meansNone() {
        assertNull(envelopeIdOf(BuiltInCategories.FOOD, setOf("env1")))
    }

    @Test
    fun emptyGroupId_isNotMatchedUnlessItExists() {
        assertNull(envelopeIdOf(BuiltInCategories.FOOD.copy(groupId = ""), setOf("env1")))
    }
}

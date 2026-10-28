package com.joaoeoneves.fintrack.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The [Activity] route's initial-value arguments. */
class ActivityRouteTest {
    @Test
    fun defaults_haveNoInitialValues() {
        val route = Activity()

        assertNull(route.period)
        assertNull(route.type)
        assertNull(route.category)
        assertNull(route.envelope)
    }

    @Test
    fun envelope_isCarriedIndependentlyOfTheOtherArguments() {
        val route = Activity(period = "current", envelope = "env1")

        assertEquals("env1", route.envelope)
        assertNull(route.category)
        assertEquals(Activity(period = "current", envelope = "env1"), route)
    }

    @Test
    fun emptyEnvelope_isDistinctFromNone() {
        assertEquals("", Activity(envelope = "").envelope)
        assertNull(Activity().envelope)
    }
}

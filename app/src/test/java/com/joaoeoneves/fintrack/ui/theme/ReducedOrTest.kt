package com.joaoeoneves.fintrack.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.tween
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedOrTest {
    @Test
    fun reduced_givesSnap() {
        val spec = tween<Float>(300)
        val result = reducedOr(true, spec)
        assertTrue(result is SnapSpec<Float>)
        assertNotSame(spec, result)
    }

    @Test
    fun notReduced_givesSameInstance() {
        val spec = tween<Float>(300)
        assertSame(spec, reducedOr(false, spec))
    }
}

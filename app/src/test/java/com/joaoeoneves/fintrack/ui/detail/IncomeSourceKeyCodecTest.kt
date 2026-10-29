package com.joaoeoneves.fintrack.ui.detail

import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.ui.activity.analytics.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomeSourceKeyCodecTest {
    private fun roundTrip(key: IncomeSourceKey) = assertEquals(key, decodeIncomeSourceKey(key.encode()))

    @Test
    fun managedComponentRow_roundTrips() {
        SalaryComponent.entries.forEach { roundTrip(IncomeSourceKey.Row(it, null, null)) }
    }

    @Test
    fun extraRow_roundTrips() {
        roundTrip(IncomeSourceKey.Row(SalaryComponent.EXTRA, "extra-1", null))
    }

    @Test
    fun manualLineRow_roundTrips() {
        roundTrip(IncomeSourceKey.Row(null, null, "line-1"))
    }

    @Test
    fun named_roundTrips_includingPipesAndEmpty() {
        roundTrip(IncomeSourceKey.Named("gift"))
        roundTrip(IncomeSourceKey.Named("a|b"))
        roundTrip(IncomeSourceKey.Named(""))
    }

    @Test
    fun malformed_isNull() {
        assertNull(decodeIncomeSourceKey(""))
        assertNull(decodeIncomeSourceKey("gift"))
        assertNull(decodeIncomeSourceKey("row:"))
        assertNull(decodeIncomeSourceKey("row:SALARY"))
        assertNull(decodeIncomeSourceKey("row:SALARY|x"))
        assertNull(decodeIncomeSourceKey("row:NOT_A_COMPONENT||"))
    }
}

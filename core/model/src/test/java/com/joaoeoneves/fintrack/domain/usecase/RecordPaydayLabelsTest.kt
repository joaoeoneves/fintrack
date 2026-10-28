package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.managedExtraKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [RecordPayday] names the incomes of generated lines in the app language and leaves other lines alone. */
class RecordPaydayLabelsTest : RecordPaydayTestBase() {
    private val today = LocalDate.of(2026, 10, 15)
    private val pt = FakeSalaryLineLabelsProvider.PORTUGUESE
    private val extraKey = managedExtraKey("bonus", 10)

    private fun lines() =
        listOf(
            line("manual"),
            line(MANAGED_NET_SALARY, managedKey = MANAGED_NET_SALARY),
            line(MANAGED_MEAL, managedKey = MANAGED_MEAL),
            line(extraKey, managedKey = extraKey),
        )

    private suspend fun sources() = stored().associate { it.id to it.source }

    @Test
    fun receive_managedLinesUseTheLocalizedLabel_manualAndExtraKeepTheirOwn() =
        runTest {
            labels.language = pt

            val request = ReceiveRequest(lines(), oct, 21, today, today, utc, null)
            assertEquals(true, useCase.receive(request).isSuccess)

            val sources = sources()
            assertEquals("Salário líquido", sources["pay_${MANAGED_NET_SALARY}_202610"])
            assertEquals("Subsídio de refeição", sources["pay_${MANAGED_MEAL}_202610"])
            assertEquals("Line manual", sources["pay_manual_202610"])
            assertEquals("Line $extraKey", sources["pay_${extraKey}_202610"])
        }

    @Test
    fun runDue_managedLinesUseTheLocalizedLabel_manualAndExtraKeepTheirOwn() =
        runTest {
            labels.language = pt
            lines().forEach { plan.savePlannedIncome(it) }
            cursors(*lines().map { it.id to YearMonth.of(2026, 9) }.toTypedArray())

            useCase.runDue(LocalDate.of(2026, 10, 30), utc)

            val sources = sources()
            assertEquals("Salário líquido", sources["pay_${MANAGED_NET_SALARY}_202610"])
            assertEquals("Subsídio de refeição", sources["pay_${MANAGED_MEAL}_202610"])
            assertEquals("Line manual", sources["pay_manual_202610"])
            assertEquals("Line $extraKey", sources["pay_${extraKey}_202610"])
        }

    @Test
    fun receive_inEnglish_usesTheEnglishLabel() =
        runTest {
            val net = line(MANAGED_NET_SALARY, managedKey = MANAGED_NET_SALARY)
            val request = ReceiveRequest(listOf(net), oct, 21, today, today, utc, null)
            useCase.receive(request)

            assertEquals("Net salary", stored().single().source)
        }
}

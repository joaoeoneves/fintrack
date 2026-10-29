package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [RecordPayday.receive]: the user marking a row as received. */
class RecordPaydayReceiveTest : RecordPaydayTestBase() {
    private val today = LocalDate.of(2026, 10, 15)

    private fun request(
        vararg lines: PlannedIncome,
        date: LocalDate = today,
        override: Long? = null,
    ) = ReceiveRequest(lines.toList(), oct, 21, date, today, utc, override)

    @Test
    fun receive_doesNotFlagForReview() =
        runTest {
            useCase.receive(request(line("p1")))

            assertFalse(stored().single().needsReview)
        }

    @Test
    fun singleLine_isRecorded_withDeterministicIdAndNoonDate() =
        runTest {
            val result = useCase.receive(request(line("p1", 123_45)))

            assertTrue(result.isSuccess)
            val income = stored().single()
            assertEquals("pay_p1_202610", income.id)
            assertEquals("Line p1", income.source)
            assertEquals(123_45L, income.amountCents)
            assertEquals(at("2026-10-15T12:00:00Z"), income.date)
        }

    @Test
    fun receivingDoesNotMoveTheCursor() =
        runTest {
            useCase.receive(request(line("p1")))

            assertTrue(automation.recorded.isEmpty())
            assertEquals(null, cursor())
        }

    @Test
    fun dateEqualToToday_isAllowed_pastDateToo() =
        runTest {
            assertTrue(useCase.receive(request(line("a"), date = today)).isSuccess)
            assertTrue(useCase.receive(request(line("b"), date = LocalDate.of(2026, 10, 1))).isSuccess)
        }

    @Test
    fun futureDate_isRejected_andNothingIsWritten() =
        runTest {
            val result = useCase.receive(request(line("p1"), date = today.plusDays(1)))

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(stored().isEmpty())
        }

    @Test
    fun multiLineRow_recordsEveryPayingLine_atItsOwnAmount() =
        runTest {
            val net = line("net", 200_000, MANAGED_NET_SALARY)
            val idle = line("idle", 9_000, MANAGED_NET_SALARY).copy(skipMonths = setOf(10))
            val meal = line("meal", 1_000, MANAGED_MEAL).copy(basis = IncomeBasis.PER_WORKING_DAY)

            val result = useCase.receive(request(net, idle, meal))

            assertTrue(result.isSuccess)
            assertEquals(
                listOf("pay_meal_202610" to 21_000L, "pay_net_202610" to 200_000L),
                stored().map { it.id to it.amountCents },
            )
        }

    @Test
    fun amountOverride_singleLine_replacesTheAmount() =
        runTest {
            val result = useCase.receive(request(line("p1", 100_000), override = 90_000))

            assertTrue(result.isSuccess)
            assertEquals(90_000L, stored().single().amountCents)
        }

    @Test
    fun amountOverride_onSeveralLines_isRejected() =
        runTest {
            val result = useCase.receive(request(line("a"), line("b"), override = 5))

            assertTrue(result.isFailure)
            assertTrue(stored().isEmpty())
        }

    @Test
    fun amountOverride_zeroOrNegative_isRejected() =
        runTest {
            assertTrue(useCase.receive(request(line("a"), override = 0)).isFailure)
            assertTrue(useCase.receive(request(line("a"), override = -1)).isFailure)
            assertTrue(stored().isEmpty())
        }

    @Test
    fun nothingPaying_isRejected() =
        runTest {
            assertTrue(useCase.receive(request()).isFailure)
            assertTrue(useCase.receive(request(line("a", 0))).isFailure)
            assertTrue(useCase.receive(request(line("a").copy(skipMonths = setOf(10)))).isFailure)
            assertTrue(stored().isEmpty())
        }

    @Test
    fun amountOverride_onSingleSkippedLine_isRejected_andNothingIsWritten() =
        runTest {
            val skipped = line("a").copy(skipMonths = setOf(10))

            val result = useCase.receive(request(skipped, override = 90_000))

            assertTrue(result.isFailure)
            assertTrue(stored().isEmpty())
        }

    @Test
    fun amountOverride_withOneSkippedLine_appliesToTheSingleActiveLine() =
        runTest {
            val skipped = line("a").copy(skipMonths = setOf(10))

            val result = useCase.receive(request(skipped, line("b", 100_000), override = 90_000))

            assertTrue(result.isSuccess)
            val income = stored().single()
            assertEquals("pay_b_202610", income.id)
            assertEquals(90_000L, income.amountCents)
        }
}

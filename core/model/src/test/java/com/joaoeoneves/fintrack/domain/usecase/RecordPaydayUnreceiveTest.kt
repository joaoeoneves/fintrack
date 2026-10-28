package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [RecordPayday.unreceive]: taking a received row back to not received. */
class RecordPaydayUnreceiveTest : RecordPaydayTestBase() {
    private val today = LocalDate.of(2026, 10, 15)

    private suspend fun receive(vararg lines: com.joaoeoneves.fintrack.domain.model.PlannedIncome) =
        useCase.receive(ReceiveRequest(lines.toList(), oct, 21, today, today, utc)).getOrThrow()

    @Test
    fun deletesEachLinesPaydayIncome() =
        runTest {
            val a = line("a")
            val b = line("b")
            receive(a, b)
            assertEquals(2, stored().size)

            assertTrue(useCase.unreceive(listOf(a, b), oct).isSuccess)

            assertTrue(stored().isEmpty())
        }

    @Test
    fun onlyTouchesTheGivenLinesAndMonth() =
        runTest {
            val a = line("a")
            val b = line("b")
            receive(a, b)

            useCase.unreceive(listOf(a), oct)
            useCase.unreceive(listOf(b), oct.plusMonths(1))

            assertEquals(listOf("pay_b_202610"), stored().map { it.id })
        }

    @Test
    fun lineWithNothingRecorded_isSkippedWithoutFailure() =
        runTest {
            val a = line("a")
            receive(a)

            val result = useCase.unreceive(listOf(line("ghost"), a), oct)

            assertTrue(result.isSuccess)
            assertTrue(stored().isEmpty())
        }

    @Test
    fun emptyLines_isASuccessNoOp() =
        runTest {
            assertTrue(useCase.unreceive(emptyList(), oct).isSuccess)
        }

    @Test
    fun deleteFailure_isReturned_andStopsTheLoop() =
        runTest {
            val boom = IllegalStateException("offline")
            val failing = FailingDelete(incomes, failOn = "pay_b_202610", error = boom)
            val failingUseCase = RecordPayday(plan, monthSettings, failing, automation, labels)
            val a = line("a")
            val b = line("b")
            val c = line("c")
            receive(a, b, c)

            val result = failingUseCase.unreceive(listOf(a, b, c), oct)

            assertSame(boom, result.exceptionOrNull())
            assertEquals(listOf("pay_b_202610", "pay_c_202610"), stored().map { it.id })
        }

    @Test
    fun afterUnreceive_runDueForAMonthAtOrBeforeTheCursor_doesNotRecordAgain() =
        runTest {
            cursors("a" to oct)
            plan.savePlannedIncome(line("a"))
            receive(line("a"))

            useCase.unreceive(listOf(line("a")), oct)
            val created = useCase.runDue(LocalDate.of(2026, 10, 31), utc)

            assertEquals(0, created)
            assertTrue(stored().isEmpty())
            assertEquals(oct, cursor("a"))
        }

    @Test
    fun deletesTheLegacyRecIncomeOfAMigratedLine() =
        runTest {
            val migrated = line("rinc_abc")
            incomes.addIncome(Income("rec_abc_20261005", "Old", 500L, at("2026-10-05T12:00:00Z"), null))
            incomes.addIncome(Income("rec_abc_20260905", "Old", 500L, at("2026-09-05T12:00:00Z"), null))

            assertTrue(useCase.unreceive(listOf(migrated), oct, utc).isSuccess)

            assertEquals(listOf("rec_abc_20260905"), stored().map { it.id })
        }

    @Test
    fun deletesBothThePaydayAndTheLegacyIncomeWhenBothExist() =
        runTest {
            val migrated = line("rinc_abc")
            receive(migrated)
            incomes.addIncome(Income("rec_abc_20261005", "Old", 500L, at("2026-10-05T12:00:00Z"), null))

            useCase.unreceive(listOf(migrated), oct, utc)

            assertTrue(stored().isEmpty())
        }
}

/** Delegates to [delegate] but fails deleting [failOn]. */
private class FailingDelete(
    private val delegate: FakeIncomeRepository,
    private val failOn: String,
    private val error: Throwable,
) : IncomeRepository by delegate {
    override suspend fun deleteIncome(id: String): Result<Unit> {
        val failing = id == failOn
        return if (failing) Result.failure(error) else delegate.deleteIncome(id)
    }
}

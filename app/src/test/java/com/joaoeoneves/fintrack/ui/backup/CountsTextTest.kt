package com.joaoeoneves.fintrack.ui.backup

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The record summary shared by the backup and migration dialogs. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en")
class CountsTextTest {
    @get:Rule
    val rule = createComposeRule()

    private fun render(counts: BackupCounts): String {
        var text = ""
        rule.setContent { text = countsText(counts) }
        rule.waitForIdle()
        return text
    }

    @Test
    fun countsEveryKind_includingSavingsPlansAndEnvelopes() {
        val text =
            render(
                BackupCounts(
                    expenses = 1,
                    income = 2,
                    budgets = 99,
                    categories = 4,
                    plannedIncome = 5,
                    recurring = 6,
                    categoryGroups = 3,
                    goals = 7,
                    salaryProfile = 1,
                ),
            )

        assertEquals(
            "Expenses: 1\nIncome: 2\nEnvelopes: 3\nCategories: 4\nExpected income: 5\nRecurring: 6\n" +
                "Savings plans: 7\nSalary profile: Yes",
            text,
        )
    }

    @Test
    fun withoutSalaryProfile_saysNo_andZeroCountsAreShown() {
        val text = render(BackupCounts(0, 0, 0, 0, 0, 0))

        assertEquals(true, text.endsWith("Savings plans: 0\nSalary profile: No"))
    }
}


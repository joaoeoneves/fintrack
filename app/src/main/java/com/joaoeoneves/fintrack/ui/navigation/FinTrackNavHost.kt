package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.ui.activity.ActivityCallbacks
import com.joaoeoneves.fintrack.ui.activity.ActivityScreen
import com.joaoeoneves.fintrack.ui.common.newTransactionArg
import com.joaoeoneves.fintrack.ui.dashboard.DashboardCallbacks
import com.joaoeoneves.fintrack.ui.dashboard.DashboardScreen
import com.joaoeoneves.fintrack.ui.designsystem.LocalAppSnackbar
import com.joaoeoneves.fintrack.ui.expense.addedit.AddEditExpenseScreen
import com.joaoeoneves.fintrack.ui.income.IncomeTabScreen
import com.joaoeoneves.fintrack.ui.income.addedit.AddEditIncomeScreen
import com.joaoeoneves.fintrack.ui.plan.envelopes.EnvelopeEditorScreen
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryProfileScreen
import com.joaoeoneves.fintrack.ui.plan.salary.setup.SalarySetupScreen
import com.joaoeoneves.fintrack.ui.recurring.AddEditRecurringScreen
import com.joaoeoneves.fintrack.ui.recurring.RecurringListScreen
import com.joaoeoneves.fintrack.ui.settings.SettingsScreen
import com.joaoeoneves.fintrack.ui.settings.categories.CategoriesScreen
import com.joaoeoneves.fintrack.ui.spending.SpendingTabNavigation
import com.joaoeoneves.fintrack.ui.spending.SpendingTabScreen
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion

/** The app's navigation graph: one nested graph per bottom-bar tab plus the screens shared by all tabs. */
@Composable
fun FinTrackNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val reducedMotion = LocalReducedMotion.current
    val transitions = remember(reducedMotion) { NavTransitions(reducedMotion) }
    NavHost(
        navController = navController,
        startDestination = HomeGraph,
        modifier = modifier,
        enterTransition = { transitions.enter(initialState.destination, targetState.destination) },
        exitTransition = { transitions.exit(initialState.destination, targetState.destination) },
        popEnterTransition = { transitions.popEnter(initialState.destination, targetState.destination) },
        popExitTransition = { transitions.popExit(initialState.destination, targetState.destination) },
    ) {
        homeRoutes(navController)
        activityRoutes(navController)
        incomeRoutes(navController)
        spendingRoutes(navController)
        sharedRoutes(navController)
        detailRoutes(navController)
        investmentRoutes(navController)
        envelopeRoutes(navController)
        formRoutes(navController, transitions)
    }
}

private fun NavGraphBuilder.homeRoutes(navController: NavHostController) {
    navigation<HomeGraph>(startDestination = Home) {
        composable<Home> {
            DashboardScreen(
                callbacks =
                    DashboardCallbacks(
                        onAddExpense = { period ->
                            navController.navigate(AddEditExpense(period = period.newTransactionArg()))
                        },
                        onAddIncome = { period ->
                            navController.navigate(AddEditIncome(period = period.newTransactionArg()))
                        },
                        onOpenSettings = { navController.navigate(Settings) },
                        onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
                        onOpenIncome = { id -> navController.navigate(AddEditIncome(incomeId = id)) },
                        onOpenIncomeTab = { period -> navController.navigateToTabWith(IncomeHome(period.encode())) },
                        onOpenSpendingTab = { period ->
                            navController.navigateToTabWith(SpendingHome(period.encode()))
                        },
                        onOpenInvestments = { period -> navController.navigate(InvestmentDetail(period.encode())) },
                        onOpenRecurring = { navController.navigate(RecurringList) },
                        onOpenStartStep = { step -> navController.navigate(routeForStartStep(step)) },
                        onCreateRecurring = { candidate ->
                            navController.navigate(
                                AddEditRecurring(
                                    prefillName = candidate.name,
                                    prefillAmountCents = candidate.amountCents,
                                    prefillCategoryId = candidate.categoryId.value,
                                    prefillDay = candidate.dayOfMonth,
                                    prefillLastMonth = candidate.lastMonth.toString(),
                                ),
                            )
                        },
                        onQuickAdd = { chip ->
                            navController.navigate(
                                AddEditExpense(
                                    prefillName = chip.name,
                                    prefillAmountCents = chip.amountCents,
                                    prefillCategoryId = chip.categoryId.value,
                                ),
                            )
                        },
                    ),
            )
        }
    }
}

/** Where a "Get started" checklist step leads: the "add" editor itself (pay day edits existing income). */
private fun routeForStartStep(step: StartStep): Any =
    when (step) {
        StartStep.SALARY -> SalarySetup
        StartStep.BILLS -> AddEditRecurring()
        StartStep.PAYDAY -> IncomeHome()
        StartStep.FIRST_EXPENSE -> AddEditExpense()
    }

private fun NavGraphBuilder.activityRoutes(navController: NavHostController) {
    navigation<ActivityGraph>(startDestination = Activity()) {
        composable<Activity> {
            ActivityScreen(
                callbacks =
                    ActivityCallbacks(
                        onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
                        onOpenIncome = { id -> navController.navigate(AddEditIncome(incomeId = id)) },
                        onAddExpense = { period ->
                            navController.navigate(AddEditExpense(period = period.newTransactionArg()))
                        },
                        onAddIncome = { period ->
                            navController.navigate(AddEditIncome(period = period.newTransactionArg()))
                        },
                        onOpenSettings = { navController.navigate(Settings) },
                    ),
            )
        }
    }
}

private fun NavGraphBuilder.incomeRoutes(navController: NavHostController) {
    navigation<IncomeGraph>(startDestination = IncomeHome()) {
        composable<IncomeHome> {
            IncomeTabScreen(
                onOpenSettings = { navController.navigate(Settings) },
                onOpenSalaryProfile = { hasProfile ->
                    navController.navigate(if (hasProfile) EditSalaryProfile else SalarySetup)
                },
                onOpenSource = { key, period -> navController.navigate(IncomeSourceDetail(key, period.encode())) },
                onAddExpense = { navController.navigate(AddEditExpense(period = it.newTransactionArg())) },
                onAddIncome = { navController.navigate(AddEditIncome(period = it.newTransactionArg())) },
            )
        }
    }
}

private fun NavGraphBuilder.spendingRoutes(navController: NavHostController) {
    navigation<SpendingGraph>(startDestination = SpendingHome()) {
        composable<SpendingHome> {
            SpendingTabScreen(
                onOpenSettings = { navController.navigate(Settings) },
                onAddExpense = { navController.navigate(AddEditExpense(period = it.newTransactionArg())) },
                onAddIncome = { navController.navigate(AddEditIncome(period = it.newTransactionArg())) },
                navigation =
                    SpendingTabNavigation(
                        onOpenCategory = { id, period -> navController.navigate(CategoryDetail(id, period.encode())) },
                        onOpenCategories = { navController.navigate(Categories()) },
                        onOpenGroup = { id -> navController.navigate(EditEnvelope(envelopeId = id)) },
                        onOpenRecurring = { navController.navigate(RecurringList) },
                    ),
            )
        }
    }
}

/** The group editor, pushed on top of whichever tab is current. */
private fun NavGraphBuilder.envelopeRoutes(navController: NavHostController) {
    composable<EditEnvelope> {
        EnvelopeEditorScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
}

/** Screens pushed on top of whichever tab is current; the bottom bar is hidden on all of them. */
private fun NavGraphBuilder.sharedRoutes(navController: NavHostController) {
    composable<Settings> {
        SettingsScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onDataDeleted = { navController.navigateToTab(TopLevelTab.HOME) },
        )
    }
    composable<Categories> {
        CategoriesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
    composable<RecurringList> {
        RecurringListScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onOpenEditor = { id -> navController.navigate(AddEditRecurring(recurringId = id)) },
            onOpenIncome = { navController.navigateToTabWith(IncomeHome()) },
        )
    }
    salaryRoutes(navController)
}

/** The salary profile editor and set-up wizard. */
private fun NavGraphBuilder.salaryRoutes(navController: NavHostController) {
    composable<EditSalaryProfile> {
        SalaryProfileScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
    composable<SalarySetup> {
        val showMessage = LocalAppSnackbar.current
        val doneMessage = stringResource(R.string.salary_setup_done)
        SalarySetupScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onDone =
                dropUnlessResumed {
                    navController.popBackStack()
                    showMessage(doneMessage)
                },
            onOpenAdvanced = {
                navController.navigate(EditSalaryProfile) { popUpTo<SalarySetup> { inclusive = true } }
            },
        )
    }
}

/** Add/edit forms: they enter from the bottom and leave downwards. */
private fun NavGraphBuilder.formRoutes(
    navController: NavHostController,
    transitions: NavTransitions,
) {
    composable<AddEditRecurring>(
        enterTransition = { transitions.enter(initialState.destination, targetState.destination) },
        exitTransition = { transitions.exit(initialState.destination, targetState.destination) },
        popEnterTransition = { transitions.popEnter(initialState.destination, targetState.destination) },
        popExitTransition = { transitions.popExit(initialState.destination, targetState.destination) },
    ) {
        AddEditRecurringScreen(
            onSaved = dropUnlessResumed { navController.popBackStack() },
            onCancel = dropUnlessResumed { navController.popBackStack() },
        )
    }
    composable<AddEditExpense>(
        enterTransition = { transitions.enter(initialState.destination, targetState.destination) },
        exitTransition = { transitions.exit(initialState.destination, targetState.destination) },
        popEnterTransition = { transitions.popEnter(initialState.destination, targetState.destination) },
        popExitTransition = { transitions.popExit(initialState.destination, targetState.destination) },
    ) {
        AddEditExpenseScreen(
            onSaved = dropUnlessResumed { navController.popBackStack() },
            onDeleted = dropUnlessResumed { navController.popBackStack() },
            onCancel = dropUnlessResumed { navController.popBackStack() },
            onRecordRefund = { id -> navController.navigate(AddEditExpense(refundOf = id)) },
        )
    }
    composable<AddEditIncome>(
        enterTransition = { transitions.enter(initialState.destination, targetState.destination) },
        exitTransition = { transitions.exit(initialState.destination, targetState.destination) },
        popEnterTransition = { transitions.popEnter(initialState.destination, targetState.destination) },
        popExitTransition = { transitions.popExit(initialState.destination, targetState.destination) },
    ) {
        AddEditIncomeScreen(
            onSaved = dropUnlessResumed { navController.popBackStack() },
            onDeleted = dropUnlessResumed { navController.popBackStack() },
            onCancel = dropUnlessResumed { navController.popBackStack() },
        )
    }
}

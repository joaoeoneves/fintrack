package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.ui.activity.ActivityCallbacks
import com.joaoeoneves.fintrack.ui.activity.ActivityScreen
import com.joaoeoneves.fintrack.ui.dashboard.DashboardCallbacks
import com.joaoeoneves.fintrack.ui.dashboard.DashboardScreen
import com.joaoeoneves.fintrack.ui.expense.addedit.AddEditExpenseScreen
import com.joaoeoneves.fintrack.ui.income.addedit.AddEditIncomeScreen
import com.joaoeoneves.fintrack.ui.plan.IncomeScreen
import com.joaoeoneves.fintrack.ui.plan.PlanNavigation
import com.joaoeoneves.fintrack.ui.plan.PlanScreen
import com.joaoeoneves.fintrack.ui.plan.envelopes.EnvelopeEditorScreen
import com.joaoeoneves.fintrack.ui.plan.envelopes.EnvelopesScreen
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryProfileScreen
import com.joaoeoneves.fintrack.ui.plan.salary.setup.SalarySetupScreen
import com.joaoeoneves.fintrack.ui.recurring.AddEditRecurringScreen
import com.joaoeoneves.fintrack.ui.recurring.RecurringListScreen
import com.joaoeoneves.fintrack.ui.savings.SavingsPlanEditorScreen
import com.joaoeoneves.fintrack.ui.savings.SavingsScreen
import com.joaoeoneves.fintrack.ui.settings.SettingsScreen
import com.joaoeoneves.fintrack.ui.settings.categories.CategoriesScreen
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
        planRoutes(navController)
        savingsRoutes(navController)
        sharedRoutes(navController)
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
                        onAddExpense = { navController.navigate(AddEditExpense()) },
                        onAddIncome = { navController.navigate(AddEditIncome()) },
                        onOpenSettings = { navController.navigate(Settings) },
                        onOpenExpense = { id -> navController.navigate(AddEditExpense(expenseId = id)) },
                        onOpenIncome = { id -> navController.navigate(AddEditIncome(incomeId = id)) },
                        onOpenActivity = { period -> navController.navigateToTabWith(Activity(period.encode())) },
                        onOpenPlan = { navController.navigateToTab(TopLevelTab.PLAN) },
                        onOpenRecurring = { navController.navigate(RecurringList) },
                        onOpenStartStep = { step -> navController.navigate(routeForStartStep(step)) },
                    ),
            )
        }
    }
}

/** Where a "Get started" checklist step leads. */
private fun routeForStartStep(step: StartStep): Any =
    when (step) {
        StartStep.SALARY -> SalarySetup
        StartStep.ENVELOPES -> PlanEnvelopes
        StartStep.BILLS -> RecurringList
        StartStep.SAVINGS_PLAN -> EditSavingsPlan()
        StartStep.PAYDAY -> PlanIncome()
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
                        onAddExpense = { navController.navigate(AddEditExpense()) },
                        onAddIncome = { navController.navigate(AddEditIncome()) },
                        onOpenSettings = { navController.navigate(Settings) },
                    ),
            )
        }
    }
}

private fun NavGraphBuilder.planRoutes(navController: NavHostController) {
    navigation<PlanGraph>(startDestination = PlanHome) {
        composable<PlanHome> {
            PlanScreen(
                onOpenSettings = { navController.navigate(Settings) },
                navigation =
                    PlanNavigation(
                        onOpenIncome = { period -> navController.navigate(PlanIncome(month = period.encode())) },
                        onOpenEnvelopes = { navController.navigate(PlanEnvelopes) },
                        onOpenEnvelope = { id -> navController.navigate(EditEnvelope(envelopeId = id)) },
                        onOpenRecurring = { navController.navigate(RecurringList) },
                        onOpenCategories = { navController.navigate(Categories) },
                    ),
            )
        }
        composable<PlanIncome> {
            IncomeScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenSalaryProfile = { hasProfile ->
                    navController.navigate(if (hasProfile) EditSalaryProfile else SalarySetup)
                },
            )
        }
    }
}

private fun NavGraphBuilder.savingsRoutes(navController: NavHostController) {
    navigation<SavingsGraph>(startDestination = SavingsHome) {
        composable<SavingsHome> {
            SavingsScreen(
                onOpenSettings = { navController.navigate(Settings) },
                onOpenPlan = { id -> navController.navigate(EditSavingsPlan(id = id)) },
            )
        }
    }
}

/** The envelope list and editor, pushed from the Plan tab. */
private fun NavGraphBuilder.envelopeRoutes(navController: NavHostController) {
    composable<PlanEnvelopes> {
        EnvelopesScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onOpenEditor = { id -> navController.navigate(EditEnvelope(envelopeId = id)) },
        )
    }
    composable<EditSavingsPlan> {
        SavingsPlanEditorScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
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
            onOpenIncome = { navController.navigate(PlanIncome()) },
        )
    }
    composable<EditSalaryProfile> {
        SalaryProfileScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
    composable<SalarySetup> {
        SalarySetupScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onDone = dropUnlessResumed { navController.popBackStack() },
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

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
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.ui.activity.ActivityCallbacks
import com.joaoeoneves.fintrack.ui.activity.ActivityScreen
import com.joaoeoneves.fintrack.ui.dashboard.DashboardCallbacks
import com.joaoeoneves.fintrack.ui.dashboard.DashboardScreen
import com.joaoeoneves.fintrack.ui.expense.addedit.AddEditExpenseScreen
import com.joaoeoneves.fintrack.ui.income.addedit.AddEditIncomeScreen
import com.joaoeoneves.fintrack.ui.insights.InsightsScreen
import com.joaoeoneves.fintrack.ui.plan.PlanScreen
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryProfileScreen
import com.joaoeoneves.fintrack.ui.recurring.AddEditRecurringScreen
import com.joaoeoneves.fintrack.ui.recurring.RecurringListScreen
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
        enterTransition = {
            if (isTabSwitch(initialState.destination, targetState.destination)) {
                transitions.fadeEnter()
            } else {
                transitions.enter()
            }
        },
        exitTransition = {
            when {
                isTabSwitch(initialState.destination, targetState.destination) -> transitions.fadeExit()
                targetState.destination.isForm() -> transitions.exitUnderForm()
                else -> transitions.exit()
            }
        },
        popEnterTransition = {
            when {
                isTabSwitch(initialState.destination, targetState.destination) -> transitions.fadeEnter()
                initialState.destination.isForm() -> transitions.popEnterUnderForm()
                else -> transitions.popEnter()
            }
        },
        popExitTransition = {
            if (isTabSwitch(initialState.destination, targetState.destination)) {
                transitions.fadeExit()
            } else {
                transitions.popExit()
            }
        },
    ) {
        homeRoutes(navController)
        activityRoutes(navController)
        planRoutes(navController)
        insightsRoutes(navController)
        sharedRoutes(navController, transitions)
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
                        onOpenRecurring = {
                            navController.navigateToTab(TopLevelTab.PLAN)
                            navController.navigate(RecurringList)
                        },
                    ),
            )
        }
    }
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
                onOpenRecurring = { navController.navigate(RecurringList) },
                onOpenSalaryProfile = { navController.navigate(EditSalaryProfile) },
            )
        }
    }
}

private fun NavGraphBuilder.insightsRoutes(navController: NavHostController) {
    navigation<InsightsGraph>(startDestination = Insights()) {
        composable<Insights> {
            InsightsScreen(onOpenSettings = { navController.navigate(Settings) })
        }
    }
}

/** Screens pushed on top of whichever tab is current; the bottom bar is hidden on all of them. */
private fun NavGraphBuilder.sharedRoutes(
    navController: NavHostController,
    transitions: NavTransitions,
) {
    composable<Settings> {
        SettingsScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onOpenCategories = { navController.navigate(Categories) },
            onOpenRecurring = { navController.navigate(RecurringList) },
        )
    }
    composable<Categories> {
        CategoriesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
    composable<RecurringList> {
        RecurringListScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onOpenEditor = { id -> navController.navigate(AddEditRecurring(recurringId = id)) },
        )
    }
    composable<EditSalaryProfile> {
        SalaryProfileScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
    composable<AddEditRecurring>(
        enterTransition = { transitions.enterFromBottom() },
        popExitTransition = { transitions.popExitToBottom() },
    ) {
        AddEditRecurringScreen(
            onSaved = dropUnlessResumed { navController.popBackStack() },
            onCancel = dropUnlessResumed { navController.popBackStack() },
        )
    }
    composable<AddEditExpense>(
        enterTransition = { transitions.enterFromBottom() },
        popExitTransition = { transitions.popExitToBottom() },
    ) {
        AddEditExpenseScreen(
            onSaved = dropUnlessResumed { navController.popBackStack() },
            onCancel = dropUnlessResumed { navController.popBackStack() },
        )
    }
    composable<AddEditIncome>(
        enterTransition = { transitions.enterFromBottom() },
        popExitTransition = { transitions.popExitToBottom() },
    ) {
        AddEditIncomeScreen(
            onSaved = dropUnlessResumed { navController.popBackStack() },
            onCancel = dropUnlessResumed { navController.popBackStack() },
        )
    }
}

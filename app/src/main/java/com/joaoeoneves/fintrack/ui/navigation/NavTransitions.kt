package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.joaoeoneves.fintrack.ui.theme.Motion

/** Shared-axis screen transitions built from the [Motion] tokens. */
internal class NavTransitions(
    private val reducedMotion: Boolean,
) {
    /** The motion when navigating forward from [from] to [to]. */
    private fun forward(
        from: NavDestination,
        to: NavDestination,
    ): NavMotion = navMotionFor(from.tabOrdinal(), to.tabOrdinal(), to.isForm(), reducedMotion)

    /** The motion when popping from [from] back to [to]. */
    private fun back(
        from: NavDestination,
        to: NavDestination,
    ): NavMotion = navMotionFor(from.tabOrdinal(), to.tabOrdinal(), from.isForm(), reducedMotion)

    fun enter(
        from: NavDestination,
        to: NavDestination,
    ): EnterTransition = enterFor(forward(from, to))

    fun exit(
        from: NavDestination,
        to: NavDestination,
    ): ExitTransition = exitFor(forward(from, to))

    fun popEnter(
        from: NavDestination,
        to: NavDestination,
    ): EnterTransition = popEnterFor(back(from, to))

    fun popExit(
        from: NavDestination,
        to: NavDestination,
    ): ExitTransition = popExitFor(back(from, to))

    /** Entry of an add/edit form route (per-route override). */
    fun enterFromBottom(): EnterTransition = enterFor(formMotion())

    /** Pop-exit of an add/edit form route (per-route override). */
    fun popExitToBottom(): ExitTransition = popExitFor(formMotion())

    private fun formMotion() = navMotionFor(fromTab = null, toTab = null, toIsForm = true, reduced = reducedMotion)
}

/** The tab ordinal when this is a tab root, else `null`. */
private fun NavDestination.tabOrdinal(): Int? = if (isTopLevel()) currentTab()?.ordinal else null

/** Add/edit forms enter from the bottom; the screen beneath them fades instead of sliding sideways. */
internal fun NavDestination.isForm(): Boolean {
    val forms = listOf(hasRoute<AddEditExpense>(), hasRoute<AddEditIncome>(), hasRoute<AddEditRecurring>())
    return forms.any { it }
}

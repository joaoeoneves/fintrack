package com.joaoeoneves.fintrack.ui.common

/**
 * Test tags exposed as UI Automator resource ids (see `testTagsAsResourceId` in MainActivity).
 * Used by the baseline profile generator; mirrored in `GeneratorTags` of the `:baselineprofile`
 * module, so keep the two in sync.
 */
object TestTags {
    const val CONTINUE_AS_GUEST = "continue_as_guest"
    const val DAYS_OFF_SAVE = "days_off_save"
    const val DAYS_OFF_UNTIL = "days_off_until"
    const val DASHBOARD_LIST = "dashboard_list"
    const val DASHBOARD_ADD_EXPENSE = "dashboard_add_expense"
    const val ACCOUNT_AVATAR = "account_avatar"
    const val SEE_ALL_ACTIVITY = "see_all_activity"
    const val ACTIVITY_LIST = "activity_list"
    const val NAV_HOME = "nav_home"
    const val NAV_ACTIVITY = "nav_activity"
    const val NAV_INCOME = "nav_income"
    const val NAV_SPENDING = "nav_spending"
    const val RECURRING_CARD = "recurring_card"
    const val RECURRING_CONFIRM = "recurring_confirm"
    const val RECURRING_SKIP = "recurring_skip"
    const val RECURRING_MORE = "recurring_more"
    const val RECURRING_CHANGE = "recurring_change"
    const val RECURRING_ADD = "recurring_add"
    const val START_CHECKLIST = "start_checklist"
    const val REVIEW_INBOX = "review_inbox"
    const val QUICK_ADD = "quick_add"
    const val RECURRING_SUGGESTION = "recurring_suggestion"
    const val SKELETON = "skeleton"

    /** Tag of the checklist row of the step named [stepName] (a `StartStep` name). */
    fun startStep(stepName: String): String = "start_step_$stepName"
}

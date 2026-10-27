package com.joaoeoneves.fintrack.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

private const val SIGN_IN_TIMEOUT_MS = 5_000L
private const val DASHBOARD_TIMEOUT_MS = 15_000L
private const val SCREEN_TIMEOUT_MS = 5_000L
private const val FLING_MARGIN = 0.2f

/** Mirrors `com.joaoeoneves.fintrack.ui.common.TestTags` in the app module; keep in sync. */
object GeneratorTags {
    const val CONTINUE_AS_GUEST = "continue_as_guest"
    const val DASHBOARD_LIST = "dashboard_list"
    const val DASHBOARD_ADD_EXPENSE = "dashboard_add_expense"
    const val DASHBOARD_SETTINGS = "dashboard_settings"
    const val SEE_ALL_EXPENSES = "see_all_expenses"
    const val EXPENSE_LIST = "expense_list"
}

/** Enters guest mode when the sign-in screen shows; otherwise assumes a session already exists. */
internal fun MacrobenchmarkScope.enterApp() {
    if (device.wait(Until.hasObject(By.res(GeneratorTags.CONTINUE_AS_GUEST)), SIGN_IN_TIMEOUT_MS)) {
        device.findObject(By.res(GeneratorTags.CONTINUE_AS_GUEST)).click()
    }
}

internal fun MacrobenchmarkScope.waitForDashboard() {
    val found = device.wait(Until.hasObject(By.res(GeneratorTags.DASHBOARD_LIST)), DASHBOARD_TIMEOUT_MS)
    check(found) {
        "Dashboard (resource id '${GeneratorTags.DASHBOARD_LIST}') did not appear within " +
            "${DASHBOARD_TIMEOUT_MS}ms; is the app installed and past sign-in?"
    }
}

internal fun MacrobenchmarkScope.flingDashboard() {
    val list = device.findObject(By.res(GeneratorTags.DASHBOARD_LIST)) ?: return
    list.setGestureMargin(device.displayWidth.times(FLING_MARGIN).toInt())
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

/** Taps the element with [tag] if present, waits for [expectedTag] to show, then goes back. */
internal fun MacrobenchmarkScope.openAndBack(
    tag: String,
    expectedTag: String? = null,
) {
    val target = device.findObject(By.res(tag)) ?: return
    target.click()
    if (expectedTag != null) {
        device.wait(Until.hasObject(By.res(expectedTag)), SCREEN_TIMEOUT_MS)
    } else {
        device.waitForIdle()
    }
    device.pressBack()
    waitForDashboard()
}

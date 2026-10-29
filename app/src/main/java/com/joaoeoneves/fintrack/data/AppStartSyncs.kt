package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.data.automation.AutomationSync
import com.joaoeoneves.fintrack.data.preferences.PreferencesSync
import com.joaoeoneves.fintrack.data.salary.ManagedIncomeSync
import javax.inject.Inject
import javax.inject.Singleton

/** The long-lived background syncs the app starts once at launch, in a fixed order. */
@Singleton
class AppStartSyncs
    @Inject
    constructor(
        private val preferencesSync: PreferencesSync,
        private val managedIncomeSync: ManagedIncomeSync,
        private val automationSync: AutomationSync,
    ) {
        fun startAll() {
            preferencesSync.start()
            managedIncomeSync.start()
            automationSync.start()
        }
    }

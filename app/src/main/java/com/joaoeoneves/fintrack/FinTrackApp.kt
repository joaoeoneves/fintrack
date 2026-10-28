package com.joaoeoneves.fintrack

import android.app.Application
import com.joaoeoneves.fintrack.data.automation.AutomationSync
import com.joaoeoneves.fintrack.data.preferences.PreferencesSync
import com.joaoeoneves.fintrack.data.salary.ManagedIncomeSync
import com.joaoeoneves.fintrack.ui.theme.NightModeSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinTrackApp : Application() {
    @Inject
    lateinit var preferencesSync: PreferencesSync

    @Inject
    lateinit var managedIncomeSync: ManagedIncomeSync

    @Inject
    lateinit var automationSync: AutomationSync

    override fun onCreate() {
        super.onCreate()
        NightModeSync.restore(this)
        preferencesSync.start()
        managedIncomeSync.start()
        automationSync.start()
    }
}

package com.joaoeoneves.fintrack

import android.app.Application
import com.joaoeoneves.fintrack.data.automation.AutomationSync
import com.joaoeoneves.fintrack.data.migration.EnvelopeMigrationSync
import com.joaoeoneves.fintrack.data.migration.IncomeMigrationSync
import com.joaoeoneves.fintrack.data.migration.SavingsPlanMigrationSync
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
    lateinit var envelopeMigrationSync: EnvelopeMigrationSync

    @Inject
    lateinit var savingsPlanMigrationSync: SavingsPlanMigrationSync

    @Inject
    lateinit var incomeMigrationSync: IncomeMigrationSync

    @Inject
    lateinit var automationSync: AutomationSync

    override fun onCreate() {
        super.onCreate()
        NightModeSync.restore(this)
        preferencesSync.start()
        managedIncomeSync.start()
        envelopeMigrationSync.start()
        savingsPlanMigrationSync.start()
        incomeMigrationSync.start()
        automationSync.start()
    }
}

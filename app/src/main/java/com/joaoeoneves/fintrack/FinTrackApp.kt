package com.joaoeoneves.fintrack

import android.app.Application
import com.joaoeoneves.fintrack.data.AppStartSyncs
import com.joaoeoneves.fintrack.ui.theme.NightModeSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinTrackApp : Application() {
    @Inject
    lateinit var appStartSyncs: AppStartSyncs

    override fun onCreate() {
        super.onCreate()
        NightModeSync.restore(this)
        appStartSyncs.startAll()
    }
}

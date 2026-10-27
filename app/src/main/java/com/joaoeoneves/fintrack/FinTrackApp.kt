package com.joaoeoneves.fintrack

import android.app.Application
import com.joaoeoneves.fintrack.data.preferences.PreferencesSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinTrackApp : Application() {
    @Inject
    lateinit var preferencesSync: PreferencesSync

    override fun onCreate() {
        super.onCreate()
        preferencesSync.start()
    }
}

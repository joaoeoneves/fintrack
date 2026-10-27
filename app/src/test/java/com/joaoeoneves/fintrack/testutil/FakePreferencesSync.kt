package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.data.preferences.PreferenceField
import com.joaoeoneves.fintrack.data.preferences.PreferencesSync

/** Recording [PreferencesSync]: keeps every push in order; optional hook runs on each push. */
class FakePreferencesSync : PreferencesSync {
    val pushes = mutableListOf<Pair<PreferenceField, String>>()
    var startCalls = 0
        private set
    var onPush: (PreferenceField, String) -> Unit = { _, _ -> }

    override fun start() {
        startCalls++
    }

    override fun push(
        field: PreferenceField,
        value: String,
    ) {
        pushes += field to value
        onPush(field, value)
    }
}

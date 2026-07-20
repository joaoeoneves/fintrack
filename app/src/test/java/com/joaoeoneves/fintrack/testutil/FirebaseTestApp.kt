package com.joaoeoneves.fintrack.testutil

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.joaoeoneves.fintrack.BuildConfig

/**
 * Ensures a [FirebaseApp] exists for the given Robolectric [context], so tests can construct real
 * `FirebaseFirestore.getInstance(app)` / `FirebaseAuth.getInstance(app)` instances (needed to satisfy
 * the Firestore repositories' non-nullable constructor parameters) without ever making a network
 * call. The options come from `BuildConfig.TEST_FIREBASE_*`, generated at build time from this dev's
 * local (gitignored) `google-services.json` -- see `app/build.gradle.kts` -- rather than a hardcoded
 * copy in source, matching how the rest of this project keeps Firebase client config out of git.
 */
object FirebaseTestApp {
    fun ensureInitialized(context: Context): FirebaseApp {
        val existing = FirebaseApp.getApps(context)
        if (existing.isNotEmpty()) {
            return existing[0]
        }
        return FirebaseApp.initializeApp(
            context,
            FirebaseOptions
                .Builder()
                .setApplicationId(BuildConfig.TEST_FIREBASE_APPLICATION_ID)
                .setProjectId(BuildConfig.TEST_FIREBASE_PROJECT_ID)
                .setApiKey(BuildConfig.TEST_FIREBASE_API_KEY)
                .build(),
        )
    }
}

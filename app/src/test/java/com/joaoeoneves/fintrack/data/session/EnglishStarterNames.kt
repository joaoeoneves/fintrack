package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.local.GuestStarterSeeder
import com.joaoeoneves.fintrack.data.local.LocalDataStore
import com.joaoeoneves.fintrack.domain.model.StarterCategories
import com.joaoeoneves.fintrack.domain.model.StarterCategoryNames
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File

/** English starter names, like the app would resolve them. */
internal object EnglishStarterNames : StarterCategoryNames {
    override fun names(): Map<String, String> =
        mapOf(
            StarterCategories.HOME_ID to "Home",
            StarterCategories.TRANSPORT_ID to "Transport",
            StarterCategories.HEALTH_ID to "Health",
            StarterCategories.LEISURE_ID to "Leisure",
        )
}

/** A guest [LocalDataStore] on a real temp file, configured like production. */
@OptIn(ExperimentalSerializationApi::class)
internal fun guestStoreIn(dir: File) =
    LocalDataStore(
        File(dir, "guest-data.json"),
        Dispatchers.IO,
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        },
    )

internal fun guestSeederIn(dir: File) = GuestStarterSeeder(guestStoreIn(dir), EnglishStarterNames)

package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.StarterCategories
import com.joaoeoneves.fintrack.domain.model.StarterCategoryNames
import javax.inject.Inject
import javax.inject.Singleton

/** Gives a brand-new guest the starter categories, but only while the store is completely untouched. */
@Singleton
class GuestStarterSeeder
    @Inject
    constructor(
        private val store: LocalDataStore,
        private val names: StarterCategoryNames,
    ) {
        suspend fun seedIfUntouched(): Result<Unit> =
            store.update { doc ->
                if (doc.copy(revision = 0, schemaVersion = LOCAL_SCHEMA_VERSION) != LocalDataDocument()) {
                    doc to Unit
                } else {
                    val starters =
                        names.names().let { byId ->
                            StarterCategories.specs.mapNotNull { spec ->
                                byId[spec.id]?.let {
                                    LocalCategory(
                                        id = spec.id,
                                        name = it,
                                        iconKey = spec.iconKey,
                                        colorKey = spec.colorKey,
                                        kind = CategoryKind.SPENDING.name,
                                        order = spec.order,
                                    )
                                }
                            }
                        }
                    doc.copy(categories = starters) to Unit
                }
            }
    }

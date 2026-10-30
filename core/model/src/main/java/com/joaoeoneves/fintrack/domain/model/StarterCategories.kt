package com.joaoeoneves.fintrack.domain.model

/**
 * The few spending categories a brand-new user (account or guest) starts with. They are ordinary user categories
 * (editable, archivable, deletable) with fixed ids, so seeding twice never duplicates them. Their display names come
 * from string resources; [knownNames] lists those names in every supported language so an untouched starter can be
 * told from a renamed one (keep in sync with `strings_starter.xml`).
 */

object StarterCategories {
    // Starters follow the five built-ins (orders 0..4) in the default order.
    private const val FIRST_ORDER = 5

    const val HOME_ID = "starter_home"
    const val TRANSPORT_ID = "starter_transport"
    const val HEALTH_ID = "starter_health"
    const val LEISURE_ID = "starter_leisure"

    /** What a starter looks like apart from its name. */
    data class Spec(
        val id: String,
        val iconKey: String,
        val colorKey: String,
        val order: Int,
    )

    val specs: List<Spec> =
        listOf(
            Triple(HOME_ID, "home", "teal"),
            Triple(TRANSPORT_ID, "directions_car", "orange"),
            Triple(HEALTH_ID, "medical_services", "red"),
            Triple(LEISURE_ID, "sports_esports", "violet"),
        ).mapIndexed { index, (id, icon, color) -> Spec(id, icon, color, FIRST_ORDER + index) }

    val ids: Set<String> = specs.mapTo(mutableSetOf()) { it.id }

    /** The names each starter gets in English and European Portuguese. */
    val knownNames: Map<String, Set<String>> =
        mapOf(
            HOME_ID to setOf("Home", "Casa"),
            TRANSPORT_ID to setOf("Transport", "Transportes"),
            HEALTH_ID to setOf("Health", "Saúde"),
            LEISURE_ID to setOf("Leisure", "Lazer"),
        )

    fun specOf(id: String): Spec? = specs.find { it.id == id }
}

/** Localized names of the [StarterCategories], read from string resources in the app's current language. */
interface StarterCategoryNames {
    /** The display name of each starter, by id. */
    fun names(): Map<String, String>
}

package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BuiltInCategoryNames

/** [BuiltInCategoryNames] for tests, seeded with the real English and pt-PT default names. */
class FakeBuiltInCategoryNames(
    private val names: Map<CategoryId, Set<String>> = DEFAULT_NAMES,
) : BuiltInCategoryNames {
    override fun localizedNames(): Map<CategoryId, Set<String>> = names

    companion object {
        val DEFAULT_NAMES: Map<CategoryId, Set<String>> =
            mapOf(
                BuiltInCategories.TRANSFER.id to setOf("Transfer", "Transferência"),
                BuiltInCategories.INVESTMENTS.id to setOf("Investments", "Investimentos"),
                BuiltInCategories.SHOPPING.id to setOf("Shopping", "Compras"),
                BuiltInCategories.FOOD.id to setOf("Food", "Alimentação"),
                BuiltInCategories.RECURRING.id to setOf("Recurring", "Recorrente"),
            )
    }
}

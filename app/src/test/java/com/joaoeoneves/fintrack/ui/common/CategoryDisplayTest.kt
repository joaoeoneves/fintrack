package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for the [BuiltInCategories.icon] extension property in CategoryDisplay.kt, mirroring
 * the fixed per-category mapping used by the pie chart legend and expense rows.
 */
class CategoryDisplayTest {
    @Test
    fun transfer_mapsToSwapHorizIcon() {
        assertEquals(Icons.Filled.SwapHoriz, BuiltInCategories.TRANSFER.icon)
    }

    @Test
    fun investments_mapsToTrendingUpIcon() {
        assertEquals(Icons.AutoMirrored.Filled.TrendingUp, BuiltInCategories.INVESTMENTS.icon)
    }

    @Test
    fun shopping_mapsToShoppingCartIcon() {
        assertEquals(Icons.Filled.ShoppingCart, BuiltInCategories.SHOPPING.icon)
    }

    @Test
    fun food_mapsToRestaurantIcon() {
        assertEquals(Icons.Filled.Restaurant, BuiltInCategories.FOOD.icon)
    }

    @Test
    fun recurring_mapsToAutorenewIcon() {
        assertEquals(Icons.Filled.Autorenew, BuiltInCategories.RECURRING.icon)
    }

    @Test
    fun everyCategory_hasANonNullIcon() {
        // Guards against the `when` becoming non-exhaustive (e.g. a category added to the enum
        // without a corresponding branch here would throw at runtime rather than fail to compile,
        // since Kotlin's `when` over a sealed/enum without an `else` is only checked exhaustively
        // by the compiler -- this is a runtime safety net for that same guarantee).
        for (category in BuiltInCategories.all) {
            assertNotNull("Expected a non-null icon for $category", category.icon)
        }
    }

    @Test
    fun allCategories_mapToDistinctIcons() {
        // No two categories should accidentally share the same icon -- every fixed category must be
        // visually distinguishable by icon alone.
        val icons = BuiltInCategories.all.map { it.icon }

        assertEquals(
            "Expected each ExpenseCategory to map to a distinct icon, but found duplicates",
            BuiltInCategories.all.size,
            icons.distinct().size,
        )
    }

    @Test
    fun iconMapping_isStableAcrossRepeatedAccess() {
        // The `icon` property recomputes the `when` on every access (it's a getter, not cached);
        // confirm repeated access is nonetheless referentially stable, since ImageVector equality
        // for Material icons is backed by a lazily-initialized singleton per icon.
        val first = BuiltInCategories.TRANSFER.icon
        val second = BuiltInCategories.TRANSFER.icon

        assertEquals(first, second)
    }
}

package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreCategoryRepositorySignedOutTest {
    private lateinit var repository: FirestoreCategoryRepository

    @Before
    fun setUp() {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        repository = FirestoreCategoryRepository(FirebaseFirestore.getInstance(app), FirebaseAuth.getInstance(app))
    }

    @Test
    fun observeCategories_signedOut_emitsBuiltIns() =
        runBlocking {
            assertEquals(BuiltInCategories.all, repository.observeCategories().first())
        }

    @Test
    fun writes_signedOut_failWithNotSignedIn() =
        runBlocking {
            val add = repository.addCategory("Gym", "home", "teal", CategoryKind.SPENDING)
            val archive = repository.setArchived(BuiltInCategories.FOOD.id, true)
            val delete = repository.deleteCategory(CategoryId("x"))

            listOf(add.exceptionOrNull(), archive.exceptionOrNull(), delete.exceptionOrNull()).forEach {
                assertTrue(it is IllegalStateException)
            }
        }
}

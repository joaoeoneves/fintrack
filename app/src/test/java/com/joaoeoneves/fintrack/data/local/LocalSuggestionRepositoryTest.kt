package com.joaoeoneves.fintrack.data.local

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSuggestionRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalSuggestionRepository(store) }

    @Test
    fun startsEmpty() =
        runStoreTest {
            assertTrue(repo.observeDismissedRecurring().first().isEmpty())
        }

    @Test
    fun dismiss_addsTheKey_andPersistsIt() =
        runStoreTest {
            assertTrue(repo.dismissRecurring("netflix").isSuccess)

            assertEquals(setOf("netflix"), repo.observeDismissedRecurring().first())
            assertEquals(listOf("netflix"), diskDoc().dismissedRecurringSuggestions)
        }

    @Test
    fun dismissingTwice_keepsOneEntry() =
        runStoreTest {
            repo.dismissRecurring("netflix")
            repo.dismissRecurring("netflix")
            repo.dismissRecurring("gym")

            assertEquals(listOf("netflix", "gym"), diskDoc().dismissedRecurringSuggestions)
        }

    @Test
    fun undismiss_removesOnlyThatKey_andAMissingKeyIsANoOp() =
        runStoreTest {
            repo.dismissRecurring("netflix")
            repo.dismissRecurring("gym")

            assertTrue(repo.undismissRecurring("netflix").isSuccess)
            assertTrue(repo.undismissRecurring("never").isSuccess)

            assertEquals(setOf("gym"), repo.observeDismissedRecurring().first())
        }

    @Test
    fun undismiss_thenDismissAgain_works() =
        runStoreTest {
            repo.dismissRecurring("netflix")
            repo.undismissRecurring("netflix")
            repo.dismissRecurring("netflix")

            assertEquals(setOf("netflix"), repo.observeDismissedRecurring().first())
        }
}

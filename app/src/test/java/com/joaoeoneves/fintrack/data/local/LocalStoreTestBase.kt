package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** Shared fixtures for the guest-mode local store tests: a temp dir, the production Json config, fake ids. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
abstract class LocalStoreTestBase {
    @get:Rule
    val tmp = TemporaryFolder()

    protected val dispatcher = StandardTestDispatcher()

    protected val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    protected val dataFile: File get() = File(tmp.root, "guest-data.json")

    protected fun newStore(file: File = dataFile) = LocalDataStore(file, dispatcher, json)

    protected fun runStoreTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher, testBody = block)

    protected fun filesInDir(): List<String> =
        tmp.root
            .list()
            .orEmpty()
            .sorted()

    protected fun decode(text: String): LocalDataDocument = json.decodeFromString(LocalDataDocument.serializer(), text)

    protected fun diskDoc(): LocalDataDocument = decode(dataFile.readText())

    protected suspend fun LocalDataStore.seedCategory(
        id: String,
        name: String? = "Gym",
        order: Int = 0,
    ) = update {
        it.copy(categories = it.categories + LocalCategory(id, name, "home", "teal", "SPENDING", order)) to Unit
    }

    protected class SequenceIds(
        private val prefix: String = "id",
    ) : IdGenerator {
        private var n = 0

        override fun newId(): String = "$prefix${++n}"
    }

    protected fun expense(
        id: String = "e1",
        name: String = "Lunch",
        amountCents: Long = 1000,
        categoryId: String = "FOOD",
        date: Instant = Instant.parse("2026-03-10T12:00:00Z"),
    ) = Expense(
        id = id,
        name = name,
        amountCents = amountCents,
        category =
            BuiltInCategories.byId(
                com.joaoeoneves.fintrack.domain.model
                    .CategoryId(categoryId),
            )
                ?: com.joaoeoneves.fintrack.domain.model.Category
                    .unknown(
                        com.joaoeoneves.fintrack.domain.model
                            .CategoryId(categoryId),
                    ),
        date = date,
        note = null,
    )

    protected fun income(
        id: String = "i1",
        source: String = "Salary",
        amountCents: Long = 100_000,
        date: Instant = Instant.parse("2026-03-10T12:00:00Z"),
        note: String? = null,
    ) = Income(id = id, source = source, amountCents = amountCents, date = date, note = note)
}

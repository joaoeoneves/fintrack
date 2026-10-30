package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Envelopes from a pre-23 file: the removed amount/funding/timing fields are ignored and never written back. */
class LocalEnvelopeRepositoryLegacyTest : LocalStoreTestBase() {
    private val legacyFile =
        """{"schemaVersion":22,"revision":4,"categoryGroups":[{"id":"g1","name":"Food","order":0,""" +
            """"amountCents":5000,"fundedByLineId":"meal","startMonth":"2026-03",""" +
            """"versions":[{"from":"2026-03","amountCents":5000}]}]}"""

    private val store by lazy { newStore() }
    private val groups by lazy { LocalEnvelopeRepository(store, SequenceIds("g")) }

    @Test
    fun legacyFields_areDroppedOnRead() =
        runStoreTest {
            dataFile.writeText(legacyFile)

            assertEquals(listOf(Envelope("g1", "Food", 0)), groups.observeEnvelopes().first())
        }

    @Test
    fun saveAfterLegacyRead_stripsTheRemovedKeysFromDisk() =
        runStoreTest {
            dataFile.writeText(legacyFile)

            assertTrue(groups.saveEnvelope(EnvelopeEdit("g1", "Groceries", emptySet())).isSuccess)

            val text = dataFile.readText()
            assertEquals(listOf(Envelope("g1", "Groceries", 0)), groups.observeEnvelopes().first())
            assertFalse(text.contains("fundedByLineId"))
            assertFalse(text.contains("versions"))
            assertFalse(text.contains("startMonth"))
            assertFalse(text.contains("\"amountCents\""))
        }
}

package com.joaoeoneves.fintrack.data.local

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File

class LocalDataStoreFailureTest : LocalStoreTestBase() {
    @Test
    fun update_validationFailure_isFailureAndLeavesDataUntouched() =
        runStoreTest {
            val store = newStore()
            store.update { it.copy(dismissedRecurringSuggestions = listOf("d1")) to Unit }
            val result =
                store.update<Unit> {
                    require(false) { "nope" }
                    it to Unit
                }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(1L, store.data.first().revision)
            assertEquals(1L, diskDoc().revision)
        }

    @Test
    fun writeFailure_isFailureAndDataUnchanged() =
        runStoreTest {
            val dir = File(tmp.root, "sub").apply { mkdirs() }
            val store = newStore(File(dir, "guest-data.json"))
            store.update { it.copy(dismissedRecurringSuggestions = listOf("d1")) to Unit }
            dir.setWritable(false)
            try {
                assumeFalse(
                    "directory permissions are not enforced",
                    runCatching { File(dir, "probe").createNewFile() }.getOrDefault(false),
                )
                val result = store.update { it.copy(dismissedRecurringSuggestions = listOf("d2")) to Unit }
                assertTrue(result.isFailure)
                assertEquals(1L, store.data.first().revision)
                assertEquals(
                    "d1",
                    store.data
                        .first()
                        .dismissedRecurringSuggestions[0],
                )
            } finally {
                dir.setWritable(true)
            }
        }

    @Test
    fun writeFailure_whenTargetIsNonEmptyDirectory_isFailureAndTmpCleaned() =
        runStoreTest {
            val target = File(tmp.root, "guest-data.json").apply { mkdirs() }
            File(target, "x").writeText("x")
            val store = newStore(target)
            // Loading a directory fails to read as a file (IOException), so first load fails.
            val result = store.update { it.copy(dismissedRecurringSuggestions = listOf("d1")) to Unit }
            assertTrue(result.isFailure)
            assertFalse(File(tmp.root, "guest-data.json.tmp").exists())
        }

    @Test
    fun clearWithBackup_copiesFileThenWritesEmptyWithNextRevision() =
        runStoreTest {
            val store = newStore()
            store.update { it.copy(dismissedRecurringSuggestions = listOf("d1")) to Unit }
            val original = dataFile.readText()
            assertTrue(store.clearWithBackup().isSuccess)
            assertEquals(original, File(tmp.root, "guest-data.migrated.json").readText())
            val doc = store.data.first()
            assertEquals(LocalDataDocument(revision = 2), doc)
            assertEquals(doc, diskDoc())
        }

    @Test
    fun clearWithBackup_overwritesSingleBackupSlot() =
        runStoreTest {
            val store = newStore()
            store.update { it.copy(dismissedRecurringSuggestions = listOf("d1")) to Unit }
            store.clearWithBackup()
            store.update { it.copy(dismissedRecurringSuggestions = listOf("d9")) to Unit }
            store.clearWithBackup()
            val backup = decode(File(tmp.root, "guest-data.migrated.json").readText())
            assertEquals("d9", backup.dismissedRecurringSuggestions[0])
            assertEquals(listOf("guest-data.json", "guest-data.migrated.json"), filesInDir())
        }

    @Test
    fun clearWithBackup_noFile_skipsCopyAndWritesEmptyDoc() =
        runStoreTest {
            val store = newStore()
            assertTrue(store.clearWithBackup().isSuccess)
            assertFalse(File(tmp.root, "guest-data.migrated.json").exists())
            assertEquals(1L, diskDoc().revision)
        }

    @Test
    fun clearWithBackup_emptyDocument_keepsEarlierBackup_andStillBumpsRevision() =
        runStoreTest {
            val store = newStore()
            store.update { it.copy(dismissedRecurringSuggestions = listOf("d7")) to Unit }
            store.clearWithBackup()
            val backup = File(tmp.root, "guest-data.migrated.json").readText()

            // The store is now empty (revision 2); clearing again must not overwrite the backup with it.
            assertTrue(store.clearWithBackup().isSuccess)

            assertEquals(backup, File(tmp.root, "guest-data.migrated.json").readText())
            assertEquals(LocalDataDocument(revision = 3), diskDoc())
            assertEquals(LocalDataDocument(revision = 3), store.data.first())
        }

    @Test
    fun clearWithBackup_existingEmptyFile_noBackupCreated() =
        runStoreTest {
            val store = newStore()
            store.update { it.copy(dismissedRecurringSuggestions = listOf("d1")) to Unit }
            store.replaceAll(LocalDataDocument())
            assertTrue(diskDoc().dismissedRecurringSuggestions.isEmpty())

            assertTrue(store.clearWithBackup().isSuccess)

            assertFalse(File(tmp.root, "guest-data.migrated.json").exists())
            assertEquals(3L, diskDoc().revision)
        }

    @Test
    fun ioErrorOnFirstLoad_isNotMovedAside_andFlowThrows() =
        runStoreTest {
            val asDir = File(tmp.root, "guest-data.json").apply { mkdirs() }
            val store = newStore(asDir)
            val flowResult = runCatching { store.data.first() }
            assertNotNull(flowResult.exceptionOrNull())
            assertEquals(listOf("guest-data.json"), filesInDir())
        }
}

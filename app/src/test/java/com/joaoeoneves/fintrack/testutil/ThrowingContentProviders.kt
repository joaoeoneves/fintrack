package com.joaoeoneves.fintrack.testutil

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException

/**
 * A registerable [ContentProvider] (via `Robolectric.setupContentProvider`) whose `openFile` always
 * throws [CancellationException], standing in for "the coroutine was cancelled mid-read/write"
 * without needing a mocking library (this project has none -- see `FirebaseAuthRepositoryTest`).
 *
 * Directly subclassing/mocking `ContentResolver` itself doesn't work here: its `openInputStream`/
 * `openOutputStream` are `final` in the public SDK stub used at compile time (Robolectric only
 * un-finals them at bytecode-instrumentation time for its own shadowing, which doesn't help a test
 * that needs to *compile* an override). Registering a real [ContentProvider] under a test authority
 * and pointing a `content://` [Uri] at it is the supported extension point instead.
 */
class CancellingContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = throw CancellationException("cancelled mid-read/write")

    companion object {
        const val AUTHORITY = "com.joaoeoneves.fintrack.test.cancelling"
    }
}

/** Same shape as [CancellingContentProvider], but throws a plain [IllegalStateException] instead. */
class GenericFailureContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = throw IllegalStateException("boom")

    companion object {
        const val AUTHORITY = "com.joaoeoneves.fintrack.test.genericfailure"
    }
}

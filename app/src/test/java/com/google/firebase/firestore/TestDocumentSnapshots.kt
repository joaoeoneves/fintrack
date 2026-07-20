package com.google.firebase.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.ObjectValue
import com.google.firebase.firestore.model.SnapshotVersion
import com.google.firestore.v1.Value

/**
 * Test-only factory for real [DocumentSnapshot] instances, usable from JVM unit tests without a
 * mocking library (this project has none -- see `FirebaseAuthRepositoryTest`'s doc comment for the
 * same constraint) and without a live Firestore backend/emulator.
 *
 * [DocumentSnapshot] has no public constructor -- only the package-private static factory
 * [DocumentSnapshot.fromDocument]. This file lives in the exact same `com.google.firebase.firestore`
 * package (on the test classpath only, never shipped in the app) purely so ordinary JVM
 * package-private access rules let it call that factory directly, with no reflection involved.
 *
 * The `firestore` reference must be non-null (the real constructor `checkNotNull`s it), but our
 * repositories' `toXOrNull()` conversions only ever call `getString`/`getLong`/`getTimestamp`, whose
 * internal `UserDataWriter` conversion path for STRING/INTEGER/TIMESTAMP value kinds never actually
 * calls back into it (it's only dereferenced for reference-type fields, e.g. `getDocumentReference`,
 * which none of our repositories read) -- so callers can safely pass whatever already-constructed
 * `FirebaseFirestore` instance they used to build the repository under test.
 */
object TestDocumentSnapshots {
    /**
     * Builds a "found" (exists() == true) [DocumentSnapshot] with the given [id] and [fields],
     * nested (arbitrarily -- the collection name doesn't affect [DocumentSnapshot.getId] or field
     * reads) under `users/test-uid/<collection>/<id>`.
     * Use [stringValue], [longValue], and [timestampValue] to build the [fields] map.
     */
    fun found(
        firestore: FirebaseFirestore,
        id: String,
        fields: Map<String, Value>,
        collection: String = "expenses",
    ): DocumentSnapshot {
        val key = DocumentKey.fromPathString("users/test-uid/$collection/$id")
        val objectValue = ObjectValue.fromMap(fields)
        val document = MutableDocument.newFoundDocument(key, SnapshotVersion.NONE, objectValue)
        return DocumentSnapshot.fromDocument(
            firestore,
            document,
            // isFromCache =
            false,
            // hasCommittedMutations =
            false,
        )
    }

    fun stringValue(value: String): Value = Value.newBuilder().setStringValue(value).build()

    fun longValue(value: Long): Value = Value.newBuilder().setIntegerValue(value).build()

    fun timestampValue(timestamp: Timestamp): Value =
        Value
            .newBuilder()
            .setTimestampValue(
                com.google.protobuf.Timestamp
                    .newBuilder()
                    .setSeconds(timestamp.seconds)
                    .setNanos(timestamp.nanoseconds)
                    .build(),
            ).build()
}

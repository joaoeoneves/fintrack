package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.data.backup.AccountBackupRestorer

/**
 * An [AccountBackupRestorer] whose constructor never ran (its Firebase fields are null), for tests that only restore
 * as a guest and so never reach the account side. The class is final and the project has no mocking library; using
 * it for a signed-in restore throws a NullPointerException.
 */
fun inertAccountRestorer(): AccountBackupRestorer {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
    return allocate.invoke(unsafe, AccountBackupRestorer::class.java) as AccountBackupRestorer
}

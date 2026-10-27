package com.joaoeoneves.fintrack.di

import javax.inject.Qualifier

/** The [java.io.File] holding the guest-mode (device-only) data document. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GuestDataFile

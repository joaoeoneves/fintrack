package com.joaoeoneves.fintrack.di

import javax.inject.Qualifier

/** The process-lifetime [kotlinx.coroutines.CoroutineScope] for app-wide background work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

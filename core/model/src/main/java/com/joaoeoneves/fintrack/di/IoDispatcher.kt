package com.joaoeoneves.fintrack.di

import javax.inject.Qualifier

/** The [kotlinx.coroutines.CoroutineDispatcher] for blocking disk I/O (injectable so tests can swap it). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

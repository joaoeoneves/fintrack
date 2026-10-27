package com.joaoeoneves.fintrack.di

import javax.inject.Qualifier

/**
 * The raw (session-switched Firestore or local) expense repository, whose expenses carry only
 * built-in (or unknown placeholder) categories; everything else injects the category-resolving
 * decorator over it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RawExpenses

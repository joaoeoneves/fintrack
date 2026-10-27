package com.joaoeoneves.fintrack.di

import javax.inject.Qualifier

/**
 * The raw Firestore-backed expense repository, whose expenses carry only built-in (or unknown
 * placeholder) categories; everything else injects the category-resolving decorator over it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirestoreExpenses

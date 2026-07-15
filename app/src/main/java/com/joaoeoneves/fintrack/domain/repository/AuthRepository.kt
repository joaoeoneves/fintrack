package com.joaoeoneves.fintrack.domain.repository

import android.content.Context
import com.joaoeoneves.fintrack.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeCurrentUser(): Flow<AuthUser?>

    suspend fun signIn(context: Context): Result<AuthUser>

    suspend fun signOut(): Result<Unit>
}

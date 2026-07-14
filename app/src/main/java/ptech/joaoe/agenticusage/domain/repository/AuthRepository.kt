package ptech.joaoe.agenticusage.domain.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import ptech.joaoe.agenticusage.domain.model.AuthUser

interface AuthRepository {
    fun observeCurrentUser(): Flow<AuthUser?>

    suspend fun signIn(context: Context): Result<AuthUser>

    suspend fun signOut(): Result<Unit>
}

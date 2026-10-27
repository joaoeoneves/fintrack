package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreCategoryGroupRepository
import com.joaoeoneves.fintrack.data.local.LocalCategoryGroupRepository
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [CategoryGroupRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionCategoryGroupRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreCategoryGroupRepository>,
        local: LocalCategoryGroupRepository,
    ) : CategoryGroupRepository {
        private val switch = SessionSwitch<CategoryGroupRepository>(sessionRepository, remote, local)

        override fun observeGroups(): Flow<List<CategoryGroup>> = switch.flow(emptyList()) { it.observeGroups() }

        override suspend fun addGroup(name: String): Result<String> = switch.call { it.addGroup(name) }

        override suspend fun renameGroup(
            id: String,
            name: String,
        ): Result<Unit> = switch.call { it.renameGroup(id, name) }

        override suspend fun reorderGroups(orderedIds: List<String>) = switch.call { it.reorderGroups(orderedIds) }

        override suspend fun deleteGroup(id: String): Result<Unit> = switch.call { it.deleteGroup(id) }
    }

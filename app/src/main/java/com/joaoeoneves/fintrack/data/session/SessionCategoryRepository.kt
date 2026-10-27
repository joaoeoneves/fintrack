package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreCategoryRepository
import com.joaoeoneves.fintrack.data.local.LocalCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [CategoryRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionCategoryRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreCategoryRepository>,
        local: LocalCategoryRepository,
    ) : CategoryRepository {
        private val switch = SessionSwitch<CategoryRepository>(sessionRepository, remote, local)

        override fun observeCategories(): Flow<List<Category>> =
            switch.flow(BuiltInCategories.all) {
                it.observeCategories()
            }

        override suspend fun addCategory(
            name: String,
            iconKey: String,
            colorKey: String,
            kind: CategoryKind,
            groupId: String?,
        ): Result<CategoryId> = switch.call { it.addCategory(name, iconKey, colorKey, kind, groupId) }

        override suspend fun updateCategory(category: Category): Result<Unit> =
            switch.call {
                it.updateCategory(category)
            }

        override suspend fun setArchived(
            id: CategoryId,
            archived: Boolean,
        ): Result<Unit> = switch.call { it.setArchived(id, archived) }

        override suspend fun setGroup(
            id: CategoryId,
            groupId: String?,
        ): Result<Unit> = switch.call { it.setGroup(id, groupId) }

        override suspend fun reorder(orderedIds: List<CategoryId>): Result<Unit> =
            switch.call {
                it.reorder(orderedIds)
            }

        override suspend fun deleteCategory(id: CategoryId): Result<Unit> = switch.call { it.deleteCategory(id) }
    }

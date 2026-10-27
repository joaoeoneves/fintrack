package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [CategoryGroupRepository] for tests. When [categories] is given, deleting a group also ungroups
 * that fake's categories, like the real repositories. Not wired via Hilt; construct and pass it directly.
 */
class FakeCategoryGroupRepository(
    initialGroups: List<CategoryGroup> = emptyList(),
    private val categories: FakeCategoryRepository? = null,
    var nextObserveGroupsError: Throwable? = null,
    var nextWriteError: Throwable? = null,
) : CategoryGroupRepository {
    private val groupsFlow = MutableStateFlow(initialGroups)
    private var nextGeneratedId = 1

    /** The stored groups in insertion order. */
    val groups: List<CategoryGroup> get() = groupsFlow.value

    override fun observeGroups(): Flow<List<CategoryGroup>> {
        nextObserveGroupsError?.let { error -> return flow { throw error } }
        return groupsFlow.map { list -> list.sortedWith(compareBy({ it.order }, { it.id })) }
    }

    override suspend fun addGroup(name: String): Result<String> =
        write {
            validateName(name)
            val id = "group-${nextGeneratedId++}"
            val order = (groupsFlow.value.maxOfOrNull { it.order } ?: -1) + 1
            groupsFlow.value = groupsFlow.value + CategoryGroup(id, name, order)
            id
        }

    override suspend fun renameGroup(
        id: String,
        name: String,
    ): Result<Unit> =
        write {
            validateName(name)
            require(groupsFlow.value.any { it.id == id }) { "Unknown group $id" }
            groupsFlow.value = groupsFlow.value.map { if (it.id == id) it.copy(name = name) else it }
        }

    override suspend fun reorderGroups(orderedIds: List<String>): Result<Unit> =
        write {
            val byId = groupsFlow.value.associateBy { it.id }
            val reordered =
                orderedIds.mapIndexed { index, id ->
                    requireNotNull(byId[id]) { "Unknown group $id" }.copy(order = index)
                }
            groupsFlow.value = reordered + groupsFlow.value.filter { it.id !in orderedIds }
        }

    override suspend fun deleteGroup(id: String): Result<Unit> =
        write {
            groupsFlow.value = groupsFlow.value.filterNot { it.id == id }
            categories?.ungroup(id)
        }

    private fun validateName(name: String) {
        require(name.isNotEmpty() && name.codePointLength() <= MAX_GROUP_NAME_LENGTH) { "Invalid group name" }
    }

    private inline fun <T> write(block: () -> T): Result<T> {
        nextWriteError?.let { error ->
            nextWriteError = null
            return Result.failure(error)
        }
        return runCatching(block)
    }
}

package io.github.sh4.zabuton.workspace

import java.util.*


data class WorkspaceName(val name: String) {
    override fun toString() = name
}
data class WorkspaceId(val id: UUID) {
    override fun toString() = id.toString()
}
data class Workspace(val id: WorkspaceId,
                     val name: WorkspaceName,
                     val comment: String = "",
                     val deleted: Boolean = false)

data class WorkspaceFindRequest(val id: WorkspaceId? = null,
                                val name: WorkspaceName? = null,
                                val deleted: Boolean? = null,
                                val comment: String? = null)

interface WorkspaceRepository {
    fun find(id: WorkspaceId): Workspace?
    fun find(request: WorkspaceFindRequest): Collection<Workspace>
    fun save(workspace: Workspace)
    fun delete(id: WorkspaceId)
}

class WorkspaceService(private val repository: WorkspaceRepository) {
    fun save(workspace: Workspace) = repository.save(workspace)
    fun find(id: WorkspaceId): Workspace? = repository.find(id)
    fun find(request: WorkspaceFindRequest): Collection<Workspace> = repository.find(request)
    fun delete(id: WorkspaceId) {
        val workspace = repository.find(id)?.copy(deleted = true)
        if (workspace != null) {
            repository.save(workspace)
        }
    }
    fun restore(id: WorkspaceId) {
        val workspace = repository.find(id)?.copy(deleted = false)
        if (workspace != null) {
            repository.save(workspace)
        }
    }
    fun deletePermanently(id: WorkspaceId) {
        val workspace = repository.find(id)
        if (workspace != null && workspace.deleted) {
            repository.delete(workspace.id)
        }
    }
}

class OnMemoryWorkspaceRepository : WorkspaceRepository {
    private val repository = HashMap<WorkspaceId, Workspace>()

    override fun find(id: WorkspaceId): Workspace? = repository.get(id)

    override fun find(request: WorkspaceFindRequest): Collection<Workspace> {
        return repository.filter {
            if (request.id != null && request.id != it.key) {
                return@filter false
            }
            if (request.name != null && request.name != it.value.name) {
                return@filter false
            }
            if (request.deleted != null && request.deleted != it.value.deleted) {
                return@filter false
            }
            if (request.comment != null && !it.value.comment.contains(request.comment)) {
                return@filter false
            }
            return@filter true
        }.map { x -> x.value }
    }

    override fun save(workspace: Workspace) {
        repository.put(workspace.id, workspace)
    }

    override fun delete(id: WorkspaceId) {
        repository.remove(id)
    }
}
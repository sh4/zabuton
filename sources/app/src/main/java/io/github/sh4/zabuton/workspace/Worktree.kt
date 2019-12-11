package io.github.sh4.zabuton.workspace

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.File
import kotlin.collections.HashMap

interface Worktree {
    val workspace: Workspace
    val root: File

    fun deletePermanently()
}

interface WorktreeRepository {
    fun find(id: WorkspaceId): Worktree?
    fun save(worktree: Worktree)
    fun deletePermanently(id: WorkspaceId)
}

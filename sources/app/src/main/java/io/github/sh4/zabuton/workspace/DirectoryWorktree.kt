package io.github.sh4.zabuton.workspace

import java.io.File

class DirectoryWorktree(override val workspace: Workspace,
                        override val root: File) : Worktree {
    override fun deletePermanently() {
        root.deleteRecursively()
    }
}
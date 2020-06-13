package io.github.sh4.zabuton.workspace

import android.content.Context
import io.github.sh4.zabuton.git.*
import io.github.sh4.zabuton.util.Progress
import io.github.sh4.zabuton.util.ProgressContext
import io.github.sh4.zabuton.util.ProgressType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

const val GIT_PROGRESS_RATIO = 10000L
const val LIBGIT2_ROOT_CERTIFICATE_IN_ASSETS = "build/cacert.pem"
const val LIBGIT2_ROOT_CERTIFICATE = "root-cacert.pem"

private val initializedLibGit2 = AtomicBoolean()

fun initializeLibGit2(context: Context) {
    if (!initializedLibGit2.compareAndSet(false, true)) {
        return
    }
    val sslCertificatesFile = File(context.filesDir, LIBGIT2_ROOT_CERTIFICATE)
    sslCertificatesFile.outputStream().use { context.assets.open(LIBGIT2_ROOT_CERTIFICATE_IN_ASSETS).copyTo(it) }
    LibGit2.init(sslCertificatesFile.absolutePath)
}

suspend fun createGitRepositoryWorktree(
        workspace: Workspace,
        root: File,
        url: URL,
        block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<String>>) -> Unit
): GitRepositoryWorktree = coroutineScope {
    val progressContext = ProgressContext(this, block)
    launch(Dispatchers.IO) {
        val progress = progressContext.next(ProgressType.CloneGitRepository, GIT_PROGRESS_RATIO)
        var sidebandMessage = ""
        Repository.clone(url.toString(), root.absolutePath) { p ->
            val network = if (p.totalObjects > 0L) (GIT_PROGRESS_RATIO * p.receivedObjects) / p.totalObjects else 0L
            val index = if (p.totalObjects > 0L) (GIT_PROGRESS_RATIO * p.indexedObjects) / p.totalObjects else 0L
            val checkout = if (p.totalSteps > 0L) (GIT_PROGRESS_RATIO * p.completedSteps) / p.totalSteps else 0L
            val resolvingDelta = if (p.totalObjects > 0L && p.receivedObjects == p.totalObjects) {
                if (p.totalDeltas > 0L) (GIT_PROGRESS_RATIO * p.indexedDeltas ) / p.totalDeltas else GIT_PROGRESS_RATIO
            } else 0L
            val message = p.sidebandMessage ?: ""
            if (sidebandMessage != message) {
                sidebandMessage = message
                progress.additionalData = sidebandMessage
            }
            progress.report((network + index + checkout + resolvingDelta) / 4L)
        }
        progress.finish()
    }.join()
    progressContext.finish()
    return@coroutineScope GitRepositoryWorktree(workspace, root)
}

class GitRepositoryWorktree(override val workspace: Workspace,
                            override val root: File) : Worktree {
    private val repository = Repository.open(root.canonicalPath)

    val headName: String
        get() = repository.headName
    val tagNames: Array<String>
        get() = repository.tagNames
    val localBranchNames: Array<String>
        get() = repository.localBranchNames
    val remoteBranchNames: Array<String>
        get() = repository.remoteBranchNames
    val remotes: Array<Remote>
        get() = repository.remotes

    suspend fun checkout(
            refspec: String,
            block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<Unit>>) -> Unit
    ) = coroutineScope {
        val progressContext = ProgressContext(this, block)
        launch(Dispatchers.IO) {
            val progress = progressContext.next(ProgressType.CheckoutGitRepository, GIT_PROGRESS_RATIO)
            repository.checkout(refspec) { p ->
                val checkout = if (p.totalSteps > 0) (GIT_PROGRESS_RATIO * p.completedSteps) / p.totalSteps else 0L
                progress.report(checkout)
            }
            progress.finish()
        }.join()
        progressContext.finish()
    }

    suspend fun fetch(
            remote: String,
            block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<String>>) -> Unit
    ) = coroutineScope {
        val progressContext = ProgressContext(this, block)
        launch(Dispatchers.IO) {
            val progress = progressContext.next(ProgressType.FetchGitRepository, GIT_PROGRESS_RATIO)
            var sidebandMessage = ""
            repository.fetch(remote) { p ->
                val network = if (p.totalObjects > 0L) (GIT_PROGRESS_RATIO * p.receivedObjects) / p.totalObjects else 0L
                val index = if (p.totalObjects > 0L) (GIT_PROGRESS_RATIO * p.indexedObjects) / p.totalObjects else 0L
                val resolvingDelta = if (p.totalObjects > 0L && p.receivedObjects == p.totalObjects) {
                    if (p.totalDeltas > 0L) (GIT_PROGRESS_RATIO * p.indexedDeltas ) / p.totalDeltas else GIT_PROGRESS_RATIO
                } else 0L
                val message = p.sidebandMessage ?: ""
                if (sidebandMessage != message) {
                    sidebandMessage = message
                    progress.additionalData = sidebandMessage
                }
                progress.report((network + index + resolvingDelta) / 3L)
            }
            progress.finish()
        }.join()
        progressContext.finish()
    }

    suspend fun reset(
            kind: ResetKind,
            block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<Unit>>) -> Unit
    ) = coroutineScope {
        val progressContext = ProgressContext(this, block)
        launch(Dispatchers.IO) {
            val progress = progressContext.next(ProgressType.ResetGitRepository, GIT_PROGRESS_RATIO)
            repository.reset(kind) { p ->
                val checkout = if (p.totalSteps > 0) (GIT_PROGRESS_RATIO * p.completedSteps) / p.totalSteps else 0L
                progress.report(checkout)
            }
            progress.finish()
        }.join()
        progressContext.finish()
    }

    suspend fun log(
            block: suspend CoroutineScope.(commit: ReceiveChannel<ICommitObject>) -> Unit
    ) = coroutineScope {
        val channel = Channel<ICommitObject>()
        val receiver = launch { block(this, channel) }
        repository.log(fun (commit:ICommitObject):Boolean {
            try {
                launch(Dispatchers.Default) { channel.send(commit) }
            } catch (_: ClosedReceiveChannelException) {
                return false
            }
            return true
        })
        channel.close()
        receiver.join()
    }

    override fun deletePermanently() {
        root.deleteRecursively()
    }
}
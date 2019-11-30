package io.github.sh4.zabuton

import io.github.sh4.zabuton.workspace.*
import org.junit.Assert
import org.junit.Test
import java.util.*

class WorkspaceUnitTest {
    @Test
    fun simpleTest() {
        val uuid = UUID.randomUUID()
        val w1 = Workspace(WorkspaceId(uuid), WorkspaceName("test"))
        val w2 = Workspace(WorkspaceId(uuid), WorkspaceName("test"))
        Assert.assertEquals("test", w1.name.toString())
        Assert.assertNotEquals("", w1.id.toString())
        Assert.assertEquals(w1, w2)
    }

    @Test
    fun repositoryTest() {
        val repository = OnMemoryWorkspaceRepository()
        val id = WorkspaceId(UUID.randomUUID())
        val findAll = { repository.find(WorkspaceFindRequest()) }

        Assert.assertNull(repository.find(id))
        repository.save(Workspace(id, WorkspaceName("test")))

        Assert.assertNotNull(repository.find(id))

        findAll().let {
            Assert.assertEquals(1, it.size)
            Assert.assertEquals(id, it.toTypedArray()[0].id)
        }
        repository.find(WorkspaceFindRequest(id = WorkspaceId(UUID.randomUUID()))).let {
            Assert.assertEquals(0, it.size)
        }

        Workspace(WorkspaceId(UUID.randomUUID()), WorkspaceName("test")).let {
            repository.save(it)
            Assert.assertEquals(2, findAll().size)
            repository.delete(it.id)
            Assert.assertEquals(1, findAll().size)
        }

        repository.find(id)?.copy(deleted = true).let {
            Assert.assertNotNull(it)
            repository.save(it!!)
            Assert.assertEquals(1,
                    repository.find(WorkspaceFindRequest(deleted = true)).size)
        }
    }

    @Test
    fun serviceTest() {
        val service = WorkspaceService(OnMemoryWorkspaceRepository())
        val workspace = Workspace(WorkspaceId(UUID.randomUUID()), WorkspaceName("test"))
        val workspaceCount = { service.find(WorkspaceFindRequest()).size }

        service.save(workspace)
        Assert.assertNotNull(service.find(workspace.id))

        service.delete(workspace.id)
        service.find(workspace.id).let {
            Assert.assertNotNull(it!!)
            Assert.assertTrue(it.deleted)
        }
        Assert.assertEquals(1, workspaceCount())

        service.restore(workspace.id)

        service.deletePermanently(workspace.id)
        Assert.assertEquals(1, workspaceCount())

        service.delete(workspace.id)
        service.deletePermanently(workspace.id)
        Assert.assertEquals(0, workspaceCount())
    }
}
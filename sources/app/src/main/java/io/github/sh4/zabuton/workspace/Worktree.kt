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

class OnMemoryWorktreeRepository : WorktreeRepository {
    private val repository = HashMap<WorkspaceId, Worktree>()

    override fun find(id: WorkspaceId): Worktree? {
        return repository.get(id)
    }

    override fun save(worktree: Worktree) {
        // [x] ワークツリー実装クラス特有のフィールドをどうやって serialize/deserialize する？
        // -> PolymorphicJsonAdapterFactory を使って共通のインターフェイスを実装するクラス識別用のラベルを付ける
        repository.put(worktree.workspace.id, worktree)
    }

    override fun deletePermanently(id: WorkspaceId) {
        repository.remove(id)?.deletePermanently()
    }
}


// ビルド
// * QMK ファームウェアのビルド
//   * ビルド対象の選択
// クリーン
// * QMK ファームウェアのワークツリーをクリーン (クリーンターゲットの実行)
// ファームウェア書き込み
// * QMK ファームウェアの書き込み

enum class HogeType {
    HogeImpl,
    FugaImpl
}

//@JsonClass(generateAdapter = true)
interface Hoge {
    @Json(name="hoge")
    val type: HogeType
    fun hello()
}

@JsonClass(generateAdapter = true)
class HogeImpl(val piyo: String = "piyo") : Hoge {
    override val type = HogeType.HogeImpl

    override fun hello() {
        println("Hello, World!")
    }
}

@JsonClass(generateAdapter = true)
class FugaImpl(val foobar:Int = 2000) : Hoge {
    override val type = HogeType.FugaImpl

    override fun hello() {
        println("Hello, Fuga World!")
    }
}


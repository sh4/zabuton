package io.github.sh4.zabuton

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import io.github.sh4.zabuton.app.SymlinkMaps
import io.github.sh4.zabuton.workspace.FugaImpl
import io.github.sh4.zabuton.workspace.Hoge
import io.github.sh4.zabuton.workspace.HogeImpl
import io.github.sh4.zabuton.workspace.HogeType
import org.junit.Assert
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        Assert.assertEquals(4, 2 + 2.toLong())
    }

    @Test
    fun moshiTest() {
        val adapter = Moshi.Builder().add(
                PolymorphicJsonAdapterFactory.of(Hoge::class.java, "hoge")
                        .withSubtype(HogeImpl::class.java, HogeType.HogeImpl.name)
                        .withSubtype(FugaImpl::class.java, HogeType.FugaImpl.name)
        ).build().adapter(Hoge::class.java)

        //val adapter = Moshi.Builder().build().adapter(HogeImpl::class.java)
        val hogeImplJson = adapter.toJson(HogeImpl())
        val hogeImpl = adapter.fromJson(hogeImplJson)
        Assert.assertNotNull(hogeImplJson)
        Assert.assertNotNull(hogeImpl)

        val fugaImplJson = adapter.toJson(FugaImpl())
        val fugaImpl = adapter.fromJson(fugaImplJson)
        Assert.assertNotNull(fugaImplJson)
        Assert.assertNotNull(fugaImpl)

        /*
        val symlinkMaps = moshi.adapter(SymlinkMaps::class.java).fromJson("""
           { "files": [ {"target": "target from", "src": "source from"} ] }
        """.trimIndent())
        Assert.assertNotNull(symlinkMaps)
         */
        //val hoge:Hoge = HogeImpl()
        //moshi.adapter(HogeImpl::class.java).toJson(hoge)
    }
}
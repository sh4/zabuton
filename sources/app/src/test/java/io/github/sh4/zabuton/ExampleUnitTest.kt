package io.github.sh4.zabuton

import com.squareup.moshi.Moshi
import io.github.sh4.zabuton.app.SymlinkMaps
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
        val moshi = Moshi.Builder().build()
        val symlinkMaps = moshi.adapter(SymlinkMaps::class.java).fromJson("""
           { "files": [ {"target": "target from", "src": "source from"} ] }
        """.trimIndent())
        Assert.assertNotNull(symlinkMaps)
    }
}
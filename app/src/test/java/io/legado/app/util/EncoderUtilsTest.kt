package io.legado.app.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.GSON
import io.legado.app.utils.deHex
import io.legado.app.utils.enHex
import io.legado.app.utils.fromJsonArray
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.decodingWith

class EncoderUtilsTest {
//    @Test  //大文件有46.6MB
//    fun testGsonBig(){
//        val filepath = "D:/code/backend/kotlin/demo/demo-utils/src/test/resources/io/big.json"
//        val stream = FileInputStream(filepath)
//        GSON.fromJsonArray<BookSource>(stream).getOrThrow().let {
//            val source = it.firstOrNull() ?: return@let
//            if (source.bookSourceUrl.isEmpty()) {
//                throw NoStackTraceException("不是书源")
//            }
//            println("共加载 ${it.size}个书源")
//        }
//    }
//    会报错：java.io.EOFException: End of input at line 422220 column 4 path $[10123]
//    尽管在看起来读取了，但是不知道为什么错了
//    @OptIn(ExperimentalEncodingApi::class)
//    @Test
//    fun testGsonBase64Big(){
//        val filepath = "D:/code/backend/kotlin/demo/demo-utils/src/test/resources/io/big.base64.json"
//        val stream = FileInputStream(filepath).decodingWith(Base64.Mime)
//        val type = TypeToken.getParameterized(List::class.java, BookSource::class.java).type
//        val list = GSON.fromJson(InputStreamReader(stream, Charset.forName("UTF-8")), type) as List<BookSource?>
////        Gson().fromJsonArray<>()
////        GSON.fromJsonArray<BookSource>(stream).getOrThrow().let {
////            val source = it.firstOrNull() ?: return@let
////            if (source.bookSourceUrl.isEmpty()) {
////                throw NoStackTraceException("不是书源")
////            }
////            println("共加载 ${it.size}个书源")
////        }
//    }
//    @OptIn(ExperimentalEncodingApi::class)
//    @Test
//    fun testGsonBase64Smail(){
//        val filepath = "D:/code/backend/kotlin/demo/demo-utils/src/test/resources/io/demo.base64.json"
//        val stream = FileInputStream(filepath)
//        GSON.fromJsonArray<BookSource>(stream.decodingWith(Base64.Mime)).getOrThrow().let {
//            val source = it.firstOrNull() ?: return@let
//            if (source.bookSourceUrl.isEmpty()) {
//                throw NoStackTraceException("不是书源")
//            }
//            println("共加载 ${it.size}个书源")
//        }
//    }
//    @Test
//    fun testGsonBase16Big(){
//        val filepath = "D:/code/backend/kotlin/demo/demo-utils/src/test/resources/io/big.base16.json"
//        val stream = FileInputStream(filepath)
//        GSON.fromJsonArray<BookSource>(stream.deHex()).getOrThrow().let {
//            val source = it.firstOrNull() ?: return@let
//            if (source.bookSourceUrl.isEmpty()) {
//                throw NoStackTraceException("不是书源")
//            }
//            println("共加载 ${it.size}个书源")
//        }
//    }

    @Test
    @OptIn(ExperimentalStdlibApi::class)
    fun testHexEncodeOutputStream() {
        //覆盖一个字节的所有可能
        val plainText =  (0..0xff).map { it.toChar() }.joinToString("")
        var hexString1 = plainText.toByteArray().toHexString()
        var hexString2 = ByteArrayOutputStream().run {
            enHex().run {
                write(plainText.toByteArray())
            }
            toByteArray().decodeToString()
        }
        Assert.assertEquals(
            hexString1,
            hexString2
        )
        hexString1 = plainText.toByteArray().toHexString(HexFormat { upperCase = true })
        hexString2 = ByteArrayOutputStream().run {
            enHex(true).run {
                write(plainText.toByteArray())
            }
            toByteArray().decodeToString()
        }
        Assert.assertEquals(
            hexString1,
            hexString2
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testHexDecodeInputStream() {
        //覆盖一个字节的所有可能
        val plainText = (0..0xff).map { it.toChar() }
            .joinToString("")
            .toByteArray()
            .toHexString()

        run {
            ByteArrayInputStream(plainText.toByteArray().toHexString().toByteArray()).deHex().readBytes()
        }.apply {
            Assert.assertTrue(plainText.toByteArray().contentEquals(this))
        }
    }
}
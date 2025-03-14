package io.legado.app.utils

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream

/**
 * 编码工具 escape base64
 */
@Suppress("unused")
object EncoderUtils {

    fun escape(src: String): String {
        val tmp = StringBuilder()
        for (char in src) {
            val charCode = char.code
            if (charCode in 48..57 || charCode in 65..90 || charCode in 97..122) {
                tmp.append(char)
                continue
            }

            val prefix = when {
                charCode < 16 -> "%0"
                charCode < 256 -> "%"
                else -> "%u"
            }
            tmp.append(prefix).append(charCode.toString(16))
        }
        return tmp.toString()
    }

    @JvmOverloads
    fun base64Decode(str: String, flags: Int = Base64.DEFAULT): String {
        val bytes = Base64.decode(str, flags)
        return String(bytes)
    }

    @JvmOverloads
    fun base64Encode(str: String, flags: Int = Base64.NO_WRAP): String? {
        return Base64.encodeToString(str.toByteArray(), flags)
    }

    @JvmOverloads
    fun base64Encode(bytes: ByteArray, flags: Int = Base64.NO_WRAP): String {
        return Base64.encodeToString(bytes, flags)
    }
    
    @JvmOverloads
    fun base64DecodeToByteArray(str: String, flags: Int = Base64.DEFAULT): ByteArray {
        return Base64.decode(str, flags)
    }

}
fun OutputStream.enHex(upperCase: Boolean = false): OutputStream {
    return HexEncodeOutputStream(this, upperCase)
}
fun InputStream.deHex(): InputStream {
    return HexDecodeInputStream(this)
}
class HexEncodeOutputStream(
    private val stream: OutputStream,
    private val upperCase: Boolean = false
) : OutputStream() {
    override fun write(b: Int) {
        val v = (b and 0xFF)
        stream.write(hexChar(v shr 4))
        stream.write(hexChar(v and 0x0F))
    }

    private fun hexChar(v: Int): Int {
        if (v > 9) {
            return if (upperCase) {
                'A'.code + (v - 10)
            } else {
                'a'.code + (v - 10)
            }
        }
        return '0'.code + v
    }

    override fun close() {
        stream.close()
    }
}

private val hexLookup = IntArray(128).apply {
    // 预先生成 Hex 字符映射表 (0-9, a-f, A-F)
    for (i in '0'.code..'9'.code) this[i] = i - '0'.code
    for (i in 'a'.code..'f'.code) this[i] = i - 'a'.code + 10
    for (i in 'A'.code..'F'.code) this[i] = i - 'A'.code + 10
}
class HexDecodeInputStream(
    private val stream: InputStream
) : InputStream() {
    override fun read(): Int {
        // 批量读取两个字节，减少 IO 调用
        val b1 = stream.read()
        if (b1 == -1) return -1
        val b2 = stream.read().takeIf { it != -1 }
            ?: throw IllegalStateException("Odd number of hex characters")

        // 直接查表避免字符串操作
        return decodeHexPair(b1.toChar(), b2.toChar())
    }

    private fun decodeHexPair(c1: Char, c2: Char): Int {
        // 检查字符合法性
        if (c1.code >= hexLookup.size || hexLookup[c1.code] == -1 ||
            c2.code >= hexLookup.size || hexLookup[c2.code] == -1
        ) {
            throw NumberFormatException("Invalid hex characters: $c1$c2")
        }
        return (hexLookup[c1.code] shl 4) or hexLookup[c2.code]
    }

    override fun close() {
        stream.close()
    }

    override fun available(): Int {
        return stream.available()
    }
}
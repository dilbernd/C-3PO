package org.c_3po.util

import java.io.BufferedInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

@ExperimentalStdlibApi
@ExperimentalUnsignedTypes
object ChecksumCalculatorKt {
    @Throws(NoSuchAlgorithmException::class, IOException::class)
    fun computeSha1Hash(file: Path): UByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        BufferedInputStream(Files.newInputStream(file)).use { bis ->
            val buffer = ByteArray(1024)
            var numBytesRead = 0
            while (numBytesRead != -1) {
                numBytesRead = bis.read(buffer)
                if (numBytesRead > 0) {

                    // It's crucial to not just pass `buffer` because then `.update`
                    // would add all of `buffer` even though `.read` might has read less bytes
                    // than buffer's length.
                    md.update(buffer, 0, numBytesRead)
                }
            }
            return md.digest().toUByteArray()
        }
    }

    /**
     * Source: https://www.baeldung.com/java-byte-arrays-hex-strings
     */
    fun encodeHexString(byteArray: UByteArray): String {
        val hexStringBuilder = StringBuilder(40)
        byteArray.forEach { b -> appendHexByte(b, hexStringBuilder) }
        return hexStringBuilder.toString()
    }


    @JvmStatic
    fun sha1String(file: Path) = encodeHexString(computeSha1Hash(file))


    /**
     * Source: https://www.baeldung.com/java-byte-arrays-hex-strings
     */
    private fun appendHexByte(num: UByte, sb: java.lang.StringBuilder) {
        sb.append((num.rotateRight(4) and (0xF.toUByte())).toString(16))
        sb.append((num and 0xF.toUByte()).toString(16))
    }
}

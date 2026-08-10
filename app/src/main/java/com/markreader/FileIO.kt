package com.markreader

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Refuse to load files larger than this; readText() into a single String would OOM. */
const val MAX_FILE_BYTES = 10 * 1024 * 1024

class FileTooLargeException :
    Exception("File exceeds the ${MAX_FILE_BYTES / (1024 * 1024)} MB limit")

/**
 * Reads [stream] fully as text, throwing [FileTooLargeException] once more than
 * [MAX_FILE_BYTES] have been read. Honours UTF-8/UTF-16 byte-order marks (the BOM
 * is stripped rather than leaking into the text, where it would break first-line
 * heading detection).
 */
fun readBoundedText(stream: InputStream): String {
    val buffer = ByteArray(64 * 1024)
    val out = ByteArrayOutputStream()
    while (true) {
        val read = stream.read(buffer)
        if (read == -1) break
        out.write(buffer, 0, read)
        if (out.size() > MAX_FILE_BYTES) throw FileTooLargeException()
    }
    return decodeText(out.toByteArray())
}

private fun decodeText(bytes: ByteArray): String = when {
    bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
        String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    else -> String(bytes, Charsets.UTF_8)
}

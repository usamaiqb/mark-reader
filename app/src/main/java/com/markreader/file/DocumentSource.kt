package com.markreader.file

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

private const val BYTES_PER_MIB = 1024 * 1024
private const val READ_BUFFER_BYTES = 64 * 1024

/** Refuse to load files larger than this; decoding one into a single String would OOM. */
const val MAX_FILE_BYTES = 10 * BYTES_PER_MIB

/** The same limit in MB, for the two places that have to say it out loud. */
const val MAX_FILE_MIB = MAX_FILE_BYTES / BYTES_PER_MIB

class FileTooLargeException :
    Exception("File exceeds the $MAX_FILE_MIB MB limit")

/**
 * Getting document bytes in and out of a URI: the provider dance, the size bound, and the
 * hand-off from the Activity's eager read.
 *
 * Bytes, not text — decoding belongs to [TextCodec], and keeping the two apart is what lets a
 * document be written back in the encoding it arrived in.
 */
object DocumentSource {

    /**
     * All the bytes behind [uri], or null if it cannot be opened. Throws
     * [FileTooLargeException] past [MAX_FILE_BYTES].
     *
     * Three sources in order: a pre-read registered by the Activity, then the ordinary input
     * stream, then a file descriptor. The last is not redundant — some providers refuse
     * `openInputStream` and allow `openFileDescriptor`.
     */
    suspend fun read(resolver: ContentResolver, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        // Registered by MainActivity while it held the intent's grant. Restricted providers
        // reject reads from a ViewModel scope but allow them there, so this is the only route
        // that works for some documents. A null result means that read failed; fall through.
        ExternalFileCache.consume(uri.toString())?.await()?.let { return@withContext it }

        resolver.openInputStream(uri)?.use { return@withContext readBounded(it) }
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { return@withContext readBounded(it) }
        }
        null
    }

    /** True if the bytes were written. Every failure is remedied the same way — save a copy. */
    suspend fun write(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            // A revoked grant, a deleted document, a provider that will not take a write —
            // the caller offers Save As for all three, so they need no telling apart here.
            try {
                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
            } catch (_: SecurityException) {
                false
            } catch (_: IOException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
        }

    /** Reads [stream] fully, giving up once more than [MAX_FILE_BYTES] have arrived. */
    fun readBounded(stream: InputStream): ByteArray {
        val buffer = ByteArray(READ_BUFFER_BYTES)
        val out = ByteArrayOutputStream()
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_FILE_BYTES) throw FileTooLargeException()
        }
        return out.toByteArray()
    }
}

/** Best-effort persistable grant; not all providers support them. */
fun ContentResolver.tryTakePersistablePermission(uri: Uri, flags: Int) {
    try {
        takePersistableUriPermission(uri, flags)
    } catch (_: SecurityException) {
        // Provider does not allow persistable grants.
    } catch (_: IllegalArgumentException) {
        // Not a persistable URI.
    }
}

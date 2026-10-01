package com.agoose.neonupdater.adb

import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * The two ADB services this app needs: `sync:` to copy the APK onto the watch and `shell:` to run
 * `pm install` on it. Everything here is blocking and must run off the main thread.
 */
object AdbTransfer {

    /** adb's own sync chunk size. Also keeps every WRTE packet well below the negotiated max. */
    private const val CHUNK = 8 * 1024

    /** 0100644 — a regular file, rw-r--r--, same mode `adb push` uses. */
    private const val FILE_MODE = 33188

    /**
     * Streams [file] to [remotePath] on the connected device using the ADB sync protocol.
     *
     * @param onProgress called with (bytesSent, totalBytes) after every chunk.
     */
    fun push(
        manager: AbsAdbConnectionManager,
        file: File,
        remotePath: String,
        onProgress: (Long, Long) -> Unit,
    ) {
        val total = file.length()
        manager.openStream("sync:").use { stream ->
            val input = stream.openInputStream()

            val pathAndMode = "$remotePath,$FILE_MODE".toByteArray(Charsets.UTF_8)
            stream.sendChunk("SEND", pathAndMode, pathAndMode.size)

            var sent = 0L
            FileInputStream(file).use { source ->
                val buffer = ByteArray(CHUNK)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    stream.sendChunk("DATA", buffer, read)
                    sent += read
                    onProgress(sent, total)
                }
            }

            stream.sendHeader("DONE", (file.lastModified() / 1000L).toInt())

            val response = ByteArray(8)
            input.readFullyOrThrow(response)
            when (val id = String(response, 0, 4, Charsets.US_ASCII)) {
                "OKAY" -> Unit
                "FAIL" -> {
                    val message = ByteArray(readLe32(response, 4).coerceIn(0, 4096))
                    runCatching { input.readFullyOrThrow(message) }
                    throw IOException("Push rejected: ${String(message, Charsets.UTF_8)}")
                }
                else -> throw IOException("Unexpected sync reply '$id'")
            }

            runCatching { stream.sendHeader("QUIT", 0) }
        }
    }

    /**
     * Runs [command] through `shell:` and returns everything it printed.
     *
     * The daemon closes the stream when the command exits, which is what ends the read loop.
     */
    fun shell(manager: AbsAdbConnectionManager, command: String): String {
        manager.openStream("shell:$command").use { stream ->
            val input = stream.openInputStream()
            val output = StringBuilder()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = try {
                    input.read(buffer)
                } catch (e: IOException) {
                    // AdbStream signals "closed by peer" with an IOException rather than -1.
                    break
                }
                if (read < 0) break
                if (read > 0) output.append(String(buffer, 0, read, Charsets.UTF_8))
            }
            return output.toString()
        }
    }

    private fun AdbStream.sendChunk(id: String, payload: ByteArray, length: Int) {
        val packet = ByteArray(8 + length)
        writeHeaderInto(packet, id, length)
        payload.copyInto(packet, 8, 0, length)
        write(packet, 0, packet.size)
    }

    private fun AdbStream.sendHeader(id: String, value: Int) {
        val packet = ByteArray(8)
        writeHeaderInto(packet, id, value)
        write(packet, 0, packet.size)
    }

    private fun writeHeaderInto(target: ByteArray, id: String, value: Int) {
        val idBytes = id.toByteArray(Charsets.US_ASCII)
        idBytes.copyInto(target, 0, 0, 4)
        target[4] = (value and 0xFF).toByte()
        target[5] = ((value shr 8) and 0xFF).toByte()
        target[6] = ((value shr 16) and 0xFF).toByte()
        target[7] = ((value shr 24) and 0xFF).toByte()
    }

    private fun readLe32(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)

    private fun InputStream.readFullyOrThrow(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            if (read < 0) throw IOException("Connection closed before the device replied")
            offset += read
        }
    }
}

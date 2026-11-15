package com.atv.adbcommander.adb

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implements ADB Sync protocol for pushing files, pulling files, and installing APKs to Android TV.
 */
class AdbSync(private val connection: AdbConnection) {

    companion object {
        const val ID_SEND = 0x444e4553 // "SEND"
        const val ID_RECV = 0x56434552 // "RECV"
        const val ID_DATA = 0x41544144 // "DATA"
        const val ID_DONE = 0x454e4f44 // "DONE"
        const val ID_OKAY = 0x59414b4f // "OKAY"
        const val ID_FAIL = 0x4c494146 // "FAIL"
        const val ID_QUIT = 0x54495551 // "QUIT"

        private const val CHUNK_SIZE = 64 * 1024 // 64 KB
    }

    /**
     * Pushes a stream of data to a remote file path on Android TV.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun push(
        inputStream: InputStream,
        remotePath: String,
        totalBytes: Long,
        mode: Int = 420, // 0644 in octal = 420 in decimal
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val stream = connection.openStream("sync:")
        try {
            // 1. Send SEND packet: "remotePath,mode"
            val pathAndMode = "$remotePath,$mode"
            val pathBytes = pathAndMode.toByteArray(Charsets.UTF_8)

            val sendHeader = ByteBuffer.allocate(8 + pathBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            sendHeader.putInt(ID_SEND)
            sendHeader.putInt(pathBytes.size)
            sendHeader.put(pathBytes)
            stream.write(sendHeader.array())

            // 2. Stream DATA chunks
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesRead: Int
            var totalSent = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val dataHeader = ByteBuffer.allocate(8 + bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                dataHeader.putInt(ID_DATA)
                dataHeader.putInt(bytesRead)
                dataHeader.put(buffer, 0, bytesRead)
                stream.write(dataHeader.array())

                totalSent += bytesRead
                onProgress?.invoke(totalSent, totalBytes)
            }

            // 3. Send DONE packet
            val mtime = (System.currentTimeMillis() / 1000).toInt()
            val donePacket = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            donePacket.putInt(ID_DONE)
            donePacket.putInt(mtime)
            stream.write(donePacket.array())

            // 4. Read response
            val responseBytes = readExact(stream, 8)
            val resBb = ByteBuffer.wrap(responseBytes).order(ByteOrder.LITTLE_ENDIAN)
            val responseId = resBb.int
            val responseLength = resBb.int

            if (responseId == ID_FAIL) {
                val msgBytes = readExact(stream, responseLength)
                val errorMsg = String(msgBytes, Charsets.UTF_8)
                throw IOException("Sync push failed: $errorMsg")
            } else if (responseId != ID_OKAY) {
                throw IOException("Sync push unexpected response ID: $responseId")
            }

            // 5. Send QUIT packet
            val quitPacket = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            quitPacket.putInt(ID_QUIT)
            quitPacket.putInt(0)
            try {
                stream.write(quitPacket.array())
            } catch (ignored: Exception) {}

        } finally {
            stream.close()
        }
    }

    /**
     * Pulls a file from Android TV to an OutputStream.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun pull(
        remotePath: String,
        outputStream: OutputStream,
        onProgress: ((bytesReceived: Long) -> Unit)? = null
    ) {
        val stream = connection.openStream("sync:")
        try {
            val pathBytes = remotePath.toByteArray(Charsets.UTF_8)
            val recvHeader = ByteBuffer.allocate(8 + pathBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            recvHeader.putInt(ID_RECV)
            recvHeader.putInt(pathBytes.size)
            recvHeader.put(pathBytes)
            stream.write(recvHeader.array())

            var totalReceived = 0L

            while (true) {
                val headerBytes = readExact(stream, 8)
                val hbb = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val chunkId = hbb.int
                val chunkLength = hbb.int

                when (chunkId) {
                    ID_DATA -> {
                        // Stream chunk bytes without dropping any buffer data
                        var remaining = chunkLength
                        val tempBuf = ByteArray(minOf(remaining, CHUNK_SIZE))
                        while (remaining > 0) {
                            val toRead = minOf(remaining, tempBuf.size)
                            val readCount = stream.read(tempBuf, 0, toRead)
                            if (readCount == -1) throw IOException("Premature EOF while reading sync DATA payload")
                            outputStream.write(tempBuf, 0, readCount)
                            remaining -= readCount
                            totalReceived += readCount
                            onProgress?.invoke(totalReceived)
                        }
                    }
                    ID_DONE -> {
                        break
                    }
                    ID_FAIL -> {
                        val errBytes = readExact(stream, chunkLength)
                        val errMsg = String(errBytes, Charsets.UTF_8)
                        throw IOException("Sync pull failed: $errMsg")
                    }
                    else -> {
                        throw IOException("Sync pull unexpected packet ID: $chunkId")
                    }
                }
            }

            outputStream.flush()

            val quitPacket = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            quitPacket.putInt(ID_QUIT)
            quitPacket.putInt(0)
            try {
                stream.write(quitPacket.array())
            } catch (ignored: Exception) {}
        } finally {
            stream.close()
        }
    }

    private fun readExact(stream: AdbStream, count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val bytesRead = stream.read(result, offset, count - offset)
            if (bytesRead == -1) {
                throw IOException("Stream ended prematurely while waiting for sync response (read $offset of $count bytes)")
            }
            offset += bytesRead
        }
        return result
    }
}

package com.atv.adbcommander.adb

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An ADB logical stream representing a single connection (shell, sync, etc.).
 */
class AdbStream(
    private val connection: AdbConnection,
    val localId: Int
) : Closeable {

    var remoteId: Int = 0
        internal set

    private val readQueue = LinkedBlockingQueue<ByteArray>()
    private val isClosed = AtomicBoolean(false)
    private var writeLatch = CountDownLatch(1)
    private var currentBuffer: ByteArray? = null
    private var currentBufferOffset = 0

    internal fun notifyReady(remoteId: Int) {
        this.remoteId = remoteId
    }

    internal fun notifyWriteAcknowledged() {
        writeLatch.countDown()
    }

    internal fun addReceivedData(data: ByteArray) {
        if (!isClosed.get()) {
            readQueue.offer(data)
        }
    }

    internal fun notifyClosed() {
        if (isClosed.compareAndSet(false, true)) {
            // Signal EOF
            readQueue.offer(ByteArray(0))
            writeLatch.countDown()
        }
    }

    /**
     * Reads a chunk of data from the stream. Returns null when stream is closed.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun read(): ByteArray? {
        if (isClosed.get() && readQueue.isEmpty()) return null

        val data = readQueue.poll(90, TimeUnit.SECONDS) ?: return null
        if (data.isEmpty()) {
            // EOF sentinel
            return null
        }
        return data
    }

    /**
     * Reads up to len bytes from the stream into buffer starting at offset off.
     * Preserves unread bytes from chunks across multiple calls.
     * Returns the total number of bytes read into buffer, or -1 if EOF.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        while (currentBuffer == null || currentBufferOffset >= currentBuffer!!.size) {
            val next = read() ?: return -1
            currentBuffer = next
            currentBufferOffset = 0
        }

        val available = currentBuffer!!.size - currentBufferOffset
        val toCopy = minOf(len, available)
        System.arraycopy(currentBuffer!!, currentBufferOffset, b, off, toCopy)
        currentBufferOffset += toCopy
        if (currentBufferOffset >= currentBuffer!!.size) {
            currentBuffer = null
            currentBufferOffset = 0
        }
        return toCopy
    }

    /**
     * Read full output until stream closes as UTF-8 string.
     */
    fun readString(): String {
        val sb = StringBuilder()
        try {
            while (true) {
                val chunk = read() ?: break
                sb.append(String(chunk, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // Stream closed or error
        }
        return sb.toString()
    }

    /**
     * Read full raw binary data until stream closes.
     */
    fun readBytes(): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        try {
            while (true) {
                val chunk = read() ?: break
                baos.write(chunk)
            }
        } catch (e: Exception) {
            // Stream closed or error
        }
        return baos.toByteArray()
    }

    /**
     * Writes data payload to the stream.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun write(data: ByteArray) {
        if (isClosed.get()) throw IOException("Stream is closed")

        var offset = 0
        val maxChunk = connection.maxData

        while (offset < data.size) {
            val chunkSize = minOf(maxChunk, data.size - offset)
            val chunk = ByteArray(chunkSize)
            System.arraycopy(data, offset, chunk, 0, chunkSize)

            writeLatch = CountDownLatch(1)
            connection.sendWrite(localId, remoteId, chunk)

            // Wait for OKAY from device
            if (!writeLatch.await(15, TimeUnit.SECONDS)) {
                throw IOException("Write timeout waiting for OKAY from device")
            }

            offset += chunkSize
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                connection.sendClose(localId, remoteId)
            } catch (ignored: Exception) {
            }
            readQueue.offer(ByteArray(0))
            writeLatch.countDown()
            connection.removeStream(localId)
        }
    }

    fun isStreamClosed(): Boolean = isClosed.get()
}

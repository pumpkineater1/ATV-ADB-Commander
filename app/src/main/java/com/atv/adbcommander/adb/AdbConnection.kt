package com.atv.adbcommander.adb

import android.util.Log
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the TCP socket connection to the Android TV, handles ADB handshake,
 * authentication, and multiplexes logical AdbStreams.
 */
class AdbConnection(
    val host: String,
    val port: Int,
    private val crypto: AdbCrypto,
    private val onAuthPromptListener: (() -> Unit)? = null,
    private val onDisconnectListener: ((Throwable?) -> Unit)? = null
) : Closeable {

    companion object {
        private const val TAG = "AdbConnection"
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val writeLock = Any()

    internal fun sendMessage(msg: AdbProtocol.AdbMessage) {
        val out = outputStream ?: throw IOException("Output stream closed")
        synchronized(writeLock) {
            msg.write(out)
            out.flush()
        }
    }

    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val pendingOpens = ConcurrentHashMap<Int, Pair<AdbStream, CountDownLatch>>()
    private val nextStreamId = AtomicInteger(1)

    private val isConnected = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)
    private var connectionThread: Thread? = null

    private val connectLatch = CountDownLatch(1)
    private var sentSignature = false

    var maxData: Int = AdbProtocol.CONNECT_MAXDATA
        private set

    /**
     * Connects to the device, performs ADB handshake and authentication.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun connect() {
        val s = Socket()
        s.trafficClass = 0x10 // IPTOS_LOWDELAY: prioritize minimum latency across IP stack
        s.tcpNoDelay = true
        s.keepAlive = true
        s.sendBufferSize = 64 * 1024
        s.receiveBufferSize = 64 * 1024
        s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        socket = s
        inputStream = s.getInputStream()
        outputStream = s.getOutputStream()

        // Start background message listener
        val thread = Thread({ runReceiverLoop() }, "ADB-Receiver-$host")
        connectionThread = thread
        thread.start()

        // Send initial CNXN packet
        sendMessage(AdbProtocol.createConnectMessage(maxData))

        // Wait for connection handshake to complete (or fail)
        if (!connectLatch.await(45, TimeUnit.SECONDS)) {
            close()
            throw IOException("Handshake timeout connecting to $host:$port")
        }

        if (!isConnected.get()) {
            close()
            throw IOException("Failed to authenticate with $host:$port")
        }
    }

    private fun runReceiverLoop() {
        var loopError: Throwable? = null
        try {
            while (!isClosed.get()) {
                val inStream = inputStream ?: break
                val msg = AdbProtocol.AdbMessage.read(inStream)

                when (msg.command) {
                    AdbProtocol.CMD_CNXN -> {
                        maxData = minOf(msg.arg1, 65536)
                        isConnected.set(true)
                        connectLatch.countDown()
                        Log.d(TAG, "Connected to $host:$port, maxData=$maxData")
                    }

                    AdbProtocol.CMD_AUTH -> {
                        if (msg.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                            if (!sentSignature) {
                                // Try signature auth first
                                sentSignature = true
                                val signature = crypto.signAdbTokenPayload(msg.payload)
                                sendMessage(AdbProtocol.createAuthMessage(AdbProtocol.AUTH_TYPE_SIGNATURE, signature))
                            } else {
                                // Device rejected signature (not yet paired), send public key and prompt TV user
                                onAuthPromptListener?.invoke()
                                val pubKey = crypto.getAdbPublicKeyPayload()
                                sendMessage(AdbProtocol.createAuthMessage(AdbProtocol.AUTH_TYPE_RSA_PUBLIC, pubKey))
                            }
                        }
                    }

                    AdbProtocol.CMD_OKAY -> {
                        val localId = msg.arg1
                        val remoteId = msg.arg0

                        // Check if this OKAY is in response to an OPEN request
                        val pending = pendingOpens[localId]
                        if (pending != null) {
                            val stream = pending.first
                            stream.notifyReady(remoteId)
                            streams[localId] = stream
                            pending.second.countDown()
                        } else {
                            // Otherwise, this is write acknowledgement
                            streams[localId]?.notifyWriteAcknowledged()
                        }
                    }

                    AdbProtocol.CMD_WRTE -> {
                        val localId = msg.arg1
                        val remoteId = msg.arg0

                        // Acknowledge the packet immediately
                        try {
                            sendMessage(AdbProtocol.createOkayMessage(localId, remoteId))
                        } catch (e: Exception) {
                            // Stream or connection closing
                        }

                        // Deliver data to stream
                        streams[localId]?.addReceivedData(msg.payload)
                    }

                    AdbProtocol.CMD_CLSE -> {
                        val localId = msg.arg1
                        val remoteId = msg.arg0
                        val stream = streams[localId]
                        if (stream != null) {
                            stream.notifyClosed()
                            streams.remove(localId)
                        }
                        // Also notify if pending open was rejected
                        val pending = pendingOpens.remove(localId)
                        pending?.second?.countDown()

                        try {
                            sendMessage(AdbProtocol.createCloseMessage(localId, remoteId))
                        } catch (ignored: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            loopError = e
            if (!isClosed.get()) {
                Log.e(TAG, "Receiver loop error: ${e.message}")
            }
        } finally {
            val wasConnected = isConnected.getAndSet(false)
            connectLatch.countDown()
            // Close all active streams
            for (stream in streams.values) {
                stream.notifyClosed()
            }
            streams.clear()
            close()
            if (wasConnected) {
                onDisconnectListener?.invoke(loopError)
            }
        }
    }

    /**
     * Opens an ADB logical stream to a specific destination (e.g. "shell:ls -la" or "sync:").
     */
    @Throws(IOException::class, InterruptedException::class)
    fun openStream(destination: String): AdbStream {
        if (!isConnected.get() || isClosed.get()) {
            throw IOException("ADB not connected")
        }

        val localId = nextStreamId.getAndIncrement()
        val stream = AdbStream(this, localId)
        val latch = CountDownLatch(1)
        pendingOpens[localId] = Pair(stream, latch)

        sendMessage(AdbProtocol.createOpenMessage(localId, destination))

        if (!latch.await(10, TimeUnit.SECONDS)) {
            pendingOpens.remove(localId)
            throw IOException("Timeout opening stream: $destination")
        }

        pendingOpens.remove(localId)
        if (stream.isStreamClosed()) {
            throw IOException("Stream rejected by device for destination: $destination")
        }

        return stream
    }

    internal fun sendWrite(localId: Int, remoteId: Int, data: ByteArray) {
        sendMessage(AdbProtocol.createWriteMessage(localId, remoteId, data))
    }

    internal fun sendClose(localId: Int, remoteId: Int) {
        try {
            sendMessage(AdbProtocol.createCloseMessage(localId, remoteId))
        } catch (ignored: Exception) {}
    }

    internal fun removeStream(localId: Int) {
        streams.remove(localId)
    }

    fun isConnectionOpen(): Boolean = isConnected.get() && !isClosed.get()

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            isConnected.set(false)
            connectLatch.countDown()
            try {
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }
}

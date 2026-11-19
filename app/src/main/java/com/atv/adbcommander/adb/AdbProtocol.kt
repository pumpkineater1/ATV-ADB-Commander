package com.atv.adbcommander.adb

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB protocol definitions, packet serialization, and parsing.
 */
object AdbProtocol {
    const val CMD_SYNC = 0x434e5953
    const val CMD_CNXN = 0x4e584e43
    const val CMD_OPEN = 0x4e45504f
    const val CMD_OKAY = 0x59414b4f
    const val CMD_CLSE = 0x45534c43
    const val CMD_WRTE = 0x45545257
    const val CMD_AUTH = 0x48545541

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC = 3

    const val CONNECT_VERSION = 0x01000000
    const val CONNECT_MAXDATA = 4096

    class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataChecksum: Int,
        val magic: Int,
        val payload: ByteArray
    ) {
        companion object {
            @Throws(IOException::class)
            fun read(inputStream: InputStream): AdbMessage {
                val header = ByteArray(24)
                var bytesRead = 0
                while (bytesRead < 24) {
                    val read = inputStream.read(header, bytesRead, 24 - bytesRead)
                    if (read < 0) throw IOException("ADB connection closed while reading header")
                    bytesRead += read
                }

                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val command = bb.int
                val arg0 = bb.int
                val arg1 = bb.int
                val length = bb.int
                val checksum = bb.int
                val magic = bb.int

                if (magic != (command xor -0x1)) {
                    throw IOException("Corrupt ADB packet header magic")
                }

                val payload = ByteArray(length)
                var payloadRead = 0
                while (payloadRead < length) {
                    val read = inputStream.read(payload, payloadRead, length - payloadRead)
                    if (read < 0) throw IOException("ADB connection closed while reading payload")
                    payloadRead += read
                }

                return AdbMessage(command, arg0, arg1, length, checksum, magic, payload)
            }
        }

        @Throws(IOException::class)
        fun write(outputStream: OutputStream) {
            val bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            bb.putInt(command)
            bb.putInt(arg0)
            bb.putInt(arg1)
            bb.putInt(payload.size)
            bb.putInt(calculateChecksum(payload))
            bb.putInt(command xor -0x1)

            if (payload.isEmpty()) {
                outputStream.write(bb.array())
            } else {
                val fullPacket = ByteArray(24 + payload.size)
                System.arraycopy(bb.array(), 0, fullPacket, 0, 24)
                System.arraycopy(payload, 0, fullPacket, 24, payload.size)
                outputStream.write(fullPacket)
            }
            outputStream.flush()
        }
    }

    fun calculateChecksum(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }

    fun createConnectMessage(maxData: Int): AdbMessage {
        val payload = "host::features=cmd,stat_v2,shell_v2\u0000".toByteArray(Charsets.UTF_8)
        return AdbMessage(
            command = CMD_CNXN,
            arg0 = CONNECT_VERSION,
            arg1 = maxData,
            dataLength = payload.size,
            dataChecksum = calculateChecksum(payload),
            magic = CMD_CNXN xor -0x1,
            payload = payload
        )
    }

    fun createAuthMessage(authType: Int, payload: ByteArray): AdbMessage {
        return AdbMessage(
            command = CMD_AUTH,
            arg0 = authType,
            arg1 = 0,
            dataLength = payload.size,
            dataChecksum = calculateChecksum(payload),
            magic = CMD_AUTH xor -0x1,
            payload = payload
        )
    }

    fun createOpenMessage(localId: Int, destination: String): AdbMessage {
        val destBytes = destination.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(destBytes.size + 1)
        System.arraycopy(destBytes, 0, payload, 0, destBytes.size)
        payload[destBytes.size] = 0 // null-terminated

        return AdbMessage(
            command = CMD_OPEN,
            arg0 = localId,
            arg1 = 0,
            dataLength = payload.size,
            dataChecksum = calculateChecksum(payload),
            magic = CMD_OPEN xor -0x1,
            payload = payload
        )
    }

    fun createWriteMessage(localId: Int, remoteId: Int, data: ByteArray): AdbMessage {
        return AdbMessage(
            command = CMD_WRTE,
            arg0 = localId,
            arg1 = remoteId,
            dataLength = data.size,
            dataChecksum = calculateChecksum(data),
            magic = CMD_WRTE xor -0x1,
            payload = data
        )
    }

    fun createOkayMessage(localId: Int, remoteId: Int): AdbMessage {
        val empty = ByteArray(0)
        return AdbMessage(
            command = CMD_OKAY,
            arg0 = localId,
            arg1 = remoteId,
            dataLength = 0,
            dataChecksum = 0,
            magic = CMD_OKAY xor -0x1,
            payload = empty
        )
    }

    fun createCloseMessage(localId: Int, remoteId: Int): AdbMessage {
        val empty = ByteArray(0)
        return AdbMessage(
            command = CMD_CLSE,
            arg0 = localId,
            arg1 = remoteId,
            dataLength = 0,
            dataChecksum = 0,
            magic = CMD_CLSE xor -0x1,
            payload = empty
        )
    }
}

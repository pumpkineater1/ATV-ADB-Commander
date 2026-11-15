package com.atv.adbcommander.adb

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Handles RSA KeyPair generation, storage, ADB public key formatting,
 * and signature generation for the ADB authentication challenge.
 */
class AdbCrypto(
    val keyPair: KeyPair,
    private val base64: AdbBase64 = AndroidAdbBase64()
) {
    companion object {
        const val KEY_LENGTH_BITS = 2048
        const val KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8
        const val KEY_LENGTH_WORDS = KEY_LENGTH_BYTES / 4

        // SHA-1 PKCS#1 v1.5 ASN.1 DigestInfo prefix
        private val SHA1_DIGEST_INFO = byteArrayOf(
            0x30.toByte(), 0x21.toByte(), 0x30.toByte(), 0x09.toByte(), 0x06.toByte(), 0x05.toByte(),
            0x2b.toByte(), 0x0e.toByte(), 0x03.toByte(), 0x02.toByte(), 0x1a.toByte(), 0x05.toByte(),
            0x00.toByte(), 0x04.toByte(), 0x14.toByte()
        )

        /**
         * Load existing keys or generate a new RSA 2048 keypair.
         */
        @Throws(IOException::class)
        fun loadOrGenerate(context: Context): AdbCrypto {
            val keyDir = File(context.filesDir, "adb")
            if (!keyDir.exists()) keyDir.mkdirs()

            val privKeyFile = File(keyDir, "adbkey")
            val pubKeyFile = File(keyDir, "adbkey.pub")

            if (privKeyFile.exists() && pubKeyFile.exists()) {
                try {
                    val kf = KeyFactory.getInstance("RSA")
                    val privBytes = FileInputStream(privKeyFile).use { it.readBytes() }
                    val pubBytes = FileInputStream(pubKeyFile).use { it.readBytes() }

                    val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                    val publicKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                    return AdbCrypto(KeyPair(publicKey, privateKey))
                } catch (e: Exception) {
                    privKeyFile.delete()
                    pubKeyFile.delete()
                }
            }

            // Generate new KeyPair
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(KEY_LENGTH_BITS)
            val keyPair = kpg.generateKeyPair()

            FileOutputStream(privKeyFile).use { it.write(keyPair.private.encoded) }
            FileOutputStream(pubKeyFile).use { it.write(keyPair.public.encoded) }

            return AdbCrypto(keyPair)
        }

        /**
         * Converts standard RSAPublicKey to Android's RSAPublicKey struct.
         */
        private fun convertRsaPublicKeyToAdbFormat(pubkey: RSAPublicKey): ByteArray {
            val r32 = BigInteger.ZERO.setBit(32)
            var n = pubkey.modulus
            val r = BigInteger.ZERO.setBit(KEY_LENGTH_WORDS * 32)
            var rr = r.modPow(BigInteger.valueOf(2), n)
            var rem = n.remainder(r32)
            val n0inv = rem.modInverse(r32)

            val myN = IntArray(KEY_LENGTH_WORDS)
            val myRr = IntArray(KEY_LENGTH_WORDS)

            for (i in 0 until KEY_LENGTH_WORDS) {
                var res = rr.divideAndRemainder(r32)
                rr = res[0]
                rem = res[1]
                myRr[i] = rem.toInt()

                res = n.divideAndRemainder(r32)
                n = res[0]
                rem = res[1]
                myN[i] = rem.toInt()
            }

            val bb = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            bb.putInt(KEY_LENGTH_WORDS)
            bb.putInt(n0inv.negate().toInt())
            for (i in 0 until KEY_LENGTH_WORDS) {
                bb.putInt(myN[i])
            }
            for (i in 0 until KEY_LENGTH_WORDS) {
                bb.putInt(myRr[i])
            }
            bb.putInt(pubkey.publicExponent.toInt())

            return bb.array()
        }
    }

    /**
     * Signs the 20-byte ADB challenge token using the RSA private key
     * formatted as a standard PKCS#1 v1.5 block:
     * 0x00 0x01 [0xFF * (k - 3 - DigestInfo.length - token.length)] 0x00 [DigestInfo] [token]
     */
    fun signAdbTokenPayload(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)

        val input = ByteBuffer.allocate(KEY_LENGTH_BYTES)
        input.put(0x00.toByte())
        input.put(0x01.toByte())
        val paddingLength = KEY_LENGTH_BYTES - 3 - SHA1_DIGEST_INFO.size - token.size
        for (i in 0 until paddingLength) {
            input.put(0xff.toByte())
        }
        input.put(0x00.toByte())
        input.put(SHA1_DIGEST_INFO)
        input.put(token)

        return cipher.doFinal(input.array())
    }

    /**
     * Formats the ADB RSA public key with user info suitable for A_AUTH payload.
     */
    fun getAdbPublicKeyPayload(): ByteArray {
        val converted = convertRsaPublicKeyToAdbFormat(keyPair.public as RSAPublicKey)
        val encoded = base64.encodeToString(converted)
        val keyString = "$encoded atv_commander@android\u0000"
        return keyString.toByteArray(Charsets.UTF_8)
    }
}

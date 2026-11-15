package com.atv.adbcommander.adb

import android.util.Base64

/**
 * Interface and Android implementation for Base64 encoding/decoding.
 */
interface AdbBase64 {
    fun encodeToString(data: ByteArray): String
    fun decode(data: String): ByteArray
}

class AndroidAdbBase64 : AdbBase64 {
    override fun encodeToString(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    override fun decode(data: String): ByteArray {
        return Base64.decode(data, Base64.DEFAULT)
    }
}

package com.atv.adbcommander.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.atv.adbcommander.model.DiscoveredDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

class TvDiscoveryScanner(private val context: Context) {

    /**
     * Scans the local network subnet for open ADB port 5555.
     */
    fun scanSubnet(port: Int = 5555, timeoutMs: Int = 350): Flow<DiscoveredDevice> = flow {
        val localIp = getLocalIpAddress()
        if (localIp == null) {
            return@flow
        }

        val subnetPrefix = localIp.substringBeforeLast(".") + "."
        val foundIps = mutableSetOf<String>()

        // Scan 1 to 254 in parallel batches of 32
        coroutineScope {
            val deferreds = mutableListOf<Deferred<DiscoveredDevice?>>()

            for (i in 1..254) {
                val targetIp = "$subnetPrefix$i"
                if (targetIp == localIp) continue // Skip self

                deferreds.add(async(Dispatchers.IO) {
                    if (isPortOpen(targetIp, port, timeoutMs)) {
                        DiscoveredDevice(
                            ip = targetIp,
                            port = port,
                            name = "Android TV ($targetIp)"
                        )
                    } else {
                        null
                    }
                })

                if (deferreds.size >= 32 || i == 254) {
                    val results = deferreds.awaitAll().filterNotNull()
                    for (device in results) {
                        if (foundIps.add(device.ip)) {
                            emit(device)
                        }
                    }
                    deferreds.clear()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return null
    }
}

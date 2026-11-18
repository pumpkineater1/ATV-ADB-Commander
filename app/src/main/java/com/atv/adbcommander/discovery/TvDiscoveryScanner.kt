package com.atv.adbcommander.discovery

import android.content.Context
import com.atv.adbcommander.model.DiscoveredDevice
import com.atv.adbcommander.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.net.Socket

class TvDiscoveryScanner(private val context: Context) {

    /**
     * Scans the local network (both IPv6 neighbors and IPv4 subnet) for open ADB port 5555.
     */
    fun scanSubnet(port: Int = 5555, timeoutMs: Int = 350): Flow<DiscoveredDevice> = flow {
        val foundIps = mutableSetOf<String>()

        // 1. First probe local IPv6 neighbors (rapid detection)
        val ipv6Neighbors = getIpv6Neighbors()
        for (targetIp in ipv6Neighbors) {
            if (isPortOpen(targetIp, port, timeoutMs)) {
                if (foundIps.add(targetIp)) {
                    val shortened = NetworkUtils.shortenIpv6(targetIp)
                    emit(
                        DiscoveredDevice(
                            ip = targetIp,
                            port = port,
                            name = "Android TV ($shortened)"
                        )
                    )
                }
            }
        }

        // 2. Scan IPv4 subnet
        val localIp = getLocalIpAddress()
        if (localIp != null) {
            val subnetPrefix = localIp.substringBeforeLast(".") + "."

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
        }
    }.flowOn(Dispatchers.IO)

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                val socketAddress = NetworkUtils.resolveSocketAddress(ip, port)
                socket.connect(socketAddress, timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getIpv6Neighbors(): List<String> {
        val list = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec("ip -6 neigh")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.isEmpty() || l.contains("FAILED", ignoreCase = true)) continue
                val parts = l.split(Regex("\\s+"))
                if (parts.isNotEmpty()) {
                    val candidate = parts[0].trim().removePrefix("[").removeSuffix("]")
                    if (candidate.contains(":") && !candidate.startsWith("ff02") && !candidate.startsWith("::1")) {
                        list.add(candidate)
                    }
                }
            }
            reader.close()
            process.waitFor()
        } catch (ignored: Exception) {}
        return list.distinct()
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

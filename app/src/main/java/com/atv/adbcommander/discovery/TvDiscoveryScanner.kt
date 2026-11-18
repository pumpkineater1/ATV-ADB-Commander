package com.atv.adbcommander.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.atv.adbcommander.model.DiscoveredDevice
import com.atv.adbcommander.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class TvDiscoveryScanner(private val context: Context) {

    companion object {
        private const val TAG = "TvDiscoveryScanner"
    }

    /**
     * Finds the very first reachable Android TV on the local network (IPv4 or IPv6)
     * and returns immediately, cancelling any remaining scan work.
     */
    suspend fun findFirstTv(port: Int = 5555, timeoutMs: Long = 7000L): DiscoveredDevice? {
        return withTimeoutOrNull(timeoutMs) {
            scanSubnet(port).firstOrNull()
        }
    }

    /**
     * Dual-stack continuous discovery flow.
     * Concurrently runs:
     * 1. Android NsdManager (mDNS / DNS-SD) for _adb._tcp
     * 2. Neighbor cache probing for IPv4 and IPv6
     * 3. Parallel IPv4 local subnet scanner (1..254)
     */
    fun scanSubnet(port: Int = 5555, timeoutMs: Int = 350): Flow<DiscoveredDevice> = channelFlow {
        val foundIps = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        val selfIps = getSelfIpAddresses()

        // 1. Android NsdManager mDNS Discovery (Instant dual-stack IPv4/IPv6 detection)
        launch(Dispatchers.IO) {
            try {
                startNsdDiscovery(port) { device ->
                    val cleanIp = device.ip.removePrefix("[").removeSuffix("]").substringBefore("%")
                    if (!selfIps.contains(cleanIp) && foundIps.add(cleanIp)) {
                        Log.d(TAG, "mDNS discovered Android TV: ${device.ip}:$port (${device.name})")
                        trySend(device)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "mDNS discovery exception: ${e.message}")
            }
        }

        // 2. Neighbor Cache Probing (Queries known network neighbors)
        launch(Dispatchers.IO) {
            val neighborCandidates = getNeighborCandidates()
            for (candidate in neighborCandidates) {
                val clean = candidate.removePrefix("[").removeSuffix("]").substringBefore("%")
                if (selfIps.contains(clean)) continue

                if (isPortOpen(candidate, port, timeoutMs = 450)) {
                    if (foundIps.add(clean)) {
                        val name = if (clean.contains(":")) {
                            "Android TV (${NetworkUtils.shortenIpv6(clean)})"
                        } else {
                            "Android TV ($clean)"
                        }
                        Log.d(TAG, "Neighbor discovered Android TV: $candidate:$port")
                        trySend(DiscoveredDevice(ip = candidate, port = port, name = name))
                    }
                }
            }
        }

        // 3. Parallel IPv4 Subnet Scanner (Scans 1..254 in fast 32-socket batches)
        launch(Dispatchers.IO) {
            val localIp = getLocalIpv4Address()
            if (localIp != null) {
                val subnetPrefix = localIp.substringBeforeLast(".") + "."

                coroutineScope {
                    val deferreds = mutableListOf<Deferred<DiscoveredDevice?>>()

                    for (i in 1..254) {
                        val targetIp = "$subnetPrefix$i"
                        if (selfIps.contains(targetIp)) continue

                        deferreds.add(async(Dispatchers.IO) {
                            if (isPortOpen(targetIp, port, timeoutMs)) {
                                DiscoveredDevice(
                                    ip = targetIp,
                                    port = port,
                                    name = "Android TV ($targetIp)"
                                )
                            } else null
                        })

                        if (deferreds.size >= 32 || i == 254) {
                            val results = deferreds.awaitAll().filterNotNull()
                            for (device in results) {
                                val clean = device.ip.removePrefix("[").removeSuffix("]")
                                if (foundIps.add(clean)) {
                                    Log.d(TAG, "Subnet scan discovered Android TV: ${device.ip}:$port")
                                    trySend(device)
                                }
                            }
                            deferreds.clear()
                        }
                    }
                }
            }
        }

        // Keep flow active for up to 10 seconds or until caller cancels
        delay(10000L)
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

    private suspend fun startNsdDiscovery(targetPort: Int, onFound: (DiscoveredDevice) -> Unit) {
        val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val resolveMutex = Mutex()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NsdManager discovery started for $regType")
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NsdManager start discovery failed: $errorCode")
                try { nsdManager.stopServiceDiscovery(this) } catch (ignored: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                val sName = service.serviceName ?: ""
                val sType = service.serviceType ?: ""
                Log.d(TAG, "NsdManager service found: $sName ($sType)")

                // Resolve matching ADB or TV services
                CoroutineScope(Dispatchers.IO).launch {
                    resolveMutex.withLock {
                        try {
                            val resolved = suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                        if (cont.isActive) cont.resume(null)
                                    }
                                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                        if (cont.isActive) cont.resume(serviceInfo)
                                    }
                                })
                            }
                            if (resolved != null) {
                                val host = resolved.host?.hostAddress?.removePrefix("/")?.substringBefore("%")
                                val p = if (resolved.port > 0) resolved.port else targetPort
                                if (!host.isNullOrBlank()) {
                                    val devName = if (host.contains(":")) {
                                        "Android TV (${NetworkUtils.shortenIpv6(host)})"
                                    } else {
                                        "Android TV ($host)"
                                    }
                                    onFound(DiscoveredDevice(ip = host, port = p, name = devName))
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            }
        }

        try {
            nsdManager.discoverServices("_adb._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            awaitCancellation()
        } catch (e: Exception) {
            Log.w(TAG, "mDNS error: ${e.message}")
        } finally {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (ignored: Exception) {}
        }
    }

    private fun getNeighborCandidates(): List<String> {
        val list = mutableListOf<String>()
        // 1. Try ip neigh
        try {
            val process = Runtime.getRuntime().exec("ip neigh")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.isEmpty() || l.contains("FAILED", ignoreCase = true)) continue
                val parts = l.split(Regex("\\s+"))
                if (parts.isNotEmpty()) {
                    val candidate = parts[0].trim().removePrefix("[").removeSuffix("]")
                    if (!candidate.startsWith("ff02") && !candidate.startsWith("::1") && candidate != "127.0.0.1") {
                        list.add(candidate)
                    }
                }
            }
            reader.close()
            process.waitFor()
        } catch (ignored: Exception) {}

        // 2. Try ip -6 neigh
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

    private fun getSelfIpAddresses(): Set<String> {
        val set = mutableSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    addr.hostAddress?.let {
                        val clean = it.removePrefix("/").substringBefore("%")
                        set.add(clean)
                    }
                }
            }
        } catch (ignored: Exception) {}
        return set
    }

    private fun getLocalIpv4Address(): String? {
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

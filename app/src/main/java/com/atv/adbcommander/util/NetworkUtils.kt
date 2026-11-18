package com.atv.adbcommander.util

import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Parses host and port from various user input formats:
     * - "192.168.1.37" -> ("192.168.1.37", 5555)
     * - "192.168.1.37:5555" -> ("192.168.1.37", 5555)
     * - "[2401:9640:7002:ba0e:4a7c:2922:27e0:bc6e]:5555" -> ("2401:9640:7002:ba0e:4a7c:2922:27e0:bc6e", 5555)
     * - "[2401:9640:7002:ba0e:4a7c:2922:27e0:bc6e]" -> ("2401:9640:7002:ba0e:4a7c:2922:27e0:bc6e", 5555)
     * - "2401:9640:7002:ba0e:4a7c:2922:27e0:bc6e" -> ("2401:9640:7002:ba0e:4a7c:2922:27e0:bc6e", 5555)
     * - "fe80::851b:c336:3e96:2069" -> ("fe80::851b:c336:3e96:2069", 5555)
     * - "fe80::851b:c336:3e96:2069%wlan0" -> ("fe80::851b:c336:3e96:2069%wlan0", 5555)
     */
    fun parseHostAndPort(rawInput: String, defaultPort: Int = 5555): Pair<String, Int> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "" to defaultPort

        // Bracketed IPv6: [fe80::...]:5555 or [fe80::...]
        if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf("]")
            if (closeIdx != -1) {
                val host = trimmed.substring(1, closeIdx).trim()
                val remainder = trimmed.substring(closeIdx + 1).trim()
                val port = if (remainder.startsWith(":")) {
                    remainder.substring(1).trim().toIntOrNull() ?: defaultPort
                } else defaultPort
                return host to port
            }
        }

        // Multiple colons indicate an IPv6 address
        val colonCount = trimmed.count { it == ':' }
        if (colonCount >= 2) {
            // Unbracketed IPv6
            return trimmed to defaultPort
        }

        // IPv4 or hostname with optional port (single colon)
        if (trimmed.contains(":")) {
            val host = trimmed.substringBefore(":").trim()
            val port = trimmed.substringAfter(":").trim().toIntOrNull() ?: defaultPort
            return host to port
        }

        return trimmed to defaultPort
    }

    /**
     * Checks if a host is an IPv6 address.
     */
    fun isIpv6(host: String): Boolean {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        return clean.contains(":")
    }

    /**
     * Formats host and port following RFC 5952 standard:
     * - IPv4: "192.168.1.37:5555"
     * - IPv6: "[2401:9640:...]:5555"
     */
    fun formatAddress(host: String, port: Int): String {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        return if (clean.contains(":")) "[$clean]:$port" else "$clean:$port"
    }

    /**
     * Shortens IPv6 for concise UI badges/titles while keeping it identifiable:
     * e.g. "2401:9640:7002:ba0e:4a7c:2922:27e0:bc6e" -> "2401:9640...bc6e"
     */
    fun shortenIpv6(ipv6: String): String {
        val clean = ipv6.trim().removePrefix("[").removeSuffix("]")
        if (!clean.contains(":") || clean.length <= 18) return clean
        val parts = clean.split(":")
        return if (parts.size >= 4) {
            "${parts.take(2).joinToString(":")}:...:${parts.last()}"
        } else clean
    }

    /**
     * Resolves an InetSocketAddress from host (IPv4 or IPv6) and port,
     * automatically binding scope interface for link-local IPv6 if missing.
     */
    fun resolveSocketAddress(rawHost: String, port: Int): InetSocketAddress {
        val cleanHost = rawHost.trim().removePrefix("[").removeSuffix("]")
        val inetAddress = try {
            val addr = InetAddress.getByName(cleanHost)
            if (addr is Inet6Address && addr.isLinkLocalAddress && addr.scopedInterface == null && addr.scopeId == 0) {
                val iface = findActiveWifiInterface()
                if (iface != null) {
                    Inet6Address.getByAddress(cleanHost, addr.address, iface)
                } else addr
            } else addr
        } catch (e: Exception) {
            return InetSocketAddress(cleanHost, port)
        }
        return InetSocketAddress(inetAddress, port)
    }

    fun findActiveWifiInterface(): NetworkInterface? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback) {
                    val name = iface.name.lowercase()
                    if (name.contains("wlan") || name.contains("swlan") || name.contains("eth") || name.contains("ap")) {
                        return iface
                    }
                }
            }
            null
        } catch (ignored: Exception) {
            null
        }
    }
}

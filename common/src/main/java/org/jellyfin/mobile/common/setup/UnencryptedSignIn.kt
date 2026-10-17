package org.jellyfin.mobile.common.setup

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException

/**
 * Whether signing in to [serverUrl] would send the password, and then every request's token,
 * unencrypted across the internet: plain HTTP to a server that isn't on the local network (or a
 * Tailscale network, which encrypts on its own). An address that can't be read doesn't count.
 */
fun signsInUnencryptedOverInternet(serverUrl: String): Boolean {
    val uri = try {
        URI(serverUrl.trim())
    } catch (_: URISyntaxException) {
        return false
    }
    val host = uri.host?.lowercase()?.removeSurrounding("[", "]")
    return uri.scheme.equals("http", ignoreCase = true) && !host.isNullOrEmpty() && !isLocalHost(host)
}

private fun isLocalHost(host: String): Boolean = when {
    host == "localhost" -> true
    IPV4.matches(host) || ':' in host -> isLocalAddress(host)
    // A bare name ("jellyfin") only resolves on the local network
    '.' !in host -> true
    else -> LOCAL_SUFFIXES.any { host.endsWith(it) }
}

/** [address] is an IP literal, so this parses it without looking anything up. */
private fun isLocalAddress(address: String): Boolean {
    val ip = try {
        InetAddress.getByName(address)
    } catch (_: UnknownHostException) {
        return false
    }
    val bytes = ip.address
    val first = bytes[0].toInt() and BYTE
    return ip.isLoopbackAddress || ip.isSiteLocalAddress || ip.isLinkLocalAddress ||
        // IPv6 unique local addresses, fc00::/7
        (bytes.size == IPV6_BYTES && first and ULA_MASK == ULA_PREFIX) ||
        // 100.64.0.0/10: carrier-grade NAT, which Tailscale's addresses come from
        (bytes.size == IPV4_BYTES && first == CGNAT_FIRST && (bytes[1].toInt() and CGNAT_MASK) == CGNAT_SECOND)
}

private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal", ".ts.net")
private const val BYTE = 0xFF
private const val IPV4_BYTES = 4
private const val IPV6_BYTES = 16
private const val ULA_MASK = 0xFE
private const val ULA_PREFIX = 0xFC
private const val CGNAT_FIRST = 100
private const val CGNAT_MASK = 0xC0
private const val CGNAT_SECOND = 64

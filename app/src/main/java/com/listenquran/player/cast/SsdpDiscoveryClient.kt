package com.listenquran.player.cast

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SsdpPassiveEvent {
    data class Alive(val device: OutputDevice) : SsdpPassiveEvent
    data class ByeBye(val locationOrUsn: String) : SsdpPassiveEvent
}

interface DeviceDiscoveryClient {
    suspend fun discoverDevices(timeoutMs: Long = 3000L): List<OutputDevice>
    fun discoverDevicesFlow(timeoutMs: Long = 3000L): Flow<OutputDevice>
    fun passiveDiscoveryFlow(): Flow<SsdpPassiveEvent>
    suspend fun verifyDevice(locationUrl: String): OutputDevice?
    fun getLocalLanIpv4Address(): InetAddress?
}

@Singleton
class SsdpDiscoveryClient @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
) : DeviceDiscoveryClient {

    private val client: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    override suspend fun discoverDevices(timeoutMs: Long): List<OutputDevice> = withContext(Dispatchers.IO) {
        val seen = ConcurrentHashMap.newKeySet<String>()
        val results = ArrayList<OutputDevice>()
        discoverDevicesFlow(timeoutMs).collect { device ->
            if (seen.add(device.id)) {
                results.add(device)
            }
        }
        results.sortedBy { it.name.lowercase() }
    }

    override fun discoverDevicesFlow(timeoutMs: Long): Flow<OutputDevice> = channelFlow {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("ListenQuranSSDP")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        val seenLocations = ConcurrentHashMap.newKeySet<String>()
        val fetchJobs = ArrayList<kotlinx.coroutines.Job>()
        var socket: DatagramSocket? = null
        try {
            val lanAddress = getLocalLanIpv4Address()
            socket = if (lanAddress != null) {
                DatagramSocket(0, lanAddress)
            } else {
                DatagramSocket()
            }.apply {
                reuseAddress = true
                soTimeout = 300
            }

            val multicastGroup = InetAddress.getByName(SSDP_ADDRESS)
            for (target in SEARCH_TARGETS) {
                val payload = buildSearchRequest(target).toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(payload, payload.size, multicastGroup, SSDP_PORT)
                runCatching { socket.send(packet) }
            }

            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(500L, 6000L)
            val buffer = ByteArray(4096)
            while (isActive && System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val announcement = DlnaXmlParser.parseSsdpResponse(raw) ?: continue
                    if (DlnaXmlParser.isPrivateLanHost(announcement.location) &&
                        seenLocations.add(announcement.location)
                    ) {
                        val loc = announcement.location
                        fetchJobs += launch(Dispatchers.IO) {
                            val dev = fetchAndParseDevice(loc)
                            if (dev != null) {
                                trySend(dev)
                            }
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Continue polling until overall discovery timeout expires.
                }
            }
        } catch (_: Exception) {
            // Best-effort discovery.
        } finally {
            runCatching { socket?.close() }
            runCatching {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }
        fetchJobs.forEach { it.join() }
    }.flowOn(Dispatchers.IO)

    override fun passiveDiscoveryFlow(): Flow<SsdpPassiveEvent> = channelFlow {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("ListenQuranSSDPPassive")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        var multicastSocket: MulticastSocket? = null
        val group = runCatching { InetAddress.getByName(SSDP_ADDRESS) }.getOrNull()
        val netIf = findPrimaryWifiNetworkInterface()

        val readerJob = launch(Dispatchers.IO) {
            try {
                val sock = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(SSDP_PORT))
                    soTimeout = 1000
                }
                multicastSocket = sock
                if (group != null) {
                    if (netIf != null) {
                        runCatching { sock.joinGroup(InetSocketAddress(group, SSDP_PORT), netIf) }
                            .onFailure { runCatching { @Suppress("DEPRECATION") sock.joinGroup(group) } }
                    } else {
                        runCatching { @Suppress("DEPRECATION") sock.joinGroup(group) }
                    }
                }
                val buffer = ByteArray(4096)
                while (isActive && !sock.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        sock.receive(packet)
                        val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (!raw.startsWith("NOTIFY ", ignoreCase = true)) continue
                        val lower = raw.lowercase(Locale.US)
                        val isRenderer = lower.contains("mediarenderer") ||
                            lower.contains("avtransport") ||
                            lower.contains("renderingcontrol")
                        if (!isRenderer) continue

                        if (lower.contains("ssdp:byebye")) {
                            val usn = raw.lines().firstOrNull { it.trim().startsWith("usn:", true) }
                                ?.substringAfter(':')?.trim()
                            val loc = raw.lines().firstOrNull { it.trim().startsWith("location:", true) }
                                ?.substringAfter(':')?.trim()
                            val key = loc ?: usn?.substringBefore("::")
                            if (!key.isNullOrBlank()) {
                                trySend(SsdpPassiveEvent.ByeBye(key))
                            }
                            continue
                        }

                        val announcement = DlnaXmlParser.parseSsdpResponse(raw) ?: continue
                        if (!DlnaXmlParser.isPrivateLanHost(announcement.location)) continue
                        launch(Dispatchers.IO) {
                            val device = fetchAndParseDevice(announcement.location)
                            if (device != null) {
                                trySend(SsdpPassiveEvent.Alive(device))
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Loop while active.
                    }
                }
            } catch (_: Exception) {
                // Best effort passive listener; active M-SEARCH still runs.
            }
        }

        awaitClose {
            readerJob.cancel()
            runCatching {
                if (group != null) {
                    @Suppress("DEPRECATION")
                    multicastSocket?.leaveGroup(group)
                }
            }
            runCatching { multicastSocket?.close() }
            runCatching {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun verifyDevice(locationUrl: String): OutputDevice? = withContext(Dispatchers.IO) {
        fetchAndParseDevice(locationUrl)
    }

    override fun getLocalLanIpv4Address(): InetAddress? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            var fallback: InetAddress? = null
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val name = iface.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("docker") ||
                    name.startsWith("br-") || name.startsWith("veth")
                ) {
                    continue
                }
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                            return addr
                        }
                        if (fallback == null) {
                            fallback = addr
                        }
                    }
                }
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    private fun findPrimaryWifiNetworkInterface(): NetworkInterface? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            interfaces.firstOrNull { iface ->
                iface.isUp && !iface.isLoopback && !iface.isVirtual &&
                    (iface.name.startsWith("wlan", ignoreCase = true) ||
                        iface.name.startsWith("eth", ignoreCase = true))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchAndParseDevice(locationUrl: String): OutputDevice? {
        if (!DlnaXmlParser.isPrivateLanHost(locationUrl)) return null
        return try {
            val request = Request.Builder().url(locationUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val xml = response.body?.string() ?: return null
                DlnaXmlParser.parseDeviceDescription(xml, locationUrl)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSearchRequest(searchTarget: String): String {
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }
    }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        val SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "ssdp:all",
        )
    }
}

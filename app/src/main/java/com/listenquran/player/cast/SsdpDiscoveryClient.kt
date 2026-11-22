package com.listenquran.player.cast

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceDiscoveryClient {
    suspend fun discoverDevices(timeoutMs: Long = 3000L): List<OutputDevice>
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
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("ListenQuranSSDP")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        val locations = LinkedHashSet<String>()
        var socket: DatagramSocket? = null
        try {
            val lanAddress = getLocalLanIpv4Address()
            socket = if (lanAddress != null) {
                DatagramSocket(0, lanAddress)
            } else {
                DatagramSocket()
            }.apply {
                reuseAddress = true
                soTimeout = 350
            }

            val multicastGroup = InetAddress.getByName(SSDP_ADDRESS)
            for (target in SEARCH_TARGETS) {
                val payload = buildSearchRequest(target).toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(payload, payload.size, multicastGroup, SSDP_PORT)
                runCatching { socket.send(packet) }
            }

            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(500L, 6000L)
            val buffer = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val announcement = DlnaXmlParser.parseSsdpResponse(raw) ?: continue
                    if (DlnaXmlParser.isPrivateLanHost(announcement.location)) {
                        locations.add(announcement.location)
                    }
                } catch (_: SocketTimeoutException) {
                    // Continue polling until overall discovery timeout expires.
                }
            }
        } catch (_: Exception) {
            // Return whatever announcements were collected safely without throwing.
        } finally {
            runCatching { socket?.close() }
            runCatching {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }

        if (locations.isEmpty()) return@withContext emptyList()

        val resolved = locations.map { location ->
            async { fetchAndParseDevice(location) }
        }.awaitAll().filterNotNull()

        resolved.distinctBy { it.id }.sortedBy { it.name.lowercase() }
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

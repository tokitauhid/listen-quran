package com.listenquran.player.cast

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

data class SsdpAnnouncement(
    val location: String,
    val usn: String?,
    val st: String?,
    val server: String?,
)

object DlnaXmlParser {

    const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"

    fun parseSsdpResponse(rawPacket: String): SsdpAnnouncement? {
        if (rawPacket.isBlank()) return null
        val headers = LinkedHashMap<String, String>()
        val lines = rawPacket.split("\r\n", "\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("HTTP/", ignoreCase = true) ||
                trimmed.startsWith("NOTIFY ", ignoreCase = true) ||
                trimmed.startsWith("M-SEARCH ", ignoreCase = true)
            ) {
                continue
            }
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) continue
            val key = trimmed.substring(0, colonIdx).trim().lowercase(Locale.US)
            val value = trimmed.substring(colonIdx + 1).trim()
            if (value.isNotEmpty() && !headers.containsKey(key)) {
                headers[key] = value
            }
        }
        val location = headers["location"] ?: return null
        val lowerLocation = location.lowercase(Locale.US)
        if (!lowerLocation.startsWith("http://") && !lowerLocation.startsWith("https://")) {
            return null
        }
        return SsdpAnnouncement(
            location = location,
            usn = headers["usn"],
            st = headers["st"] ?: headers["nt"],
            server = headers["server"],
        )
    }

    fun parseDeviceDescription(xml: String, locationUrl: String): OutputDevice? {
        if (xml.isBlank()) return null
        return try {
            val doc = newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val root = doc.documentElement ?: return null

            val urlBase = firstDirectChildText(root, "URLBase")?.trim().takeUnless { it.isNullOrBlank() }
                ?: locationUrl

            val devices = ArrayList<Element>()
            val topDevice = directChildElements(root, "device").firstOrNull()
                ?: if (localNameOf(root).equals("device", ignoreCase = true)) root else null

            if (topDevice != null) {
                collectDevices(topDevice, devices)
            }

            for (deviceEl in devices) {
                val serviceList = directChildElements(deviceEl, "serviceList").firstOrNull() ?: continue
                val services = directChildElements(serviceList, "service")

                var avTransportUrl: String? = null
                var renderingControlUrl: String? = null

                for (service in services) {
                    val serviceType = firstDirectChildText(service, "serviceType")?.trim().orEmpty()
                    val controlPath = firstDirectChildText(service, "controlURL")?.trim().orEmpty()
                    if (controlPath.isEmpty()) continue

                    val resolvedControlUrl = resolveUrl(urlBase, controlPath) ?: continue
                    if (serviceType.startsWith("urn:schemas-upnp-org:service:AVTransport:", ignoreCase = true)) {
                        avTransportUrl = resolvedControlUrl
                    } else if (serviceType.startsWith("urn:schemas-upnp-org:service:RenderingControl:", ignoreCase = true)) {
                        renderingControlUrl = resolvedControlUrl
                    }
                }

                if (avTransportUrl != null) {
                    val friendlyName = firstDirectChildText(deviceEl, "friendlyName")?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: URI(locationUrl).host
                        ?: "DLNA Speaker"
                    val manufacturer = firstDirectChildText(deviceEl, "manufacturer")?.trim()?.takeIf { it.isNotEmpty() }
                    val modelName = firstDirectChildText(deviceEl, "modelName")?.trim()?.takeIf { it.isNotEmpty() }
                    val udn = firstDirectChildText(deviceEl, "UDN")?.trim()?.takeIf { it.isNotEmpty() }
                    val deviceTypeStr = firstDirectChildText(deviceEl, "deviceType")?.trim().orEmpty()
                    val hostAddress = runCatching { URI(locationUrl).host }.getOrNull()

                    return OutputDevice(
                        id = udn ?: locationUrl,
                        name = friendlyName,
                        manufacturer = manufacturer,
                        modelName = modelName,
                        protocol = CastProtocol.DLNA,
                        deviceType = inferDeviceType(friendlyName, modelName, deviceTypeStr),
                        avTransportControlUrl = avTransportUrl,
                        renderingControlUrl = renderingControlUrl,
                        locationUrl = locationUrl,
                        hostAddress = hostAddress,
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun buildLiveDidlLiteMetadata(
        metadata: LiveTrackMetadata,
        streamUrl: String,
        format: LiveStreamFormat = LiveStreamFormat.WAV,
    ): String {
        val protocolInfo = when (format) {
            LiveStreamFormat.WAV ->
                "http-get:*:audio/wav:DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            LiveStreamFormat.L16 ->
                "http-get:*:audio/L16;rate=44100;channels=2:DLNA.ORG_OP=00;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        }
        val artworkElement = if (!metadata.artworkUrl.isNullOrBlank()) {
            "<upnp:albumArtURI>${escapeXml(metadata.artworkUrl)}</upnp:albumArtURI>"
        } else {
            ""
        }
        return buildString {
            append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """)
            append("""xmlns:dc="http://purl.org/dc/elements/1.1/" """)
            append("""xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""")
            append("""<item id="0" parentID="-1" restricted="1">""")
            append("<dc:title>${escapeXml(metadata.title)}</dc:title>")
            append("<dc:creator>${escapeXml(metadata.artist)}</dc:creator>")
            append("<upnp:artist>${escapeXml(metadata.artist)}</upnp:artist>")
            append("<upnp:album>${escapeXml(metadata.album)}</upnp:album>")
            append(artworkElement)
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
            append("""<res protocolInfo="${escapeXml(protocolInfo)}">${escapeXml(streamUrl)}</res>""")
            append("</item>")
            append("</DIDL-Lite>")
        }
    }

    fun buildDidlLiteMetadata(
        title: String,
        artist: String,
        album: String,
        streamUrl: String,
        durationMs: Long? = null,
        mimeType: String = "audio/mpeg",
        artworkUrl: String? = null,
    ): String {
        val durationAttr = if (durationMs != null && durationMs > 0L) {
            " duration=\"${formatDlnaTime(durationMs)}\""
        } else {
            ""
        }
        val protocolInfo = "http-get:*:$mimeType:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        val artworkElement = if (!artworkUrl.isNullOrBlank()) {
            "<upnp:albumArtURI>${escapeXml(artworkUrl)}</upnp:albumArtURI>"
        } else {
            ""
        }
        return buildString {
            append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """)
            append("""xmlns:dc="http://purl.org/dc/elements/1.1/" """)
            append("""xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""")
            append("""<item id="0" parentID="-1" restricted="1">""")
            append("<dc:title>${escapeXml(title)}</dc:title>")
            append("<dc:creator>${escapeXml(artist)}</dc:creator>")
            append("<upnp:artist>${escapeXml(artist)}</upnp:artist>")
            append("<upnp:album>${escapeXml(album)}</upnp:album>")
            append(artworkElement)
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
            append("""<res protocolInfo="${escapeXml(protocolInfo)}"$durationAttr>${escapeXml(streamUrl)}</res>""")
            append("</item>")
            append("</DIDL-Lite>")
        }
    }

    fun buildSoapEnvelope(
        serviceType: String,
        action: String,
        arguments: List<Pair<String, String>>,
    ): String {
        return buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """)
            append("""s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""")
            append("<s:Body>")
            append("""<u:${escapeXml(action)} xmlns:u="${escapeXml(serviceType)}">""")
            for ((key, value) in arguments) {
                val safeKey = escapeXml(key)
                append("<$safeKey>${escapeXml(value)}</$safeKey>")
            }
            append("</u:${escapeXml(action)}>")
            append("</s:Body>")
            append("</s:Envelope>")
        }
    }

    fun parseSoapResponse(xml: String): Map<String, String> {
        if (xml.isBlank()) return emptyMap()
        return try {
            val doc = newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val root = doc.documentElement ?: return emptyMap()
            val result = LinkedHashMap<String, String>()
            collectLeafElements(root, result)
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun formatDlnaTime(ms: Long): String {
        val clampedSeconds = ms.coerceAtLeast(0L) / 1000L
        val hours = clampedSeconds / 3600L
        val minutes = (clampedSeconds % 3600L) / 60L
        val seconds = clampedSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun parseDlnaTimeToMs(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        val cleaned = timeStr.trim()
        if (cleaned.equals("NOT_IMPLEMENTED", ignoreCase = true) ||
            cleaned.equals("UNKNOWN", ignoreCase = true)
        ) {
            return 0L
        }
        val parts = cleaned.split(':')
        if (parts.size != 3) return 0L
        val hours = parts[0].toLongOrNull() ?: return 0L
        val minutes = parts[1].toLongOrNull() ?: return 0L
        val secPart = parts[2].split('.')
        val seconds = secPart[0].toLongOrNull() ?: return 0L
        val millis = if (secPart.size > 1) {
            val rawMs = secPart[1].take(3).padEnd(3, '0')
            rawMs.toLongOrNull() ?: 0L
        } else {
            0L
        }
        if (hours < 0 || minutes < 0 || seconds < 0) return 0L
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
    }

    fun isPrivateLanHost(url: String): Boolean {
        return try {
            val uri = URI(url)
            if (!uri.scheme.equals("http", ignoreCase = true)) return false
            val host = uri.host?.trim()?.lowercase(Locale.US) ?: return false
            if (host.isEmpty() || host == "0.0.0.0") return false
            if (host == "localhost" || host.endsWith(".local")) return true

            val addr = InetAddress.getByName(host)
            if (addr.isMulticastAddress || addr.isAnyLocalAddress) return false
            if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress) {
                return true
            }
            if (addr is Inet4Address) {
                val b = addr.address
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                // Also support CGNAT / Tailscale 100.64.0.0/10 only if strictly non-public? No, restrict strictly to RFC1918 + 169.254/16 + loopback.
                if (first == 10) return true
                if (first == 172 && second in 16..31) return true
                if (first == 192 && second == 168) return true
                if (first == 127) return true
                if (first == 169 && second == 254) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    internal fun escapeXml(value: String): String {
        val sb = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String? {
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(relativeOrAbsolute).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun collectDevices(deviceEl: Element, out: MutableList<Element>) {
        out.add(deviceEl)
        for (deviceList in directChildElements(deviceEl, "deviceList")) {
            for (childDevice in directChildElements(deviceList, "device")) {
                collectDevices(childDevice, out)
            }
        }
    }

    private fun collectLeafElements(el: Element, out: MutableMap<String, String>) {
        val children = el.childNodes
        var hasElementChild = false
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                hasElementChild = true
                collectLeafElements(node as Element, out)
            }
        }
        if (!hasElementChild) {
            val name = localNameOf(el)
            out[name] = el.textContent?.trim().orEmpty()
        }
    }

    private fun directChildElements(parent: Element, localName: String): List<Element> {
        val result = ArrayList<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                if (localNameOf(el).equals(localName, ignoreCase = true)) {
                    result.add(el)
                }
            }
        }
        return result
    }

    private fun firstDirectChildText(parent: Element, localName: String): String? {
        return directChildElements(parent, localName).firstOrNull()?.textContent
    }

    private fun localNameOf(el: Element): String {
        return el.localName ?: el.nodeName.substringAfter(':')
    }

    private fun inferDeviceType(
        friendlyName: String,
        modelName: String?,
        deviceType: String,
    ): OutputDeviceType {
        val combined = "$friendlyName ${modelName.orEmpty()} $deviceType".lowercase(Locale.US)
        return when {
            combined.contains("tv") || combined.contains("bravia") ||
                combined.contains("webos") || combined.contains("tizen") ||
                combined.contains("display") -> OutputDeviceType.TV
            combined.contains("receiver") || combined.contains("avr") ||
                combined.contains("denon") || combined.contains("yamaha") ||
                combined.contains("onkyo") || combined.contains("marantz") -> OutputDeviceType.RECEIVER
            else -> OutputDeviceType.SPEAKER
        }
    }

    private fun newDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }.newDocumentBuilder()
}

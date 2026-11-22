package com.listenquran.player.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaXmlParserTest {

    @Test
    fun `parseSsdpResponse extracts case-insensitive headers and ignores non-http`() {
        val raw = """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=1800
            Location: http://192.168.0.225:49494/description.xml
            ST: urn:schemas-upnp-org:device:MediaRenderer:1
            USN: uuid:1234-5678::urn:schemas-upnp-org:device:MediaRenderer:1
            Server: Linux/6.6 UPnP/1.0 gmrender-resurrect/0.3
        """.trimIndent()

        val parsed = DlnaXmlParser.parseSsdpResponse(raw)
        assertNotNull(parsed)
        assertEquals("http://192.168.0.225:49494/description.xml", parsed!!.location)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", parsed.st)
        assertTrue(parsed.server!!.contains("gmrender-resurrect"))

        assertNull(DlnaXmlParser.parseSsdpResponse("HTTP/1.1 200 OK\r\nLocation: ftp://192.168.0.1/x.xml\r\n"))
        assertNull(DlnaXmlParser.parseSsdpResponse(""))
    }

    @Test
    fun `parseDeviceDescription parses nested devices, resolves relative URLs, and infers device type`() {
        val xml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <specVersion><major>1</major><minor>0</minor></specVersion>
              <device>
                <deviceType>urn:schemas-upnp-org:device:Basic:1</deviceType>
                <friendlyName>Parent Bridge</friendlyName>
                <UDN>uuid:root-bridge</UDN>
                <deviceList>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                    <friendlyName>TrueNAS Speakers</friendlyName>
                    <manufacturer>gmediarender</manufacturer>
                    <modelName>gmrender-resurrect</modelName>
                    <UDN>uuid:truenas-dlna-1</UDN>
                    <serviceList>
                      <service>
                        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                        <controlURL>/upnp/control/rendertransport1</controlURL>
                      </service>
                      <service>
                        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                        <controlURL>upnp/control/rendercontrol1</controlURL>
                      </service>
                    </serviceList>
                  </device>
                </deviceList>
              </device>
            </root>
        """.trimIndent()

        val device = DlnaXmlParser.parseDeviceDescription(xml, "http://192.168.0.225:49494/description.xml")
        assertNotNull(device)
        assertEquals("uuid:truenas-dlna-1", device!!.id)
        assertEquals("TrueNAS Speakers", device.name)
        assertEquals("gmediarender", device.manufacturer)
        assertEquals("gmrender-resurrect", device.modelName)
        assertEquals(CastProtocol.DLNA, device.protocol)
        assertEquals(OutputDeviceType.SPEAKER, device.deviceType)
        assertEquals("http://192.168.0.225:49494/upnp/control/rendertransport1", device.avTransportControlUrl)
        assertEquals("http://192.168.0.225:49494/upnp/control/rendercontrol1", device.renderingControlUrl)
        assertEquals("192.168.0.225", device.hostAddress)
    }

    @Test
    fun `parseDeviceDescription recognizes TV and AVR models`() {
        val tvXml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <URLBase>http://192.168.0.50:8080/</URLBase>
              <device>
                <friendlyName>Living Room LG WebOS TV</friendlyName>
                <UDN>uuid:tv-1</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:2</serviceType>
                    <controlURL>/AVTransport/control</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val tv = DlnaXmlParser.parseDeviceDescription(tvXml, "http://192.168.0.50:8080/desc.xml")
        assertNotNull(tv)
        assertEquals(OutputDeviceType.TV, tv!!.deviceType)
        assertEquals("http://192.168.0.50:8080/AVTransport/control", tv.avTransportControlUrl)
        assertNull(tv.renderingControlUrl)
    }

    @Test
    fun `buildDidlLiteMetadata and buildSoapEnvelope escape special characters cleanly`() {
        val didl = DlnaXmlParser.buildDidlLiteMetadata(
            title = "36. Ya-Sin & <Reflection>",
            artist = "Mishari Rashid al-`Afasy",
            album = "The Holy Qur'an",
            streamUrl = "http://192.168.0.10:8999/stream/tok/36.mp3?a=1&b=2",
            durationMs = 1_056_000L,
        )
        assertTrue(didl.contains("36. Ya-Sin &amp; &lt;Reflection&gt;"))
        assertTrue(didl.contains("""duration="00:17:36""""))
        assertTrue(didl.contains("a=1&amp;b=2"))

        val soap = DlnaXmlParser.buildSoapEnvelope(
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "SetAVTransportURI",
            arguments = listOf(
                "InstanceID" to "0",
                "CurrentURI" to "http://192.168.0.10:8999/stream/tok/36.mp3",
                "CurrentURIMetaData" to didl,
            ),
        )
        assertTrue(soap.contains("&lt;DIDL-Lite"))
        assertTrue(soap.contains("""<u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">"""))
    }

    @Test
    fun `parseSoapResponse extracts transport and fault fields`() {
        val posXml = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <Track>1</Track>
                  <TrackDuration>00:17:36</TrackDuration>
                  <TrackURI>http://192.168.0.10:8999/stream/tok/36.mp3</TrackURI>
                  <RelTime>00:01:23</RelTime>
                </u:GetPositionInfoResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        val parsed = DlnaXmlParser.parseSoapResponse(posXml)
        assertEquals("00:17:36", parsed["TrackDuration"])
        assertEquals("00:01:23", parsed["RelTime"])

        val faultXml = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <s:Fault>
                  <faultcode>s:Client</faultcode>
                  <faultstring>UPnPError</faultstring>
                  <detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>701</errorCode><errorDescription>Transition not available</errorDescription></UPnPError></detail>
                </s:Fault>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        val fault = DlnaXmlParser.parseSoapResponse(faultXml)
        assertEquals("701", fault["errorCode"])
        assertEquals("Transition not available", fault["errorDescription"])
    }

    @Test
    fun `DLNA time formatting and parsing handle edge cases`() {
        assertEquals("00:00:00", DlnaXmlParser.formatDlnaTime(-500L))
        assertEquals("00:01:23", DlnaXmlParser.formatDlnaTime(83_400L))
        assertEquals("01:02:03", DlnaXmlParser.formatDlnaTime(3_723_000L))

        assertEquals(83_000L, DlnaXmlParser.parseDlnaTimeToMs("00:01:23"))
        assertEquals(83_450L, DlnaXmlParser.parseDlnaTimeToMs("0:01:23.45"))
        assertEquals(0L, DlnaXmlParser.parseDlnaTimeToMs("NOT_IMPLEMENTED"))
        assertEquals(0L, DlnaXmlParser.parseDlnaTimeToMs(null))
    }

    @Test
    fun `isPrivateLanHost rejects public URLs and https while permitting private LAN`() {
        assertTrue(DlnaXmlParser.isPrivateLanHost("http://192.168.0.225:49494/desc.xml"))
        assertTrue(DlnaXmlParser.isPrivateLanHost("http://10.0.0.5:8080/ctrl"))
        assertTrue(DlnaXmlParser.isPrivateLanHost("http://172.16.4.2:1400/ctrl"))
        assertTrue(DlnaXmlParser.isPrivateLanHost("http://127.0.0.1:9999/test"))

        assertFalse(DlnaXmlParser.isPrivateLanHost("https://192.168.0.225/desc.xml"))
        assertFalse(DlnaXmlParser.isPrivateLanHost("http://8.8.8.8/desc.xml"))
        assertFalse(DlnaXmlParser.isPrivateLanHost("http://1.1.1.1:80/ctrl"))
        assertFalse(DlnaXmlParser.isPrivateLanHost("not-a-url"))
    }
}

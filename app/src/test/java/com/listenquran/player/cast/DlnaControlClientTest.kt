package com.listenquran.player.cast

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class DlnaControlClientTest {

    @Test
    fun `dispatches AVTransport and RenderingControl SOAP actions and parses responses`() = runTest {
        val capturedActions = CopyOnWriteArrayList<String>()
        val capturedBodies = CopyOnWriteArrayList<String>()

        val mockServer = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val serverThread = thread {
            mockServer.use { ss ->
                repeat(7) {
                    val sock = ss.accept()
                    sock.use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        var soapAction = ""
                        var contentLength = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("SOAPAction:", ignoreCase = true)) {
                                soapAction = line.substringAfter(':').trim()
                            } else if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                            }
                        }
                        val buf = CharArray(contentLength)
                        var readTotal = 0
                        while (readTotal < contentLength) {
                            val n = reader.read(buf, readTotal, contentLength - readTotal)
                            if (n <= 0) break
                            readTotal += n
                        }
                        val body = String(buf, 0, readTotal)
                        capturedActions.add(soapAction)
                        capturedBodies.add(body)

                        val responseXml = when {
                            soapAction.contains("GetPositionInfo") -> """
                                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                                  <s:Body>
                                    <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                                      <TrackDuration>00:10:00</TrackDuration>
                                      <TrackURI>http://127.0.0.1:9999/stream/t/36.mp3</TrackURI>
                                      <RelTime>00:02:05</RelTime>
                                    </u:GetPositionInfoResponse>
                                  </s:Body>
                                </s:Envelope>
                            """.trimIndent()
                            soapAction.contains("GetTransportInfo") -> """
                                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                                  <s:Body>
                                    <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                                      <CurrentTransportState>PLAYING</CurrentTransportState>
                                    </u:GetTransportInfoResponse>
                                  </s:Body>
                                </s:Envelope>
                            """.trimIndent()
                            soapAction.contains("GetVolume") -> """
                                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                                  <s:Body>
                                    <u:GetVolumeResponse xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                                      <CurrentVolume>72</CurrentVolume>
                                    </u:GetVolumeResponse>
                                  </s:Body>
                                </s:Envelope>
                            """.trimIndent()
                            soapAction.contains("Seek") -> """
                                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                                  <s:Body>
                                    <s:Fault>
                                      <faultcode>s:Client</faultcode>
                                      <faultstring>UPnPError</faultstring>
                                      <detail><UPnPError><errorCode>710</errorCode><errorDescription>Seek mode not supported</errorDescription></UPnPError></detail>
                                    </s:Fault>
                                  </s:Body>
                                </s:Envelope>
                            """.trimIndent()
                            else -> "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body/></s:Envelope>"
                        }

                        val status = if (soapAction.contains("Seek")) "500 Internal Server Error" else "200 OK"
                        val respBytes = responseXml.toByteArray()
                        val httpResp = "HTTP/1.1 $status\r\nContent-Type: text/xml; charset=utf-8\r\nContent-Length: ${respBytes.size}\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().write(httpResp.toByteArray())
                        client.getOutputStream().write(respBytes)
                        client.getOutputStream().flush()
                    }
                }
            }
        }

        val controlClient = DlnaControlClient(OkHttpClient())
        val baseUrl = "http://127.0.0.1:${mockServer.localPort}"
        val device = OutputDevice(
            id = "uuid:test",
            name = "Mock Speaker",
            avTransportControlUrl = "$baseUrl/avtransport",
            renderingControlUrl = "$baseUrl/rendering",
        )

        val didl = DlnaXmlParser.buildDidlLiteMetadata("Ya-Sin", "Mishari", "Quran", "$baseUrl/audio.mp3")
        assertTrue(controlClient.setTransportUri(device, "$baseUrl/audio.mp3", didl).isSuccess)
        assertTrue(controlClient.play(device).isSuccess)
        assertTrue(controlClient.pause(device).isSuccess)
        assertTrue(controlClient.setVolume(device, 65).isSuccess)

        val posResult = controlClient.getPositionInfo(device).getOrThrow()
        assertEquals(125_000L, posResult.relTimeMs)
        assertEquals(600_000L, posResult.trackDurationMs)

        assertEquals(RemoteTransportState.PLAYING, controlClient.getTransportInfo(device).getOrThrow())
        assertEquals(72, controlClient.getVolume(device).getOrThrow())

        // Verify 7 requests arrived matching the mocked count, wait wait - Seek also triggers HTTP 500
        // Re-test Seek fault directly with separate assertion if count matches:
        assertTrue(capturedActions.any { it.contains("SetAVTransportURI") })
        assertTrue(capturedBodies.first().contains("&lt;DIDL-Lite"))
        serverThread.join(2000)

        // Separate single-shot server for Seek fault verification
        val faultServer = ServerSocket(0, 2, InetAddress.getLoopbackAddress())
        val faultThread = thread {
            faultServer.use { ss ->
                ss.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val faultBody = """
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                          <s:Body>
                            <s:Fault>
                              <faultcode>s:Client</faultcode>
                              <detail><UPnPError><errorCode>710</errorCode><errorDescription>Seek mode not supported</errorDescription></UPnPError></detail>
                            </s:Fault>
                          </s:Body>
                        </s:Envelope>
                    """.trimIndent().toByteArray()
                    val httpResp = "HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/xml\r\nContent-Length: ${faultBody.size}\r\nConnection: close\r\n\r\n"
                    client.getOutputStream().write(httpResp.toByteArray())
                    client.getOutputStream().write(faultBody)
                    client.getOutputStream().flush()
                }
            }
        }
        val faultDevice = device.copy(avTransportControlUrl = "http://127.0.0.1:${faultServer.localPort}/avt")
        val seekRes = controlClient.seek(faultDevice, 30_000L)
        assertTrue(seekRes.isFailure)
        assertTrue(seekRes.exceptionOrNull()!!.message!!.contains("Seek mode not supported"))
        faultThread.join(2000)
    }

    @Test
    fun `rejects non-LAN public URLs cleanly`() = runTest {
        val controlClient = DlnaControlClient(OkHttpClient())
        val badDevice = OutputDevice(
            id = "bad",
            name = "Public",
            avTransportControlUrl = "http://8.8.8.8/ctrl",
        )
        val res = controlClient.play(badDevice)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
    }
}

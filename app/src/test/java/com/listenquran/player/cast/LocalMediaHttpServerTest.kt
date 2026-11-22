package com.listenquran.player.cast

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class LocalMediaHttpServerTest {

    private lateinit var server: LocalMediaHttpServer
    private lateinit var tempFile: File
    private val sampleBytes = ByteArray(1024) { idx -> (idx % 251).toByte() }

    @Before
    fun setup() {
        server = LocalMediaHttpServer(OkHttpClient())
        tempFile = File.createTempFile("surah_test", ".mp3").apply {
            writeBytes(sampleBytes)
            deleteOnExit()
        }
        server.start(InetAddress.getLoopbackAddress())
    }

    @After
    fun tearDown() {
        server.stop()
        tempFile.delete()
    }

    @Test
    fun `serves local file GET, HEAD, and RFC 7233 206 Partial Content byte ranges`() {
        val url = server.registerMedia("surah_36", CastMediaSource.LocalFile(tempFile))
        val client = OkHttpClient()

        // HEAD check
        client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("audio/mpeg", resp.header("Content-Type"))
            assertEquals("1024", resp.header("Content-Length"))
            assertEquals("bytes", resp.header("Accept-Ranges"))
            assertEquals("Streaming", resp.header("transferMode.dlna.org"))
        }

        // Full GET
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertArrayEquals(sampleBytes, resp.body!!.bytes())
        }

        // 206 Partial Content
        val rangeReq = Request.Builder().url(url).header("Range", "bytes=100-199").get().build()
        client.newCall(rangeReq).execute().use { resp ->
            assertEquals(206, resp.code)
            assertEquals("bytes 100-199/1024", resp.header("Content-Range"))
            assertEquals("100", resp.header("Content-Length"))
            assertArrayEquals(sampleBytes.copyOfRange(100, 200), resp.body!!.bytes())
        }
    }

    @Test
    fun `rejects invalid session token with 403 and unknown media key with 404`() {
        val url = server.registerMedia("surah_1", CastMediaSource.LocalFile(tempFile))
        val badTokenUrl = url.replace(Regex("/stream/[^/]+/"), "/stream/wrong-token/")
        val missingKeyUrl = url.replace("surah_1.mp3", "surah_999.mp3")
        val client = OkHttpClient()

        client.newCall(Request.Builder().url(badTokenUrl).get().build()).execute().use { resp ->
            assertEquals(403, resp.code)
        }
        client.newCall(Request.Builder().url(missingKeyUrl).get().build()).execute().use { resp ->
            assertEquals(404, resp.code)
        }
    }

    @Test
    fun `proxies remote http stream including byte range header forwarding`() {
        val upstreamSocket = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        var capturedRangeHeader: String? = null
        val upstreamThread = thread {
            upstreamSocket.use { ss ->
                ss.accept().use { sock ->
                    val reader = sock.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Range:", ignoreCase = true)) {
                            capturedRangeHeader = line.substringAfter(':').trim()
                        }
                    }
                    val slice = "hello-quran-audio".toByteArray()
                    val header = buildString {
                        append("HTTP/1.1 206 Partial Content\r\n")
                        append("Content-Type: audio/mpeg\r\n")
                        append("Content-Range: bytes 0-${slice.size - 1}/${slice.size}\r\n")
                        append("Content-Length: ${slice.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    sock.getOutputStream().write(header.toByteArray())
                    sock.getOutputStream().write(slice)
                    sock.getOutputStream().flush()
                }
            }
        }

        val upstreamUrl = "http://127.0.0.1:${upstreamSocket.localPort}/audio/036.mp3"
        val proxyUrl = server.registerMedia("proxy_36", CastMediaSource.RemoteHttpProxy(upstreamUrl))
        val client = OkHttpClient()

        client.newCall(Request.Builder().url(proxyUrl).header("Range", "bytes=0-16").get().build()).execute().use { resp ->
            assertEquals(206, resp.code)
            assertEquals("hello-quran-audio", resp.body!!.string())
        }
        upstreamThread.join(2000)
        assertEquals("bytes=0-16", capturedRangeHeader)
        assertTrue(server.isRunning)
    }
}

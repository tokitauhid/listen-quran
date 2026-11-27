package com.listenquran.player.cast

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class LocalMediaHttpServerTest {

    private lateinit var hub: PcmStreamHub
    private lateinit var server: LocalMediaHttpServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 80L)
        server = LocalMediaHttpServer(hub)
        server.start(InetAddress.getLoopbackAddress())
        client = OkHttpClient.Builder()
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        hub.closeSession()
        server.stop()
    }

    @Test
    fun `serves HEAD and live WAV stream with 44-byte RIFF header and DLNA headers`() {
        val token = hub.openSession()
        server.setSessionToken(token)
        val url = server.buildLiveStreamUrl(token, LiveStreamFormat.WAV)

        val connected = AtomicBoolean(false)
        server.onRendererConnected = { connected.set(true) }

        client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("audio/wav", resp.header("Content-Type"))
            assertEquals("Streaming", resp.header("transferMode.dlna.org"))
            assertTrue(resp.header("contentFeatures.dlna.org")!!.contains("DLNA.ORG_OP=00"))
        }

        val samplePcm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        hub.handleBuffer(ByteBuffer.wrap(samplePcm))

        val closer = thread {
            Thread.sleep(80)
            hub.closeSession()
        }

        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            val stream = resp.body!!.byteStream()
            val header = ByteArray(44)
            var total = 0
            while (total < 44) {
                val n = stream.read(header, total, 44 - total)
                if (n < 0) break
                total += n
            }
            assertEquals(44, total)
            assertEquals("RIFF", String(header, 0, 4))
            assertEquals("WAVE", String(header, 8, 4))
            assertEquals("fmt ", String(header, 12, 4))
            assertEquals("data", String(header, 36, 4))

            val pcmOut = ByteArray(8)
            var pcmRead = 0
            while (pcmRead < 8) {
                val n = stream.read(pcmOut, pcmRead, 8 - pcmRead)
                if (n < 0) break
                pcmRead += n
            }
            assertEquals(8, pcmRead)
            assertArrayEquals(samplePcm, pcmOut)
        }
        closer.join(1000)
        assertTrue(connected.get())
    }

    @Test
    fun `swaps bytes to big-endian for L16 fallback and rejects invalid token with 403`() {
        val token = hub.openSession()
        server.setSessionToken(token)
        val badUrl = server.buildLiveStreamUrl("wrong_token", LiveStreamFormat.WAV)

        client.newCall(Request.Builder().url(badUrl).get().build()).execute().use { resp ->
            assertEquals(403, resp.code)
        }

        val l16Url = server.buildLiveStreamUrl(token, LiveStreamFormat.L16)
        hub.handleBuffer(ByteBuffer.wrap(byteArrayOf(0x11, 0x22, 0x33, 0x44)))
        val closer = thread {
            Thread.sleep(60)
            hub.closeSession()
        }
        client.newCall(Request.Builder().url(l16Url).get().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("audio/L16;rate=44100;channels=2", resp.header("Content-Type"))
            val firstFour = ByteArray(4)
            var read = 0
            val s = resp.body!!.byteStream()
            while (read < 4) {
                val n = s.read(firstFour, read, 4 - read)
                if (n < 0) break
                read += n
            }
            assertEquals(4, read)
            assertArrayEquals(byteArrayOf(0x22, 0x11, 0x44, 0x33), firstFour)
        }
        closer.join(1000)
    }

    @Test
    fun `second GET client supersedes first reader so streams never steal frames`() {
        val token = hub.openSession()
        server.setSessionToken(token)
        val l16Url = server.buildLiveStreamUrl(token, LiveStreamFormat.L16)

        val stream1Connected = java.util.concurrent.CountDownLatch(1)
        val stream1Ended = AtomicBoolean(false)
        val t1 = thread {
            runCatching {
                client.newCall(Request.Builder().url(l16Url).get().build()).execute().use { resp ->
                    stream1Connected.countDown()
                    val buf = ByteArray(1024)
                    val s = resp.body!!.byteStream()
                    while (s.read(buf) >= 0) {
                        // Consume until superseded by second client
                    }
                }
            }
            stream1Ended.set(true)
        }

        assertTrue(stream1Connected.await(2, TimeUnit.SECONDS))

        val payload = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x01)
        client.newCall(Request.Builder().url(l16Url).get().build()).execute().use { resp2 ->
            assertEquals(200, resp2.code)
            t1.join(1500)
            assertTrue("First stream should close immediately when superseded", stream1Ended.get())

            hub.handleBuffer(ByteBuffer.wrap(payload))
            val closer = thread {
                Thread.sleep(60)
                hub.closeSession()
            }

            val got = ByteArray(8)
            var total = 0
            val s2 = resp2.body!!.byteStream()
            while (total < 8) {
                val n = s2.read(got, total, 8 - total)
                if (n < 0) break
                total += n
            }
            assertEquals(8, total)
            assertArrayEquals(
                byteArrayOf(0x22, 0x11, 0x44, 0x33, 0x66, 0x55, 0x01, 0x77),
                got,
            )
            closer.join(1000)
        }
    }
}

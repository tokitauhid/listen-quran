package com.listenquran.player.cast

import androidx.media3.common.C
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class PcmStreamHubTest {

    @Test
    fun `bursts separated by 250ms read contiguously with zero silence spliced`() {
        val hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 750L)
        hub.flush(44100, 2, C.ENCODING_PCM_16BIT)

        // Ignored before session opens
        hub.handleBuffer(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)))

        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)
        val payload1 = ByteArray(1024) { 21 }
        val payload2 = ByteArray(1024) { 42 }
        hub.handleBuffer(ByteBuffer.wrap(payload1))

        val readBuf = ByteArray(1024)
        val n1 = hub.read(token, readBuf, clientId = clientId)
        assertEquals(payload1.size, n1)
        assertArrayEquals(payload1, readBuf)

        // Simulate ExoPlayer FLAG_DEEP_BUFFER sleep of 250ms before pushing second burst
        val writer = thread {
            Thread.sleep(250L)
            hub.handleBuffer(ByteBuffer.wrap(payload2))
        }

        val outBuf = ByteArray(2048)
        val n2 = hub.read(token, outBuf, clientId = clientId)
        writer.join(1000L)

        assertEquals(payload2.size, n2)
        assertArrayEquals(payload2, outBuf.copyOfRange(0, n2))
        assertEquals(0L, hub.silenceSlicesEmitted)
        assertEquals(0, hub.underrunSilenceTransitions)

        hub.closeSession()
        assertEquals(-1, hub.read(token, readBuf, clientId = clientId))
    }

    @Test
    fun `emits initial prebuffer and paced silence slices after idle threshold`() {
        val hub = PcmStreamHub(initialPrebufferMs = 40L, defaultIdleSilenceThresholdMs = 60L)
        hub.flush(44100, 2, C.ENCODING_PCM_16BIT)
        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)

        // First read delivers 40ms initial zero prebuffer
        val preBuf = ByteArray(16384) { 99 }
        val preBytes = hub.read(token, preBuf, clientId = clientId)
        assertTrue("Expected initial prebuffer > 0", preBytes > 0)
        assertTrue(preBuf.copyOfRange(0, preBytes).all { it == 0.toByte() })

        // Write loud real PCM and read it out
        val loud = ByteArray(1600)
        var i = 0
        while (i + 3 < loud.size) {
            loud[i] = 0x10; loud[i + 1] = 0x20
            loud[i + 2] = 0x10; loud[i + 3] = 0x20
            i += 4
        }
        hub.handleBuffer(ByteBuffer.wrap(loud))
        val audioOut = ByteArray(1600)
        assertEquals(1600, hub.read(token, audioOut, clientId = clientId))

        // Idle past 60ms -> transitions to 20ms silence slice with de-click ramp
        val silenceOut = ByteArray(8192) { 77 }
        val sBytes = hub.read(token, silenceOut, idleSilenceThresholdMs = 60L, clientId = clientId)
        assertEquals(PcmStreamHub.SILENCE_SLICE_BYTES, sBytes)
        assertEquals(1L, hub.silenceSlicesEmitted)
        assertEquals(1, hub.underrunSilenceTransitions)
        // Tail of 20ms slice past 8ms de-click ramp should be pure zeros
        val tailStart = PcmStreamHub.DECLICK_FRAMES * PcmStreamHub.FRAME_SIZE_BYTES + 8
        assertTrue(silenceOut.copyOfRange(tailStart, sBytes).all { it == 0.toByte() })

        // Superseding client ID immediately invalidates prior client reader
        val newClientId = hub.beginClientStream(token)
        assertTrue(newClientId > clientId)
        assertEquals(-1, hub.read(token, silenceOut, clientId = clientId))

        hub.closeSession()
    }

    @Test
    fun `ring buffer drops oldest frames on overflow instead of blocking`() {
        val hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 100L)
        hub.flush(44100, 2, C.ENCODING_PCM_16BIT)
        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)

        // Write 9 seconds of tagged frames (> 8s capacity)
        val chunk = ByteArray(PcmStreamHub.BYTES_PER_SECOND)
        for (sec in 1..9) {
            chunk.fill(sec.toByte())
            hub.writePcmBytes(chunk, 0, chunk.size)
        }

        val out = ByteArray(4)
        val n = hub.read(token, out, maxLen = 4, clientId = clientId)
        assertEquals(4, n)
        // Sec 1 was overwritten, oldest remaining should be sec 2
        assertEquals(2.toByte(), out[0])
        hub.closeSession()
    }

    @Test
    fun `exclusive activeSource prevents Main and Cue PCM interleaving`() {
        val hub = PcmStreamHub(initialPrebufferMs = 0L, defaultIdleSilenceThresholdMs = 200L)
        val mainSink = hub.sinkFor(PcmSource.MAIN)
        val cueSink = hub.sinkFor(PcmSource.CUE)
        mainSink.flush(44100, 2, C.ENCODING_PCM_16BIT)
        cueSink.flush(44100, 2, C.ENCODING_PCM_16BIT)

        val token = hub.openSession()
        val clientId = hub.beginClientStream(token)

        val mainBytes = ByteArray(16) { 11 }
        val cueBytes = ByteArray(16) { 22 }

        // Active source defaults to MAIN -> Cue bytes must be ignored
        mainSink.handleBuffer(ByteBuffer.wrap(mainBytes))
        cueSink.handleBuffer(ByteBuffer.wrap(cueBytes))

        val out1 = ByteArray(16)
        assertEquals(16, hub.read(token, out1, clientId = clientId))
        assertArrayEquals(mainBytes, out1)

        // Switching to CUE -> Main bytes must be ignored
        hub.activeSource = PcmSource.CUE
        mainSink.handleBuffer(ByteBuffer.wrap(mainBytes))
        cueSink.handleBuffer(ByteBuffer.wrap(cueBytes))

        val out2 = ByteArray(16)
        assertEquals(16, hub.read(token, out2, clientId = clientId))
        assertArrayEquals(cueBytes, out2)

        hub.closeSession()
    }
}

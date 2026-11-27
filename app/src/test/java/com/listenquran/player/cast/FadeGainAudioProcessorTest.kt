package com.listenquran.player.cast

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FadeGainAudioProcessorTest {

    @Test
    fun `fade out ramps gain down smoothly to zero across frames`() {
        val processor = FadeGainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(1000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        // 100ms at 1000 Hz = 100 frames
        processor.fadeTo(0f, 100L)

        val frames = 100
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            input.putShort(10000)
            input.putShort(10000)
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)

        val firstLeft = out.getShort(0).toInt()
        val midLeft = out.getShort(50 * 4).toInt()
        val lastLeft = out.getShort(99 * 4).toInt()

        assertTrue("First sample should be near 9900, was $firstLeft", firstLeft in 9800..9990)
        assertTrue("Mid sample should be near 5000, was $midLeft", midLeft in 4800..5100)
        assertEquals("Final sample should reach 0", 0, lastLeft)
        assertEquals(0f, processor.currentGain, 0.001f)
    }

    @Test
    fun `setGainImmediate mutes and restores unity gain cleanly`() {
        val processor = FadeGainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        processor.setGainImmediate(0f)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(12000).putShort(-12000).putShort(5000).putShort(-5000)
        input.flip()

        processor.queueInput(input)
        val mutedOut = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, mutedOut.getShort(0).toInt())
        assertEquals(0, mutedOut.getShort(2).toInt())
        assertEquals(0, mutedOut.getShort(4).toInt())
        assertEquals(0, mutedOut.getShort(6).toInt())

        processor.setGainImmediate(1f)
        val input2 = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
        input2.putShort(12345).putShort(-23456)
        input2.flip()
        processor.queueInput(input2)
        val fullOut = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(12345, fullOut.getShort(0).toInt())
        assertEquals(-23456, fullOut.getShort(2).toInt())
    }
}

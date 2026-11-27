package com.listenquran.player.cast

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class PcmSource { MAIN, CUE }

/**
 * Shared bounded PCM ring buffer bridging Media3's [TeeAudioProcessor] output
 * to the live DLNA HTTP streaming server.
 *
 * Expects post-Sonic, post-ChannelMixing 44.1 kHz stereo PCM-16 audio.
 * When no cast session is active, [handleBuffer] returns immediately with zero work.
 *
 * Tolerates `FLAG_DEEP_BUFFER` AudioTrack burst intervals without splicing silence into
 * active playback, provides a bounded initial pre-roll cushion, enforces single-reader
 * exclusivity per HTTP stream, paces idle silence strictly at 1.0x wall-clock speed,
 * and applies 8ms linear de-click ramps across any silence boundary.
 */
@Singleton
@OptIn(UnstableApi::class)
class PcmStreamHub internal constructor(
    private val initialPrebufferMs: Long = DEFAULT_INITIAL_PREBUFFER_MS,
    private val defaultIdleSilenceThresholdMs: Long = DEFAULT_IDLE_SILENCE_THRESHOLD_MS,
    private val maxBurstAheadMs: Long = DEFAULT_MAX_BURST_AHEAD_MS,
    private val paceRealAudio: Boolean = false,
) : TeeAudioProcessor.AudioBufferSink {

    @Inject
    constructor() : this(
        initialPrebufferMs = DEFAULT_INITIAL_PREBUFFER_MS,
        defaultIdleSilenceThresholdMs = DEFAULT_IDLE_SILENCE_THRESHOLD_MS,
        maxBurstAheadMs = DEFAULT_MAX_BURST_AHEAD_MS,
        paceRealAudio = false,
    )

    companion object {
        const val SAMPLE_RATE_HZ = 44100
        const val CHANNEL_COUNT = 2
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        const val FRAME_SIZE_BYTES = CHANNEL_COUNT * BYTES_PER_SAMPLE
        const val BYTES_PER_SECOND = SAMPLE_RATE_HZ * FRAME_SIZE_BYTES
        const val DEFAULT_CAPACITY_BYTES = BYTES_PER_SECOND * 8 // 8 seconds ring buffer
        const val DEFAULT_INITIAL_PREBUFFER_MS = 250L
        const val DEFAULT_IDLE_SILENCE_THRESHOLD_MS = 1600L
        const val DEFAULT_MAX_BURST_AHEAD_MS = 350L
        const val MAX_PACED_CHUNK_BYTES = 7056 // ~40 ms at 44.1 kHz stereo PCM-16
        const val SILENCE_SLICE_MS = 20L
        const val SILENCE_SLICE_BYTES = ((SAMPLE_RATE_HZ / 50) * FRAME_SIZE_BYTES) // 3528 bytes (20 ms)
        const val DECLICK_FRAMES = 352 // ~8 ms at 44.1 kHz
    }

    private data class SourceSegment(
        val source: PcmSource,
        var bytes: Int,
    )

    private val lock = Object()
    private val capacityBytes = DEFAULT_CAPACITY_BYTES
    private val ringBuffer = ByteArray(capacityBytes)
    private var readPos = 0
    private var writePos = 0
    private var availableBytes = 0
    private val segments = ArrayDeque<SourceSegment>()
    private var mainBufferedBytes = 0
    private var cueBufferedBytes = 0

    @Volatile
    private var activeToken: String? = null

    @Volatile
    var activeSource: PcmSource = PcmSource.MAIN

    private var nextClientId: Long = 0L
    private var activeClientId: Long = 0L

    @Volatile
    var paused: Boolean = false
        set(value) {
            synchronized(lock) {
                val wasPaused = field
                field = value
                if (!value) {
                    lastPcmWriteNs = System.nanoTime()
                    if (wasPaused) {
                        resetPacerLocked()
                    }
                }
                lock.notifyAll()
            }
        }

    @Volatile
    private var incomingSampleRateHz: Int = SAMPLE_RATE_HZ

    @Volatile
    private var incomingChannelCount: Int = CHANNEL_COUNT

    @Volatile
    private var incomingEncoding: Int = C.ENCODING_PCM_16BIT

    @Volatile
    private var bytesWrittenToStream: Long = 0L

    @Volatile
    var silenceSlicesEmitted: Long = 0L
        private set

    @Volatile
    var underrunSilenceTransitions: Int = 0
        private set

    private var lastPcmWriteNs: Long = System.nanoTime()
    private var prebufferSilenceRemainingBytes: Int = 0
    private var hasEmittedRealAudio: Boolean = false
    private var wasEmittingSilence: Boolean = false
    private var declickFadeInRemainingFrames: Int = 0
    private var lastLeftSample: Int = 0
    private var lastRightSample: Int = 0
    private var nextSilenceDueNs: Long = 0L
    private var pacingAnchorNs: Long = 0L
    private var pacedBytesEmitted: Long = 0L

    val streamMsWritten: Long
        get() = (bytesWrittenToStream * 1000L) / BYTES_PER_SECOND

    val isSessionOpen: Boolean
        get() = activeToken != null

    fun isTokenValid(token: String?): Boolean {
        val current = activeToken ?: return false
        return token != null && token == current
    }

    fun bufferedBytes(source: PcmSource): Int = synchronized(lock) {
        if (source == PcmSource.MAIN) mainBufferedBytes else cueBufferedBytes
    }

    fun openSession(): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        synchronized(lock) {
            activeToken = token
            activeSource = PcmSource.MAIN
            paused = false
            readPos = 0
            writePos = 0
            availableBytes = 0
            resetLedgerLocked()
            bytesWrittenToStream = 0L
            silenceSlicesEmitted = 0L
            underrunSilenceTransitions = 0
            lastPcmWriteNs = System.nanoTime()
            prebufferSilenceRemainingBytes = 0
            resetDeclickLocked()
            resetPacerLocked()
            lock.notifyAll()
        }
        return token
    }

    fun closeSession() {
        synchronized(lock) {
            activeToken = null
            activeSource = PcmSource.MAIN
            activeClientId = 0L
            paused = false
            readPos = 0
            writePos = 0
            availableBytes = 0
            resetLedgerLocked()
            prebufferSilenceRemainingBytes = 0
            resetDeclickLocked()
            resetPacerLocked()
            lock.notifyAll()
        }
    }

    fun clearBuffer() {
        synchronized(lock) {
            readPos = 0
            writePos = 0
            availableBytes = 0
            resetLedgerLocked()
            resetPacerLocked()
            lastPcmWriteNs = System.nanoTime()
            lock.notifyAll()
        }
    }

    fun clearSourceBuffer(source: PcmSource) {
        synchronized(lock) {
            if (availableBytes == 0 || segments.all { it.source == source }) {
                readPos = 0
                writePos = 0
                availableBytes = 0
                resetLedgerLocked()
                resetPacerLocked()
                lastPcmWriteNs = System.nanoTime()
                lock.notifyAll()
            }
        }
    }

    suspend fun writeSilenceMs(durationMs: Long) {
        val clampedMs = durationMs.coerceIn(0L, 10_000L)
        if (clampedMs <= 0L) return
        var remainingBytes = (((clampedMs * SAMPLE_RATE_HZ) / 1000L).toInt()) * FRAME_SIZE_BYTES
        val maxChunkBytes = (BYTES_PER_SECOND / 4 / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
        while (remainingBytes > 0 && isSessionOpen) {
            if (paused) {
                kotlinx.coroutines.delay(30L)
                continue
            }
            var wrote = 0
            synchronized(lock) {
                if (activeToken == null) return
                val ringRoom = ((capacityBytes - availableBytes) / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
                val toWrite = minOf(remainingBytes, maxChunkBytes, ringRoom)
                if (toWrite > 0) {
                    val firstFill = minOf(toWrite, capacityBytes - writePos)
                    ringBuffer.fill(0, writePos, writePos + firstFill)
                    val secondFill = toWrite - firstFill
                    if (secondFill > 0) {
                        ringBuffer.fill(0, 0, secondFill)
                    }
                    writePos = (writePos + toWrite) % capacityBytes
                    availableBytes += toWrite
                    recordAppendLocked(activeSource, toWrite)
                    lastPcmWriteNs = System.nanoTime()
                    lock.notifyAll()
                    wrote = toWrite
                }
            }
            if (wrote > 0) {
                remainingBytes -= wrote
            } else {
                kotlinx.coroutines.delay(30L)
            }
        }
    }

    private fun recordAppendLocked(source: PcmSource, bytes: Int) {
        if (bytes <= 0) return
        val tail = segments.lastOrNull()
        if (tail != null && tail.source == source) {
            tail.bytes += bytes
        } else {
            segments.addLast(SourceSegment(source, bytes))
        }
        if (source == PcmSource.MAIN) {
            mainBufferedBytes += bytes
        } else {
            cueBufferedBytes += bytes
        }
    }

    private fun consumeSegmentsLocked(bytesToConsume: Int) {
        var remaining = bytesToConsume
        while (remaining > 0 && segments.isNotEmpty()) {
            val head = segments.first()
            val take = minOf(remaining, head.bytes)
            head.bytes -= take
            if (head.source == PcmSource.MAIN) {
                mainBufferedBytes = (mainBufferedBytes - take).coerceAtLeast(0)
            } else {
                cueBufferedBytes = (cueBufferedBytes - take).coerceAtLeast(0)
            }
            remaining -= take
            if (head.bytes == 0) {
                segments.removeFirst()
            }
        }
    }

    private fun resetLedgerLocked() {
        segments.clear()
        mainBufferedBytes = 0
        cueBufferedBytes = 0
    }

    /**
     * Registers a new exclusive HTTP stream reader for [token], superseding any
     * prior socket still reading on the same token and priming a bounded initial
     * pre-roll cushion minus any PCM already buffered.
     *
     * Returns `-1L` if [token] is no longer active.
     */
    fun beginClientStream(token: String): Long {
        synchronized(lock) {
            if (!isTokenValid(token)) return -1L
            val clientId = ++nextClientId
            activeClientId = clientId
            bytesWrittenToStream = 0L
            silenceSlicesEmitted = 0L
            underrunSilenceTransitions = 0
            lastPcmWriteNs = System.nanoTime()
            resetDeclickLocked()
            resetPacerLocked()

            val prebufferMs = initialPrebufferMs.coerceAtLeast(0L)
            val targetPrebufferBytes =
                ((((prebufferMs * SAMPLE_RATE_HZ) / 1000L).toInt()) * FRAME_SIZE_BYTES)
                    .coerceAtMost(capacityBytes / 2)
            val deficit = (targetPrebufferBytes - availableBytes).coerceAtLeast(0)
            prebufferSilenceRemainingBytes = (deficit / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES

            lock.notifyAll()
            return clientId
        }
    }

    fun onHttpStreamStarted() {
        synchronized(lock) {
            bytesWrittenToStream = 0L
        }
    }

    private fun resetDeclickLocked() {
        hasEmittedRealAudio = false
        wasEmittingSilence = false
        declickFadeInRemainingFrames = 0
        lastLeftSample = 0
        lastRightSample = 0
        nextSilenceDueNs = 0L
    }

    private fun resetPacerLocked() {
        pacingAnchorNs = 0L
        pacedBytesEmitted = 0L
    }

    /**
     * Returns an exclusive [TeeAudioProcessor.AudioBufferSink] for [source] ([PcmSource.MAIN]
     * or [PcmSource.CUE]). Only buffers from [activeSource] enter the ring buffer, preventing
     * simultaneous main + cue interleaving.
     */
    fun sinkFor(source: PcmSource): TeeAudioProcessor.AudioBufferSink =
        object : TeeAudioProcessor.AudioBufferSink {
            @Volatile
            private var sourceEncoding: Int = C.ENCODING_PCM_16BIT

            override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
                sourceEncoding = encoding
                if (activeSource == source) {
                    incomingSampleRateHz = sampleRateHz
                    incomingChannelCount = channelCount
                    incomingEncoding = encoding
                }
            }

            override fun handleBuffer(buffer: ByteBuffer) {
                if (activeSource != source) return
                if (activeToken == null) return
                val remaining = buffer.remaining()
                if (remaining <= 0) return
                if (sourceEncoding != C.ENCODING_PCM_16BIT) return
                writeFromByteBuffer(buffer, source)
            }
        }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        incomingSampleRateHz = sampleRateHz
        incomingChannelCount = channelCount
        incomingEncoding = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (activeSource != PcmSource.MAIN) return
        if (activeToken == null) return
        val remaining = buffer.remaining()
        if (remaining <= 0) return

        // Only accept PCM-16; drop partial trailing frame bytes to stay frame-aligned.
        if (incomingEncoding != C.ENCODING_PCM_16BIT) return
        writeFromByteBuffer(buffer, PcmSource.MAIN)
    }

    /**
     * Bounded, non-dropping PCM write for [CastPcmAudioSink].
     *
     * Returns `-1` when no cast session is open or [source] is not currently [activeSource].
     * Returns `0` when [source] already has at least [maxSourceBufferedBytes] queued or the ring
     * is full (applying backpressure without consuming [buffer]).
     * Otherwise consumes and copies up to `maxWritable` frame-aligned bytes, advancing [buffer] position.
     */
    fun writeFromByteBufferWithLimit(
        source: PcmSource,
        buffer: ByteBuffer,
        maxSourceBufferedBytes: Int,
    ): Int {
        val remaining = buffer.remaining()
        val alignedRemaining = (remaining / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
        if (alignedRemaining <= 0) return 0

        synchronized(lock) {
            if (activeToken == null || activeSource != source) return -1
            val currentForSource = if (source == PcmSource.MAIN) mainBufferedBytes else cueBufferedBytes
            val sourceRoom = (maxSourceBufferedBytes - currentForSource).coerceAtLeast(0)
            val ringRoom = (capacityBytes - availableBytes).coerceAtLeast(0)
            val maxWritable = (minOf(sourceRoom, ringRoom) / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
            if (maxWritable <= 0) return 0

            val toWrite = minOf(alignedRemaining, maxWritable)
            val firstCopy = minOf(toWrite, capacityBytes - writePos)
            buffer.get(ringBuffer, writePos, firstCopy)
            val secondCopy = toWrite - firstCopy
            if (secondCopy > 0) {
                buffer.get(ringBuffer, 0, secondCopy)
            }
            writePos = (writePos + toWrite) % capacityBytes
            availableBytes += toWrite
            recordAppendLocked(source, toWrite)
            lastPcmWriteNs = System.nanoTime()
            lock.notifyAll()
            return toWrite
        }
    }

    private fun writeFromByteBuffer(buffer: ByteBuffer, source: PcmSource = PcmSource.MAIN) {
        val remaining = buffer.remaining()
        val alignedLen = (remaining / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
        if (alignedLen <= 0) return

        val dup = buffer.duplicate()
        synchronized(lock) {
            if (activeToken == null) return

            var toWrite = alignedLen
            // If incoming chunk alone exceeds ring capacity, skip ahead to keep newest tail.
            if (toWrite > capacityBytes) {
                val skip = ((toWrite - capacityBytes) / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
                dup.position(dup.position() + skip)
                toWrite -= skip
            }

            dropOnOverflowLocked(toWrite)

            val firstCopy = minOf(toWrite, capacityBytes - writePos)
            dup.get(ringBuffer, writePos, firstCopy)
            val secondCopy = toWrite - firstCopy
            if (secondCopy > 0) {
                dup.get(ringBuffer, 0, secondCopy)
            }
            writePos = (writePos + toWrite) % capacityBytes
            availableBytes += toWrite
            recordAppendLocked(source, toWrite)
            lastPcmWriteNs = System.nanoTime()
            lock.notifyAll()
        }
    }

    internal fun writePcmBytes(src: ByteArray, offset: Int, length: Int, source: PcmSource = PcmSource.MAIN) {
        if (activeToken == null || length <= 0) return
        val alignedLen = (length / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
        if (alignedLen <= 0) return

        synchronized(lock) {
            if (activeToken == null) return

            var srcOffset = offset
            var toWrite = alignedLen
            if (toWrite > capacityBytes) {
                val skip = ((toWrite - capacityBytes) / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
                srcOffset += skip
                toWrite -= skip
            }

            dropOnOverflowLocked(toWrite)

            val firstCopy = minOf(toWrite, capacityBytes - writePos)
            System.arraycopy(src, srcOffset, ringBuffer, writePos, firstCopy)
            val secondCopy = toWrite - firstCopy
            if (secondCopy > 0) {
                System.arraycopy(src, srcOffset + firstCopy, ringBuffer, 0, secondCopy)
            }
            writePos = (writePos + toWrite) % capacityBytes
            availableBytes += toWrite
            recordAppendLocked(source, toWrite)
            lastPcmWriteNs = System.nanoTime()
            lock.notifyAll()
        }
    }

    private fun dropOnOverflowLocked(incomingBytes: Int) {
        val overflow = (availableBytes + incomingBytes) - capacityBytes
        if (overflow > 0) {
            val drop = ((overflow + FRAME_SIZE_BYTES - 1) / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
            val actualDrop = drop.coerceAtMost(availableBytes)
            readPos = (readPos + actualDrop) % capacityBytes
            availableBytes -= actualDrop
            consumeSegmentsLocked(actualDrop)
        }
    }

    /**
     * Reads up to [maxLen] frame-aligned PCM bytes into [dst] for the HTTP stream writer.
     *
     * While real audio is arriving within [idleSilenceThresholdMs], waits for contiguous PCM
     * frames rather than splicing zero-PCM gaps. Only when genuinely idle past
     * [idleSilenceThresholdMs], emits 20ms silence slices paced at 1.0x wall-clock time.
     *
     * Returns `-1` when the session is closed or superseded by a newer [beginClientStream] call.
     */
    fun read(
        token: String,
        dst: ByteArray,
        offset: Int = 0,
        maxLen: Int = dst.size - offset,
        idleSilenceThresholdMs: Long = defaultIdleSilenceThresholdMs,
        clientId: Long = activeClientId,
    ): Int {
        val alignedMax = (maxLen / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
        if (alignedMax <= 0) return 0

        synchronized(lock) {
            if (!isReaderCurrentLocked(token, clientId)) return -1

            // Initial pre-roll silence cushion so the DLNA renderer starts smoothly even across
            // initial ExoPlayer AudioTrack burst jitter.
            if (!paused && prebufferSilenceRemainingBytes > 0) {
                val preBytes = minOf(alignedMax, prebufferSilenceRemainingBytes)
                dst.fill(0, offset, offset + preBytes)
                prebufferSilenceRemainingBytes -= preBytes
                bytesWrittenToStream += preBytes
                return preBytes
            }

            val thresholdNs = idleSilenceThresholdMs.coerceAtLeast(1L) * 1_000_000L
            val burstBytes =
                (((maxBurstAheadMs.coerceAtLeast(20L) * SAMPLE_RATE_HZ) / 1000L).toLong() * FRAME_SIZE_BYTES)
            var pacedBudgetBytes = Int.MAX_VALUE
            var emitIdleSilence = false

            while (isReaderCurrentLocked(token, clientId)) {
                if (paused) {
                    // Wait while paused without inflating stream time with silence.
                    lock.wait(250L)
                    continue
                }

                val nowNs = System.nanoTime()
                if (availableBytes < FRAME_SIZE_BYTES) {
                    val elapsedIdleNs = (nowNs - lastPcmWriteNs).coerceAtLeast(0L)
                    if (!wasEmittingSilence && elapsedIdleNs < thresholdNs) {
                        val waitMs = (((thresholdNs - elapsedIdleNs) + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                        lock.wait(waitMs)
                    } else {
                        if (!wasEmittingSilence) {
                            wasEmittingSilence = true
                            nextSilenceDueNs = nowNs + (SILENCE_SLICE_MS * 1_000_000L)
                            if (hasEmittedRealAudio) {
                                underrunSilenceTransitions++
                            }
                            resetPacerLocked()
                            emitIdleSilence = true
                            break
                        }
                        if (nowNs < nextSilenceDueNs) {
                            val waitMs = (((nextSilenceDueNs - nowNs) + 999_999L) / 1_000_000L).coerceAtLeast(1L)
                            lock.wait(waitMs)
                        } else {
                            val sliceNs = SILENCE_SLICE_MS * 1_000_000L
                            nextSilenceDueNs = maxOf(nextSilenceDueNs + sliceNs, nowNs)
                            resetPacerLocked()
                            emitIdleSilence = true
                            break
                        }
                    }
                    continue
                }

                if (!paceRealAudio) {
                    pacedBudgetBytes = Int.MAX_VALUE
                    break
                }

                if (pacingAnchorNs == 0L) {
                    pacingAnchorNs = nowNs
                    pacedBytesEmitted = 0L
                }
                val elapsedNs = (nowNs - pacingAnchorNs).coerceAtLeast(0L)
                var allowedTotalBytes = ((elapsedNs * BYTES_PER_SECOND) / 1_000_000_000L) + burstBytes
                // Guard against multi-second burst credit after a network or decoder stall.
                if (allowedTotalBytes - pacedBytesEmitted > burstBytes + BYTES_PER_SECOND) {
                    pacingAnchorNs = nowNs
                    pacedBytesEmitted = 0L
                    allowedTotalBytes = burstBytes
                }
                val rawBudget = (allowedTotalBytes - pacedBytesEmitted).coerceAtLeast(0L)
                val alignedBudget = (rawBudget / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
                if (alignedBudget >= FRAME_SIZE_BYTES) {
                    pacedBudgetBytes = alignedBudget.coerceAtMost(MAX_PACED_CHUNK_BYTES.toLong()).toInt()
                    break
                }

                val targetBytes = minOf(availableBytes, SILENCE_SLICE_BYTES).coerceAtLeast(FRAME_SIZE_BYTES)
                val deficitBytes = (targetBytes - rawBudget).coerceAtLeast(FRAME_SIZE_BYTES.toLong())
                val waitNs = (deficitBytes * 1_000_000_000L) / BYTES_PER_SECOND
                val waitMs = ((waitNs + 999_999L) / 1_000_000L).coerceIn(1L, 25L)
                lock.wait(waitMs)
            }

            if (!isReaderCurrentLocked(token, clientId)) return -1

            if (!emitIdleSilence && !paused && availableBytes >= FRAME_SIZE_BYTES) {
                val availAligned = (availableBytes / FRAME_SIZE_BYTES) * FRAME_SIZE_BYTES
                val toRead = minOf(alignedMax, availAligned, pacedBudgetBytes)
                val firstCopy = minOf(toRead, capacityBytes - readPos)
                System.arraycopy(ringBuffer, readPos, dst, offset, firstCopy)
                val secondCopy = toRead - firstCopy
                if (secondCopy > 0) {
                    System.arraycopy(ringBuffer, 0, dst, offset + firstCopy, secondCopy)
                }
                readPos = (readPos + toRead) % capacityBytes
                availableBytes -= toRead
                consumeSegmentsLocked(toRead)
                if (paceRealAudio) {
                    pacedBytesEmitted += toRead
                }

                if (wasEmittingSilence && hasEmittedRealAudio) {
                    declickFadeInRemainingFrames = DECLICK_FRAMES
                }
                wasEmittingSilence = false

                if (declickFadeInRemainingFrames > 0) {
                    applyDeclickFadeIn(dst, offset, toRead)
                }

                val lastFrameIdx = offset + toRead - FRAME_SIZE_BYTES
                lastLeftSample = readSampleLe(dst, lastFrameIdx)
                lastRightSample = readSampleLe(dst, lastFrameIdx + 2)
                hasEmittedRealAudio = true
                bytesWrittenToStream += toRead
                return toRead
            }

            if (emitIdleSilence && !paused) {
                val sliceBytes = minOf(alignedMax, SILENCE_SLICE_BYTES)
                dst.fill(0, offset, offset + sliceBytes)
                if (hasEmittedRealAudio && (lastLeftSample != 0 || lastRightSample != 0)) {
                    applyDeclickFadeOutFromLastSample(dst, offset, sliceBytes)
                    lastLeftSample = 0
                    lastRightSample = 0
                }
                bytesWrittenToStream += sliceBytes
                silenceSlicesEmitted++
                return sliceBytes
            }

            return 0
        }
    }

    private fun isReaderCurrentLocked(token: String, clientId: Long): Boolean {
        return isTokenValid(token) && clientId == activeClientId
    }

    private fun applyDeclickFadeIn(buf: ByteArray, offset: Int, lenBytes: Int) {
        val frames = lenBytes / FRAME_SIZE_BYTES
        val rampFrames = minOf(frames, declickFadeInRemainingFrames)
        for (f in 0 until rampFrames) {
            val completed = (DECLICK_FRAMES - declickFadeInRemainingFrames) + 1
            val gain = completed.toFloat() / DECLICK_FRAMES.toFloat()
            val idx = offset + (f * FRAME_SIZE_BYTES)
            val l = (readSampleLe(buf, idx) * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val r = (readSampleLe(buf, idx + 2) * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            writeSampleLe(buf, idx, l)
            writeSampleLe(buf, idx + 2, r)
            declickFadeInRemainingFrames--
        }
    }

    private fun applyDeclickFadeOutFromLastSample(buf: ByteArray, offset: Int, sliceBytes: Int) {
        val rampFrames = minOf(DECLICK_FRAMES, sliceBytes / FRAME_SIZE_BYTES)
        if (rampFrames <= 0) return
        val denom = (rampFrames + 1).toFloat()
        for (f in 0 until rampFrames) {
            val gain = (rampFrames - f).toFloat() / denom
            val idx = offset + (f * FRAME_SIZE_BYTES)
            val l = (lastLeftSample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val r = (lastRightSample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            writeSampleLe(buf, idx, l)
            writeSampleLe(buf, idx + 2, r)
        }
    }

    private fun readSampleLe(buf: ByteArray, idx: Int): Int {
        return (buf[idx + 1].toInt() shl 8) or (buf[idx].toInt() and 0xFF)
    }

    private fun writeSampleLe(buf: ByteArray, idx: Int, sample: Int) {
        buf[idx] = (sample and 0xFF).toByte()
        buf[idx + 1] = ((sample ushr 8) and 0xFF).toByte()
    }
}

package com.listenquran.player.cast

import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight local HTTP server streaming live 44.1 kHz stereo PCM audio from [PcmStreamHub]
 * to DLNA/UPnP renderers on the same LAN.
 *
 * Supports WAV (`audio/wav`) and big-endian L16 (`audio/L16;rate=44100;channels=2`) fallback,
 * streaming DLNA headers, and `HEAD` preflight requests.
 */
@Singleton
class LocalMediaHttpServer @Inject constructor(
    private val hub: PcmStreamHub,
) {
    private val serverLock = Any()
    private val activeToken = AtomicReference<String?>(null)
    private val activeStreamSocket = AtomicReference<Socket?>(null)
    private val running = AtomicBoolean(false)
    private val rendererConnectedNotified = AtomicBoolean(false)

    @Volatile
    var onRendererConnected: (() -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ExecutorService? = null
    private var boundHostAddress: String = "127.0.0.1"
    private var boundPort: Int = 0

    val isRunning: Boolean
        get() = running.get()

    val baseUrl: String?
        get() = if (running.get() && boundPort > 0) "http://$boundHostAddress:$boundPort" else null

    fun start(bindAddress: InetAddress, port: Int = 0, forceRestart: Boolean = false): String {
        synchronized(serverLock) {
            val host = bindAddress.hostAddress ?: "127.0.0.1"
            if (running.get() && !forceRestart && boundHostAddress == host && serverSocket?.isClosed == false) {
                return "http://$boundHostAddress:$boundPort"
            }
            stopLocked()

            val socket = ServerSocket(port, 16, bindAddress).apply {
                reuseAddress = true
                soTimeout = 500
            }
            val pool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "ListenQuran-LiveStreamWorker").apply { isDaemon = true }
            }

            serverSocket = socket
            workerPool = pool
            boundHostAddress = host
            boundPort = socket.localPort
            rendererConnectedNotified.set(false)
            running.set(true)

            acceptThread = Thread({
                while (running.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        pool.execute { handleClient(client) }
                    } catch (_: SocketTimeoutException) {
                        // Poll running flag.
                    } catch (_: Exception) {
                        if (running.get()) break
                    }
                }
            }, "ListenQuran-LiveStreamAccept").apply {
                isDaemon = true
                start()
            }

            return "http://$boundHostAddress:$boundPort"
        }
    }

    fun setSessionToken(token: String) {
        activeToken.set(token)
        rendererConnectedNotified.set(false)
    }

    fun buildLiveStreamUrl(token: String, format: LiveStreamFormat = LiveStreamFormat.WAV): String {
        val base = baseUrl ?: throw IllegalStateException("LocalMediaHttpServer is not running")
        val ext = if (format == LiveStreamFormat.L16) "l16" else "wav"
        return "$base/live/$token.$ext"
    }

    fun stop() {
        synchronized(serverLock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        running.set(false)
        activeToken.set(null)
        rendererConnectedNotified.set(false)
        runCatching { activeStreamSocket.getAndSet(null)?.close() }
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        workerPool?.shutdownNow()
        workerPool = null
        boundPort = 0
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 10_000
            runCatching { sock.tcpNoDelay = true }
            runCatching { sock.sendBufferSize = 65536 }
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeStatusResponse(output, 400, "Bad Request")
                return
            }
            val method = parts[0].uppercase(Locale.US)
            val rawPath = parts[1].substringBefore('?')

            var rangeHeader: String? = null
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(':').trim()
                }
            }

            if (method != "GET" && method != "HEAD") {
                writeStatusResponse(output, 405, "Method Not Allowed")
                return
            }

            val rangeStart = parseRangeStartOffset(rangeHeader)
            if (rangeStart != null && rangeStart > 0L) {
                writeStatusResponse(output, 416, "Range Not Satisfiable")
                return
            }

            if (!rawPath.startsWith("/live/")) {
                writeStatusResponse(output, 404, "Not Found")
                return
            }
            val filePart = rawPath.removePrefix("/live/")
            val format = when {
                filePart.endsWith(".wav", ignoreCase = true) -> LiveStreamFormat.WAV
                filePart.endsWith(".l16", ignoreCase = true) -> LiveStreamFormat.L16
                else -> {
                    writeStatusResponse(output, 404, "Not Found")
                    return
                }
            }
            val token = filePart.substringBeforeLast('.')
            val expected = activeToken.get()
            if (expected.isNullOrBlank() || token != expected || !hub.isTokenValid(token)) {
                writeStatusResponse(output, 403, "Forbidden")
                return
            }

            if (method == "HEAD") {
                val headers = buildLiveHeaders(format)
                output.write(headers.toByteArray(Charsets.US_ASCII))
                output.flush()
                return
            }

            val clientId = hub.beginClientStream(token)
            if (clientId < 0L) {
                writeStatusResponse(output, 403, "Forbidden")
                return
            }
            activeStreamSocket.getAndSet(sock)?.let { prev ->
                if (prev !== sock) runCatching { prev.close() }
            }

            try {
                val headers = buildLiveHeaders(format)
                output.write(headers.toByteArray(Charsets.US_ASCII))
                output.flush()

                hub.onHttpStreamStarted()
                if (rendererConnectedNotified.compareAndSet(false, true)) {
                    onRendererConnected?.invoke()
                }

                if (format == LiveStreamFormat.WAV) {
                    output.write(buildStreamingWavHeader())
                    output.flush()
                }

                val buffer = ByteArray(32768)
                while (running.get() && hub.isTokenValid(token)) {
                    val n = hub.read(token = token, dst = buffer, offset = 0, maxLen = buffer.size, clientId = clientId)
                    if (n < 0) break
                    if (n == 0) continue

                    if (format == LiveStreamFormat.L16) {
                        swapPairsInPlace(buffer, n)
                    }

                    try {
                        output.write(buffer, 0, n)
                        output.flush()
                    } catch (_: Exception) {
                        break
                    }
                }
            } finally {
                activeStreamSocket.compareAndSet(sock, null)
            }
        }
    }

    private fun buildLiveHeaders(format: LiveStreamFormat): String {
        val contentType = when (format) {
            LiveStreamFormat.WAV -> "audio/wav"
            LiveStreamFormat.L16 -> "audio/L16;rate=${PcmStreamHub.SAMPLE_RATE_HZ};channels=${PcmStreamHub.CHANNEL_COUNT}"
        }
        val ci = if (format == LiveStreamFormat.L16) "1" else "0"
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $contentType\r\n")
            append("transferMode.dlna.org: Streaming\r\n")
            append("contentFeatures.dlna.org: DLNA.ORG_OP=00;DLNA.ORG_CI=$ci;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n")
            append("Accept-Ranges: none\r\n")
            append("Cache-Control: no-store, no-cache\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
    }

    private fun writeStatusResponse(output: OutputStream, code: Int, reason: String) {
        val body = "$code $reason".toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        runCatching {
            output.write(headers.toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                val s = sb.toString()
                return if (s.endsWith("\r")) s.dropLast(1) else s
            }
            sb.append(b.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun swapPairsInPlace(buf: ByteArray, len: Int) {
        var i = 0
        while (i + 1 < len) {
            val b0 = buf[i]
            buf[i] = buf[i + 1]
            buf[i + 1] = b0
            i += 2
        }
    }

    private fun parseRangeStartOffset(rangeHeader: String?): Long? {
        if (rangeHeader.isNullOrBlank()) return null
        val trimmed = rangeHeader.trim()
        if (!trimmed.startsWith("bytes=", ignoreCase = true)) return null
        val rangeSpec = trimmed.substringAfter('=').substringBefore(',').trim()
        val startPart = rangeSpec.substringBefore('-').trim()
        return startPart.toLongOrNull()
    }

    companion object {
        private const val STREAM_DATA_SIZE = 0x7FFFFF00
        private const val STREAM_RIFF_SIZE = STREAM_DATA_SIZE + 36

        fun buildStreamingWavHeader(): ByteArray {
            val header = ByteArray(44)
            fun putAscii(offset: Int, text: String) {
                for (i in text.indices) header[offset + i] = text[i].code.toByte()
            }
            fun putIntLe(offset: Int, v: Int) {
                header[offset] = (v and 0xFF).toByte()
                header[offset + 1] = ((v ushr 8) and 0xFF).toByte()
                header[offset + 2] = ((v ushr 16) and 0xFF).toByte()
                header[offset + 3] = ((v ushr 24) and 0xFF).toByte()
            }
            fun putShortLe(offset: Int, v: Int) {
                header[offset] = (v and 0xFF).toByte()
                header[offset + 1] = ((v ushr 8) and 0xFF).toByte()
            }

            val sampleRate = PcmStreamHub.SAMPLE_RATE_HZ
            val channels = PcmStreamHub.CHANNEL_COUNT
            val bitsPerSample = PcmStreamHub.BITS_PER_SAMPLE
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            val blockAlign = channels * (bitsPerSample / 8)

            putAscii(0, "RIFF")
            putIntLe(4, STREAM_RIFF_SIZE)
            putAscii(8, "WAVE")
            putAscii(12, "fmt ")
            putIntLe(16, 16)
            putShortLe(20, 1)
            putShortLe(22, channels)
            putIntLe(24, sampleRate)
            putIntLe(28, byteRate)
            putShortLe(32, blockAlign)
            putShortLe(34, bitsPerSample)
            putAscii(36, "data")
            putIntLe(40, STREAM_DATA_SIZE)
            return header
        }
    }
}

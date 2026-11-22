package com.listenquran.player.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CastMediaSource {
    val contentType: String

    data class LocalFile(
        val file: File,
        override val contentType: String = "audio/mpeg",
    ) : CastMediaSource

    data class RemoteHttpProxy(
        val upstreamUrl: String,
        override val contentType: String = "audio/mpeg",
    ) : CastMediaSource
}

@Singleton
class LocalMediaHttpServer @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val lock = Any()
    private val registry = ConcurrentHashMap<String, CastMediaSource>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var serverScope: CoroutineScope? = null

    @Volatile
    private var sessionToken: String = UUID.randomUUID().toString()

    @Volatile
    private var baseUrl: String? = null

    val isRunning: Boolean
        get() = serverSocket?.let { !it.isClosed } == true

    fun start(bindAddress: InetAddress, port: Int = 0): String {
        synchronized(lock) {
            val currentSocket = serverSocket
            val currentBase = baseUrl
            if (currentSocket != null && !currentSocket.isClosed && currentBase != null &&
                currentSocket.inetAddress == bindAddress
            ) {
                return currentBase
            }
            stopLocked()

            val newToken = UUID.randomUUID().toString()
            val socket = ServerSocket(port, 16, bindAddress)
            val host = bindAddress.hostAddress?.let {
                if (it.contains(':')) "[$it]" else it
            } ?: "127.0.0.1"
            val url = "http://$host:${socket.localPort}"

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            sessionToken = newToken
            serverSocket = socket
            baseUrl = url
            serverScope = scope

            scope.launch {
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch {
                            handleClient(client, newToken)
                        }
                    } catch (_: IOException) {
                        if (socket.isClosed) break
                    }
                }
            }
            return url
        }
    }

    fun registerMedia(mediaKey: String, source: CastMediaSource): String {
        val currentBase = baseUrl ?: throw IllegalStateException("LocalMediaHttpServer is not started")
        val sanitizedKey = mediaKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        registry[sanitizedKey] = source
        return "$currentBase/stream/$sessionToken/$sanitizedKey.mp3"
    }

    fun clearMedia() {
        registry.clear()
    }

    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        registry.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        baseUrl = null
        serverScope?.cancel()
        serverScope = null
    }

    private fun handleClient(client: Socket, expectedToken: String) {
        client.use { socket ->
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeStatusResponse(output, 400, "Bad Request", "Invalid request line")
                return
            }

            val method = parts[0].uppercase(Locale.US)
            val rawPath = parts[1].substringBefore('?')

            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase(Locale.US)] =
                        line.substring(colon + 1).trim()
                }
            }

            if (method != "GET" && method != "HEAD") {
                writeStatusResponse(output, 405, "Method Not Allowed", "Only GET and HEAD supported")
                return
            }

            val segments = rawPath.trimStart('/').split('/')
            if (segments.size != 3 || segments[0] != "stream") {
                writeStatusResponse(output, 403, "Forbidden", "Invalid stream path")
                return
            }

            val token = segments[1]
            if (token != expectedToken) {
                writeStatusResponse(output, 403, "Forbidden", "Invalid session token")
                return
            }

            val filePart = segments[2]
            val mediaKey = filePart.removeSuffix(".mp3")
            val source = registry[mediaKey] ?: registry[filePart]
            if (source == null) {
                writeStatusResponse(output, 404, "Not Found", "Unknown media key")
                return
            }

            val rangeHeader = headers["range"]
            when (source) {
                is CastMediaSource.LocalFile -> serveLocalFile(source, method, rangeHeader, output)
                is CastMediaSource.RemoteHttpProxy -> serveRemoteProxy(source, method, rangeHeader, output)
            }
        }
    }

    private fun serveLocalFile(
        source: CastMediaSource.LocalFile,
        method: String,
        rangeHeader: String?,
        output: OutputStream,
    ) {
        val file = source.file
        if (!file.exists() || !file.isFile) {
            writeStatusResponse(output, 404, "Not Found", "Audio file missing")
            return
        }

        val totalLength = file.length()
        if (totalLength <= 0L) {
            writeStatusResponse(output, 200, "OK", "")
            return
        }

        var start = 0L
        var end = totalLength - 1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank()) {
            val parsed = parseByteRange(rangeHeader, totalLength)
            if (parsed == null) {
                val header = buildString {
                    append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                    append("Content-Range: bytes */$totalLength\r\n")
                    append("Content-Length: 0\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(header.toByteArray(Charsets.UTF_8))
                output.flush()
                return
            }
            start = parsed.first
            end = parsed.second
            isPartial = true
        }

        val contentLength = (end - start) + 1L
        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
        val header = buildString {
            append("$statusLine\r\n")
            append("Content-Type: ${source.contentType}\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (isPartial) {
                append("Content-Range: bytes $start-$end/$totalLength\r\n")
            }
            append("transferMode.dlna.org: Streaming\r\n")
            append("contentFeatures.dlna.org: DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()

        if (method == "HEAD") return

        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(16 * 1024)
                var remaining = contentLength
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
            }
        } catch (_: IOException) {
            // Client renderers often close probe connections before starting range streaming.
        }
    }

    private fun serveRemoteProxy(
        source: CastMediaSource.RemoteHttpProxy,
        method: String,
        rangeHeader: String?,
        output: OutputStream,
    ) {
        val reqBuilder = Request.Builder().url(source.upstreamUrl)
        if (!rangeHeader.isNullOrBlank()) {
            reqBuilder.header("Range", rangeHeader)
        }
        if (method == "HEAD") {
            reqBuilder.head()
        } else {
            reqBuilder.get()
        }

        try {
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val contentType = response.header("Content-Type") ?: source.contentType
                val contentRange = response.header("Content-Range")
                val acceptRanges = response.header("Accept-Ranges") ?: "bytes"
                val contentLengthHeader = response.header("Content-Length")
                    ?: response.body?.contentLength()?.takeIf { it >= 0 }?.toString()

                val statusText = when (response.code) {
                    200 -> "OK"
                    206 -> "Partial Content"
                    416 -> "Range Not Satisfiable"
                    else -> response.message.ifBlank { "Error" }
                }

                val header = buildString {
                    append("HTTP/1.1 ${response.code} $statusText\r\n")
                    append("Content-Type: $contentType\r\n")
                    if (contentLengthHeader != null) {
                        append("Content-Length: $contentLengthHeader\r\n")
                    }
                    if (!contentRange.isNullOrBlank()) {
                        append("Content-Range: $contentRange\r\n")
                    }
                    append("Accept-Ranges: $acceptRanges\r\n")
                    append("transferMode.dlna.org: Streaming\r\n")
                    append("contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(header.toByteArray(Charsets.UTF_8))
                output.flush()

                if (method == "HEAD" || !response.isSuccessful) return

                val bodyStream = response.body?.byteStream() ?: return
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = bodyStream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } catch (_: IOException) {
            // Upstream or downstream closed mid-stream.
        }
    }

    private fun writeStatusResponse(
        output: OutputStream,
        code: Int,
        reason: String,
        body: String,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        runCatching {
            output.write(response.toByteArray(Charsets.UTF_8))
            output.write(bytes)
            output.flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(64)
        while (true) {
            val next = input.read()
            if (next == -1) {
                return if (bytes.isEmpty()) null else String(bytes.toByteArray(), Charsets.ISO_8859_1)
            }
            if (next == '\n'.code) break
            if (next != '\r'.code) {
                bytes.add(next.toByte())
            }
        }
        return String(bytes.toByteArray(), Charsets.ISO_8859_1)
    }

    internal fun parseByteRange(rangeHeader: String, totalLength: Long): Pair<Long, Long>? {
        val trimmed = rangeHeader.trim()
        if (!trimmed.startsWith("bytes=", ignoreCase = true)) return null
        val spec = trimmed.substring(6).substringBefore(',').trim()
        val dashIdx = spec.indexOf('-')
        if (dashIdx < 0) return null

        val startPart = spec.substring(0, dashIdx).trim()
        val endPart = spec.substring(dashIdx + 1).trim()

        return if (startPart.isEmpty()) {
            val suffix = endPart.toLongOrNull() ?: return null
            if (suffix <= 0) return null
            val start = (totalLength - suffix).coerceAtLeast(0L)
            start to (totalLength - 1L)
        } else {
            val start = startPart.toLongOrNull() ?: return null
            val end = if (endPart.isEmpty()) totalLength - 1L else endPart.toLongOrNull() ?: return null
            if (start < 0 || start >= totalLength || end < start) return null
            start to end.coerceAtMost(totalLength - 1L)
        }
    }
}

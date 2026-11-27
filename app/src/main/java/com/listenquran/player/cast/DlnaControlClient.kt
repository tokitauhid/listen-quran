package com.listenquran.player.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface RemoteControlClient {
    suspend fun setTransportUri(device: OutputDevice, mediaUrl: String, metadataXml: String): Result<Unit>
    suspend fun setNextTransportUri(device: OutputDevice, mediaUrl: String, metadataXml: String): Result<Unit>
    suspend fun play(device: OutputDevice): Result<Unit>
    suspend fun pause(device: OutputDevice): Result<Unit>
    suspend fun stop(device: OutputDevice): Result<Unit>
    suspend fun seek(device: OutputDevice, positionMs: Long): Result<Unit>
    suspend fun getPositionInfo(device: OutputDevice): Result<RemotePositionInfo>
    suspend fun getTransportInfo(device: OutputDevice): Result<RemoteTransportState>
    suspend fun getVolume(device: OutputDevice): Result<Int>
    suspend fun setVolume(device: OutputDevice, volume: Int): Result<Unit>
    suspend fun setMute(device: OutputDevice, mute: Boolean): Result<Unit>
}

@Singleton
class DlnaControlClient @Inject constructor(
    okHttpClient: OkHttpClient,
) : RemoteControlClient {

    private val client: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    override suspend fun setTransportUri(
        device: OutputDevice,
        mediaUrl: String,
        metadataXml: String,
    ): Result<Unit> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "SetAVTransportURI",
            arguments = listOf(
                "InstanceID" to "0",
                "CurrentURI" to mediaUrl,
                "CurrentURIMetaData" to metadataXml,
            ),
        ).map { Unit }
    }

    override suspend fun setNextTransportUri(
        device: OutputDevice,
        mediaUrl: String,
        metadataXml: String,
    ): Result<Unit> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "SetNextAVTransportURI",
            arguments = listOf(
                "InstanceID" to "0",
                "NextURI" to mediaUrl,
                "NextURIMetaData" to metadataXml,
            ),
        ).map { Unit }
    }

    override suspend fun play(device: OutputDevice): Result<Unit> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "Play",
            arguments = listOf(
                "InstanceID" to "0",
                "Speed" to "1",
            ),
        ).map { Unit }
    }

    override suspend fun pause(device: OutputDevice): Result<Unit> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "Pause",
            arguments = listOf("InstanceID" to "0"),
        ).map { Unit }
    }

    override suspend fun stop(device: OutputDevice): Result<Unit> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "Stop",
            arguments = listOf("InstanceID" to "0"),
        ).map { Unit }
    }

    override suspend fun seek(device: OutputDevice, positionMs: Long): Result<Unit> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "Seek",
            arguments = listOf(
                "InstanceID" to "0",
                "Unit" to "REL_TIME",
                "Target" to DlnaXmlParser.formatDlnaTime(positionMs),
            ),
        ).map { Unit }
    }

    override suspend fun getPositionInfo(device: OutputDevice): Result<RemotePositionInfo> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "GetPositionInfo",
            arguments = listOf("InstanceID" to "0"),
        ).map { fields ->
            RemotePositionInfo(
                relTimeMs = DlnaXmlParser.parseDlnaTimeToMs(fields["RelTime"]),
                trackDurationMs = DlnaXmlParser.parseDlnaTimeToMs(fields["TrackDuration"]),
                trackUri = fields["TrackURI"]?.takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun getTransportInfo(device: OutputDevice): Result<RemoteTransportState> {
        val controlUrl = device.avTransportControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks AVTransport control URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.AV_TRANSPORT_SERVICE,
            action = "GetTransportInfo",
            arguments = listOf("InstanceID" to "0"),
        ).map { fields ->
            RemoteTransportState.fromUpnpString(fields["CurrentTransportState"])
        }
    }

    override suspend fun getVolume(device: OutputDevice): Result<Int> {
        val controlUrl = device.renderingControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks RenderingControl URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.RENDERING_CONTROL_SERVICE,
            action = "GetVolume",
            arguments = listOf(
                "InstanceID" to "0",
                "Channel" to "Master",
            ),
        ).map { fields ->
            (fields["CurrentVolume"]?.toIntOrNull() ?: 50).coerceIn(0, 100)
        }
    }

    override suspend fun setVolume(device: OutputDevice, volume: Int): Result<Unit> {
        val controlUrl = device.renderingControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks RenderingControl URL"))
        val clamped = volume.coerceIn(0, 100)
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.RENDERING_CONTROL_SERVICE,
            action = "SetVolume",
            arguments = listOf(
                "InstanceID" to "0",
                "Channel" to "Master",
                "DesiredVolume" to clamped.toString(),
            ),
        ).map { Unit }
    }

    override suspend fun setMute(device: OutputDevice, mute: Boolean): Result<Unit> {
        val controlUrl = device.renderingControlUrl
            ?: return Result.failure(IllegalStateException("Device lacks RenderingControl URL"))
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = DlnaXmlParser.RENDERING_CONTROL_SERVICE,
            action = "SetMute",
            arguments = listOf(
                "InstanceID" to "0",
                "Channel" to "Master",
                "DesiredMute" to if (mute) "1" else "0",
            ),
        ).map { Unit }
    }

    private suspend fun sendSoapAction(
        controlUrl: String,
        serviceType: String,
        action: String,
        arguments: List<Pair<String, String>>,
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        if (!DlnaXmlParser.isPrivateLanHost(controlUrl)) {
            return@withContext Result.failure(
                SecurityException("Refusing non-LAN DLNA control URL: $controlUrl")
            )
        }

        try {
            val envelope = DlnaXmlParser.buildSoapEnvelope(serviceType, action, arguments)
            val request = Request.Builder()
                .url(controlUrl)
                .header("SOAPAction", "\"$serviceType#$action\"")
                .post(envelope.toRequestBody(SOAP_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val parsed = DlnaXmlParser.parseSoapResponse(body)
                if (!response.isSuccessful) {
                    val code = parsed["errorCode"]?.takeIf { it.isNotBlank() }
                    val errDesc = parsed["errorDescription"] ?: parsed["faultstring"] ?: "HTTP ${response.code}"
                    val detail = if (code != null) "$errDesc [code $code]" else errDesc
                    return@withContext Result.failure(
                        IllegalStateException("DLNA $action failed ($detail)")
                    )
                }
                if (parsed.containsKey("faultcode") || parsed.containsKey("errorCode")) {
                    val code = parsed["errorCode"]?.takeIf { it.isNotBlank() }
                    val errDesc = parsed["errorDescription"] ?: parsed["faultstring"] ?: "UPnP Fault"
                    val detail = if (code != null) "$errDesc [code $code]" else errDesc
                    return@withContext Result.failure(
                        IllegalStateException("DLNA $action fault: $detail")
                    )
                }
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        val SOAP_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()
    }
}

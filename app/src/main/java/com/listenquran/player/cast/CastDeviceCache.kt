package com.listenquran.player.cast

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.castDevicesDataStore: DataStore<Preferences> by preferencesDataStore(name = "cast_devices")

@JsonClass(generateAdapter = true)
data class CachedOutputDeviceDto(
    val id: String,
    val name: String,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val deviceType: String = OutputDeviceType.SPEAKER.name,
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val locationUrl: String? = null,
    val hostAddress: String? = null,
    val preferredFormat: String = LiveStreamFormat.WAV.name,
)

@JsonClass(generateAdapter = true)
data class CastDeviceCachePayload(
    val devices: List<CachedOutputDeviceDto> = emptyList(),
)

/**
 * Persists recently discovered DLNA speakers and their working stream format (WAV vs L16)
 * so the output picker renders immediately on cold app launch before M-SEARCH completes.
 */
@Singleton
class CastDeviceCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(CastDeviceCachePayload::class.java)
    private val cacheKey = stringPreferencesKey("cached_dlna_devices_json")

    suspend fun load(): Pair<List<OutputDevice>, Map<String, LiveStreamFormat>> {
        return runCatching {
            val prefs = context.castDevicesDataStore.data.first()
            val raw = prefs[cacheKey] ?: return emptyList<OutputDevice>() to emptyMap()
            val payload = adapter.fromJson(raw) ?: return emptyList<OutputDevice>() to emptyMap()
            val devices = payload.devices.map { dto ->
                OutputDevice(
                    id = dto.id,
                    name = dto.name,
                    manufacturer = dto.manufacturer,
                    modelName = dto.modelName,
                    protocol = CastProtocol.DLNA,
                    deviceType = runCatching { OutputDeviceType.valueOf(dto.deviceType) }
                        .getOrDefault(OutputDeviceType.SPEAKER),
                    avTransportControlUrl = dto.avTransportControlUrl,
                    renderingControlUrl = dto.renderingControlUrl,
                    locationUrl = dto.locationUrl,
                    hostAddress = dto.hostAddress,
                )
            }
            val formats = payload.devices.associate { dto ->
                dto.id to runCatching { LiveStreamFormat.valueOf(dto.preferredFormat) }
                    .getOrDefault(LiveStreamFormat.WAV)
            }
            devices to formats
        }.getOrDefault(emptyList<OutputDevice>() to emptyMap())
    }

    suspend fun save(devices: List<OutputDevice>, formats: Map<String, LiveStreamFormat>) {
        runCatching {
            val dtos = devices
                .filter { it.protocol == CastProtocol.DLNA }
                .map { dev ->
                    CachedOutputDeviceDto(
                        id = dev.id,
                        name = dev.name,
                        manufacturer = dev.manufacturer,
                        modelName = dev.modelName,
                        deviceType = dev.deviceType.name,
                        avTransportControlUrl = dev.avTransportControlUrl,
                        renderingControlUrl = dev.renderingControlUrl,
                        locationUrl = dev.locationUrl,
                        hostAddress = dev.hostAddress,
                        preferredFormat = (formats[dev.id] ?: LiveStreamFormat.WAV).name,
                    )
                }
            val json = adapter.toJson(CastDeviceCachePayload(dtos))
            context.castDevicesDataStore.edit { prefs ->
                prefs[cacheKey] = json
            }
        }
    }
}

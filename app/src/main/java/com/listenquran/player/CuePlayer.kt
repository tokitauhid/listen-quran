package com.listenquran.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.listenquran.data.repository.QuranDownloadRepository
import com.listenquran.data.repository.QuranRepository
import com.listenquran.player.cast.PcmSource
import com.listenquran.player.cast.PcmStreamHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Plays short recitation cues (currently the active reciter's Bismillah from Al-Fatihah Ayah 1)
 * on a secondary [ExoPlayer] whose PCM tee feeds the same [com.listenquran.player.cast.PcmStreamHub]
 * as the main player so cues play seamlessly on both phone/BT and Wi-Fi speakers.
 */
@Singleton
class CuePlayer @Inject constructor(
    @Named("cue") private val cuePlayerProvider: Provider<ExoPlayer>,
    private val quranRepository: QuranRepository,
    private val downloadRepository: QuranDownloadRepository,
    private val pcmStreamHub: PcmStreamHub,
) {
    suspend fun playBismillah(
        reciterId: Int,
        playbackSpeed: Float,
        isCastMuted: Boolean,
    ): Boolean {
        val timings = quranRepository.getVerseTimings(reciterId, 1)
        val v1 = timings.firstOrNull { it.verseKey == "1:1" } ?: timings.firstOrNull() ?: return false
        val v1EndMs = v1.timestampTo
        if (v1EndMs !in 1200L..20_000L) return false

        val localFile = downloadRepository.getDownloadedSurah(1, reciterId)?.localFilePath
            ?.takeIf { File(it).exists() }
        val mediaUri = if (localFile != null) {
            Uri.fromFile(File(localFile))
        } else {
            val remoteUrl = quranRepository.getChapterAudio(reciterId, 1)?.audioUrl ?: return false
            Uri.parse(remoteUrl)
        }

        val clipItem = MediaItem.Builder()
            .setMediaId("cue:bismillah:reciter:$reciterId")
            .setUri(mediaUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs(v1EndMs)
                    .build()
            )
            .build()

        val maxWaitMs = ((v1EndMs / playbackSpeed.coerceAtLeast(0.5f)).toLong() + 5_000L)

        return withContext(Dispatchers.Main) {
            val player = cuePlayerProvider.get()
            pcmStreamHub.activeSource = PcmSource.CUE
            try {
                val finished = withTimeoutOrNull(maxWaitMs) {
                    suspendCancellableCoroutine<Boolean> { cont ->
                        val listener = object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    player.removeListener(this)
                                    if (cont.isActive) cont.resume(true)
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                player.removeListener(this)
                                if (cont.isActive) cont.resume(false)
                            }
                        }

                        cont.invokeOnCancellation {
                            player.removeListener(listener)
                            runCatching {
                                player.stop()
                                player.clearMediaItems()
                            }
                        }

                        player.addListener(listener)
                        player.volume = if (isCastMuted) 0f else 1f
                        player.playbackParameters = PlaybackParameters(playbackSpeed)
                        player.setMediaItem(clipItem)
                        player.prepare()
                        player.play()
                    }
                } ?: false

                runCatching {
                    player.stop()
                    player.clearMediaItems()
                }
                finished
            } finally {
                pcmStreamHub.activeSource = PcmSource.MAIN
            }
        }
    }

    suspend fun setMuted(muted: Boolean) {
        withContext(Dispatchers.Main) {
            runCatching {
                cuePlayerProvider.get().volume = if (muted) 0f else 1f
            }
        }
    }

    suspend fun stop() {
        pcmStreamHub.activeSource = PcmSource.MAIN
        withContext(Dispatchers.Main) {
            runCatching {
                val player = cuePlayerProvider.get()
                player.stop()
                player.clearMediaItems()
            }
        }
    }
}

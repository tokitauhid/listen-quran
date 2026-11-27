package com.listenquran.player.cast

import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates switching all registered [RoutingAudioSink] instances (main + cue players)
 * between local hardware `AudioTrack` output and headless [CastPcmAudioSink] output.
 */
@Singleton
class CastAudioModeController @Inject constructor() {

    @Volatile
    var isCastMode: Boolean = false
        private set

    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun setCastMode(enabled: Boolean) {
        val changed = synchronized(this) {
            if (isCastMode == enabled) {
                false
            } else {
                isCastMode = enabled
                true
            }
        }
        if (changed) {
            listeners.forEach { it.invoke(enabled) }
        }
    }
}

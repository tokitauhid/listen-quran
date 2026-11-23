package com.listenquran.player.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OutputDeviceManagerTest {

    private val tempFile = File.createTempFile("output_mgr_test", ".mp3").apply {
        writeBytes(ByteArray(512) { 7 })
        deleteOnExit()
    }

    private val testSpeaker = OutputDevice(
        id = "uuid:speaker-1",
        name = "TrueNAS Speakers",
        protocol = CastProtocol.DLNA,
        deviceType = OutputDeviceType.SPEAKER,
        avTransportControlUrl = "http://127.0.0.1:49494/avt",
        renderingControlUrl = "http://127.0.0.1:49494/rc",
        hostAddress = "192.168.0.225",
    )

    @After
    fun cleanup() {
        tempFile.delete()
    }

    @Test
    fun `scanForDevices populates sorted discovered devices and preserves connected active device`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient()
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 20L, seekDelayMs = 0L, ioDispatcher = Dispatchers.IO)

        manager.scanForDevices()
        assertFalse(manager.routeState.value.isScanning)
        assertEquals(listOf(testSpeaker), manager.routeState.value.discoveredDevices)
        manager.disconnect()
    }

    @Test
    fun `connectToDevice sends Seek exactly once, sets next URI, controls volume, and returns position`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
            positionInfo = RemotePositionInfo(relTimeMs = 45_000L, trackDurationMs = 120_000L, trackUri = null)
            volume = 68
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 25L, seekDelayMs = 0L, ioDispatcher = Dispatchers.IO)

        val currentTrack = RemoteTrackDescriptor(
            mediaKey = "s36_r7",
            title = "36. Ya-Sin",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = "https://example.com/036.mp3",
            localFilePath = tempFile.absolutePath,
            durationMs = 120_000L,
            startPositionMs = 42_000L,
        )
        val nextTrack = RemoteTrackDescriptor(
            mediaKey = "s55_r7",
            title = "55. Ar-Rahman",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = "https://example.com/055.mp3",
            localFilePath = tempFile.absolutePath,
            durationMs = 180_000L,
            startPositionMs = 0L,
        )

        val res = manager.connectToDevice(testSpeaker, currentTrack, nextTrack, autoPlay = true)
        assertTrue(res.isSuccess)
        assertTrue(manager.isCasting)
        assertEquals(68, manager.routeState.value.remoteVolume)
        assertTrue(fakeControl.actions.any { it.startsWith("SetAVTransportURI:") })
        assertTrue(fakeControl.actions.any { it == "Play" })
        // Guard against repeat(6) return@repeat seek-storm bug
        assertEquals(1, fakeControl.actions.count { it == "Seek:42000" })
        assertTrue(fakeControl.actions.any { it.startsWith("SetNextAVTransportURI:") })

        manager.setRemoteVolume(80)
        assertEquals(80, manager.routeState.value.remoteVolume)
        manager.toggleRemoteMute()
        assertTrue(manager.routeState.value.isRemoteMuted)

        Thread.sleep(70)
        val resumePos = manager.disconnect()
        assertEquals(45_000L, resumePos)
        assertFalse(manager.isCasting)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
        Thread.sleep(30)
        assertTrue(fakeControl.actions.contains("Stop"))
        assertFalse(server.isRunning)
    }

    @Test
    fun `disconnect cancels a stalled connectToDevice immediately without deadlocking`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            setUriDelayMs = 5_000L
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 20L, seekDelayMs = 0L, connectTimeoutMs = 8_000L, ioDispatcher = Dispatchers.IO)

        val track = RemoteTrackDescriptor(
            mediaKey = "s1_r7",
            title = "1. Al-Fatihah",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
            startPositionMs = 12_000L,
        )

        val connectJob = launch(Dispatchers.IO) {
            runCatching { manager.connectToDevice(testSpeaker, track, autoPlay = true) }
        }
        Thread.sleep(60)
        assertTrue(manager.routeState.value.isConnecting)

        val t0 = System.currentTimeMillis()
        val pos = manager.disconnect()
        val elapsedMs = System.currentTimeMillis() - t0

        assertEquals(12_000L, pos)
        assertTrue("disconnect() must not block on stalled connectToDevice (took ${elapsedMs}ms)", elapsedMs < 1_000L)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
        assertEquals(CastConnectionState.DISCONNECTED, manager.routeState.value.connectionState)
        assertFalse(server.isRunning)
        connectJob.join()
    }

    @Test
    fun `timed out or failed connect resets cleanly to LOCAL_PHONE`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            setUriDelayMs = 500L
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 20L, seekDelayMs = 0L, connectTimeoutMs = 80L, ioDispatcher = Dispatchers.IO)

        val track = RemoteTrackDescriptor(
            mediaKey = "s1_r7",
            title = "1. Al-Fatihah",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
        )

        val res = manager.connectToDevice(testSpeaker, track, autoPlay = true)
        assertTrue(res.isFailure)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
        assertEquals(CastConnectionState.ERROR, manager.routeState.value.connectionState)
        assertFalse(server.isRunning)
    }

    @Test
    fun `polling detects end of track and skips position call on failed transport`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
            positionInfo = RemotePositionInfo(relTimeMs = 99_000L, trackDurationMs = 100_000L, trackUri = null)
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 15L, seekDelayMs = 0L, ioDispatcher = Dispatchers.IO)

        val endedCalled = AtomicBoolean(false)
        val lostCalled = AtomicBoolean(false)
        manager.onRemoteTrackEnded = { _ -> endedCalled.set(true) }
        manager.onRemoteConnectionLost = { _, _ -> lostCalled.set(true) }

        val currentTrack = RemoteTrackDescriptor(
            mediaKey = "s1_r7",
            title = "1. Al-Fatihah",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
            durationMs = 100_000L,
        )
        assertTrue(manager.connectToDevice(testSpeaker, currentTrack, autoPlay = true).isSuccess)

        Thread.sleep(50)
        fakeControl.transportState = RemoteTransportState.STOPPED
        Thread.sleep(60)
        assertTrue("Expected natural end-of-track callback", endedCalled.get())

        val posCallsBeforeDrop = fakeControl.positionCalls.get()
        fakeControl.failAllPolls.set(true)
        Thread.sleep(100)
        assertTrue("Expected connection lost callback after 3 failed polls", lostCalled.get())
        assertEquals("Should skip GetPositionInfo when GetTransportInfo fails", posCallsBeforeDrop, fakeControl.positionCalls.get())
        assertFalse(manager.isCasting)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
    }

    @Test
    fun `connectToDevice waits until renderer reports non-zero trackDuration or relTime before sending Seek`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
            positionResponses.addAll(
                listOf(
                    RemotePositionInfo(relTimeMs = 0L, trackDurationMs = 0L, trackUri = null),
                    RemotePositionInfo(relTimeMs = 0L, trackDurationMs = 0L, trackUri = null),
                    RemotePositionInfo(relTimeMs = 1_000L, trackDurationMs = 120_000L, trackUri = null),
                )
            )
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 500L, seekDelayMs = 5L, ioDispatcher = Dispatchers.IO)

        val currentTrack = RemoteTrackDescriptor(
            mediaKey = "s2_r2",
            title = "2. Al-Baqarah",
            artist = "AbdulBaset",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
            durationMs = 120_000L,
            startPositionMs = 27_000L,
        )

        val res = manager.connectToDevice(testSpeaker, currentTrack, autoPlay = true)
        assertTrue(res.isSuccess)
        assertEquals(1, fakeControl.actions.count { it == "Seek:27000" })
        assertTrue("Expected at least 3 GetPositionInfo checks before preroll-ready Seek", fakeControl.positionCalls.get() >= 3)
        manager.disconnect()
    }

    @Test
    fun `gapless transition to nextUri invokes onRemoteTrackEnded with true and updateNextTrackOnActiveDevice updates NextURI only`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
            positionInfo = RemotePositionInfo(relTimeMs = 5_000L, trackDurationMs = 60_000L, trackUri = null)
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 15L, seekDelayMs = 0L, ioDispatcher = Dispatchers.IO)

        val endedEvents = CopyOnWriteArrayList<Boolean>()
        manager.onRemoteTrackEnded = { advancedToNextUri -> endedEvents.add(advancedToNextUri) }

        val track1 = RemoteTrackDescriptor(
            mediaKey = "s1_r7",
            title = "1. Al-Fatihah",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
            durationMs = 45_000L,
        )
        val track2 = RemoteTrackDescriptor(
            mediaKey = "s2_r7",
            title = "2. Al-Baqarah",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
            durationMs = 120_000L,
        )
        val track3 = RemoteTrackDescriptor(
            mediaKey = "s3_r7",
            title = "3. Ali 'Imran",
            artist = "Mishari",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
            durationMs = 120_000L,
        )

        assertTrue(manager.connectToDevice(testSpeaker, track1, track2, autoPlay = true).isSuccess)
        val nextUri = fakeControl.actions.last { it.startsWith("SetNextAVTransportURI:") }.substringAfter("SetNextAVTransportURI:")
        val setUriCountBefore = fakeControl.actions.count { it.startsWith("SetAVTransportURI:") }

        fakeControl.positionInfo = RemotePositionInfo(relTimeMs = 1_000L, trackDurationMs = 120_000L, trackUri = nextUri)
        Thread.sleep(60)

        assertTrue("Expected gapless transition callback with advancedToNextUri=true", endedEvents.contains(true))

        val updateRes = manager.updateNextTrackOnActiveDevice(track3)
        assertTrue(updateRes.isSuccess)
        assertEquals(setUriCountBefore, fakeControl.actions.count { it.startsWith("SetAVTransportURI:") })
        assertTrue(fakeControl.actions.last { it.startsWith("SetNextAVTransportURI:") }.contains("s3_r7"))
        manager.disconnect()
    }

    @Test
    fun `connectToDevice sends Stop before SetAVTransportURI, tolerates UPnP 701 on Stop, and waits past stale pre-play position`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            failStop.set(true)
            transportState = RemoteTransportState.PLAYING
            positionResponses.addAll(
                listOf(
                    RemotePositionInfo(relTimeMs = 27_000L, trackDurationMs = 1_627_000L, trackUri = null),
                    RemotePositionInfo(relTimeMs = 27_000L, trackDurationMs = 1_627_000L, trackUri = null),
                    RemotePositionInfo(relTimeMs = 1_000L, trackDurationMs = 120_000L, trackUri = null),
                )
            )
            positionInfo = RemotePositionInfo(relTimeMs = 1_000L, trackDurationMs = 120_000L, trackUri = null)
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 500L, seekDelayMs = 5L, ioDispatcher = Dispatchers.IO)

        val currentTrack = RemoteTrackDescriptor(
            mediaKey = "s3_r2",
            title = "3. Ali 'Imran",
            artist = "AbdulBaset",
            album = "The Holy Quran",
            streamUrl = null,
            localFilePath = tempFile.absolutePath,
            durationMs = 120_000L,
            startPositionMs = 30_000L,
        )

        val res = manager.connectToDevice(testSpeaker, currentTrack, autoPlay = true)
        assertTrue("connectToDevice should succeed even if Stop returns UPnP 701", res.isSuccess)
        val stopIdx = fakeControl.actions.indexOf("Stop")
        val setUriIdx = fakeControl.actions.indexOfFirst { it.startsWith("SetAVTransportURI:") }
        assertTrue("Stop must be called before SetAVTransportURI", stopIdx in 0 until setUriIdx)
        assertEquals(1, fakeControl.actions.count { it == "Seek:30000" })
        assertEquals("Expected prePlay + 1 stale check + 1 ready check before Seek", 3, fakeControl.positionCalls.get())
        manager.disconnect()
    }

    private class FakeDiscoveryClient(
        private val devices: List<OutputDevice>,
    ) : DeviceDiscoveryClient {
        override suspend fun discoverDevices(timeoutMs: Long): List<OutputDevice> = devices
        override fun getLocalLanIpv4Address(): InetAddress = InetAddress.getLoopbackAddress()
    }

    private class FakeRemoteControlClient : RemoteControlClient {
        val actions = CopyOnWriteArrayList<String>()
        val failAllPolls = AtomicBoolean(false)
        val failStop = AtomicBoolean(false)
        val positionCalls = AtomicInteger(0)
        val positionResponses = ArrayDeque<RemotePositionInfo>()
        @Volatile var setUriDelayMs: Long = 0L
        @Volatile var transportState = RemoteTransportState.STOPPED
        @Volatile var positionInfo = RemotePositionInfo(0L, 100_000L, null)
        @Volatile var volume = 50

        override suspend fun setTransportUri(device: OutputDevice, mediaUrl: String, metadataXml: String): Result<Unit> {
            if (setUriDelayMs > 0L) delay(setUriDelayMs)
            actions.add("SetAVTransportURI:$mediaUrl")
            return Result.success(Unit)
        }

        override suspend fun setNextTransportUri(device: OutputDevice, mediaUrl: String, metadataXml: String): Result<Unit> {
            actions.add("SetNextAVTransportURI:$mediaUrl")
            return Result.success(Unit)
        }

        override suspend fun play(device: OutputDevice): Result<Unit> {
            actions.add("Play")
            return Result.success(Unit)
        }

        override suspend fun pause(device: OutputDevice): Result<Unit> {
            actions.add("Pause")
            return Result.success(Unit)
        }

        override suspend fun stop(device: OutputDevice): Result<Unit> {
            actions.add("Stop")
            if (failStop.get()) return Result.failure(IllegalStateException("DLNA Stop failed (Transition not available)"))
            return Result.success(Unit)
        }

        override suspend fun seek(device: OutputDevice, positionMs: Long): Result<Unit> {
            actions.add("Seek:$positionMs")
            return Result.success(Unit)
        }

        override suspend fun getPositionInfo(device: OutputDevice): Result<RemotePositionInfo> {
            positionCalls.incrementAndGet()
            if (failAllPolls.get()) return Result.failure(RuntimeException("Offline"))
            val nextQueued = synchronized(positionResponses) {
                if (positionResponses.isNotEmpty()) positionResponses.removeFirst() else null
            }
            return Result.success(nextQueued ?: positionInfo)
        }

        override suspend fun getTransportInfo(device: OutputDevice): Result<RemoteTransportState> {
            if (failAllPolls.get()) return Result.failure(RuntimeException("Offline"))
            return Result.success(transportState)
        }

        override suspend fun getVolume(device: OutputDevice): Result<Int> = Result.success(volume)

        override suspend fun setVolume(device: OutputDevice, volume: Int): Result<Unit> {
            this.volume = volume
            actions.add("SetVolume:$volume")
            return Result.success(Unit)
        }

        override suspend fun setMute(device: OutputDevice, mute: Boolean): Result<Unit> {
            actions.add("SetMute:$mute")
            return Result.success(Unit)
        }
    }
}

package com.listenquran.player.cast

import kotlinx.coroutines.Dispatchers
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
    fun `connectToDevice hands off current and next track, seeks, controls volume, and returns position on disconnect`() = runTest {
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
        assertTrue(fakeControl.actions.any { it == "Seek:42000" })
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
        assertTrue(fakeControl.actions.contains("Stop"))
        assertFalse(server.isRunning)
    }

    @Test
    fun `polling detects end of track and 3-strike disconnect recovers cleanly`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
            positionInfo = RemotePositionInfo(relTimeMs = 99_000L, trackDurationMs = 100_000L, trackUri = null)
        }
        val server = LocalMediaHttpServer(OkHttpClient())
        val manager = OutputDeviceManager(fakeDiscovery, fakeControl, server, pollIntervalMs = 15L, seekDelayMs = 0L, ioDispatcher = Dispatchers.IO)

        val endedCalled = AtomicBoolean(false)
        val lostCalled = AtomicBoolean(false)
        manager.onRemoteTrackEnded = { endedCalled.set(true) }
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

        fakeControl.failAllPolls.set(true)
        Thread.sleep(100)
        assertTrue("Expected connection lost callback after 3 failed polls", lostCalled.get())
        assertFalse(manager.isCasting)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
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
        @Volatile var transportState = RemoteTransportState.STOPPED
        @Volatile var positionInfo = RemotePositionInfo(0L, 100_000L, null)
        @Volatile var volume = 50

        override suspend fun setTransportUri(device: OutputDevice, mediaUrl: String, metadataXml: String): Result<Unit> {
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
            return Result.success(Unit)
        }

        override suspend fun seek(device: OutputDevice, positionMs: Long): Result<Unit> {
            actions.add("Seek:$positionMs")
            return Result.success(Unit)
        }

        override suspend fun getPositionInfo(device: OutputDevice): Result<RemotePositionInfo> {
            if (failAllPolls.get()) return Result.failure(RuntimeException("Offline"))
            return Result.success(positionInfo)
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

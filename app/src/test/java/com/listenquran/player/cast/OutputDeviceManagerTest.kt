package com.listenquran.player.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OutputDeviceManagerTest {

    private val testSpeaker = OutputDevice(
        id = "uuid:speaker-1",
        name = "Living Room",
        protocol = CastProtocol.DLNA,
        deviceType = OutputDeviceType.SPEAKER,
        avTransportControlUrl = "http://127.0.0.1:49494/avt",
        renderingControlUrl = "http://127.0.0.1:49494/rc",
        hostAddress = "192.168.0.225",
    )

    @Test
    fun `scanForDevices populates sorted discovered devices`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient()
        val hub = PcmStreamHub()
        val server = LocalMediaHttpServer(hub)
        val manager = OutputDeviceManager(
            discoveryClient = fakeDiscovery,
            controlClient = fakeControl,
            mediaServer = server,
            pcmStreamHub = hub,
            pollIntervalMs = 20L,
            connectPollIntervalMs = 10L,
            ioDispatcher = Dispatchers.IO,
        )

        manager.scanForDevices()
        assertFalse(manager.routeState.value.isScanning)
        assertEquals(listOf(testSpeaker), manager.routeState.value.discoveredDevices)
        manager.disconnect()
    }

    @Test
    fun `connectToDevice pushes live WAV URL, controls volume, and disconnects cleanly`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
            positionInfo = RemotePositionInfo(relTimeMs = 100L, trackDurationMs = 0L, trackUri = null)
            volume = 68
        }
        val hub = PcmStreamHub()
        val server = LocalMediaHttpServer(hub)
        val manager = OutputDeviceManager(
            discoveryClient = fakeDiscovery,
            controlClient = fakeControl,
            mediaServer = server,
            pcmStreamHub = hub,
            pollIntervalMs = 20L,
            connectPollIntervalMs = 5L,
            ioDispatcher = Dispatchers.IO,
        )

        val res = manager.connectToDevice(
            testSpeaker,
            LiveTrackMetadata("36. Ya-Sin", "Mishari"),
            autoPlay = true,
        )
        assertTrue(res.isSuccess)
        assertTrue(manager.isCasting)
        assertEquals(68, manager.routeState.value.remoteVolume)
        assertTrue(fakeControl.actions.any { it.startsWith("SetAVTransportURI:") && it.contains("/live/") && it.endsWith(".wav") })
        assertTrue(fakeControl.actions.contains("Play"))

        manager.setRemoteVolume(80)
        assertEquals(80, manager.routeState.value.remoteVolume)
        manager.toggleRemoteMute()
        assertTrue(manager.routeState.value.isRemoteMuted)

        val delayEst = manager.disconnect()
        assertTrue(delayEst >= 0L)
        assertFalse(manager.isCasting)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
        Thread.sleep(40)
        assertTrue(fakeControl.actions.contains("Stop"))
        assertFalse(server.isRunning)
        assertFalse(hub.isSessionOpen)
    }

    @Test
    fun `falls back to L16 stream when renderer rejects WAV with fault 714`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            rejectWavWith714 = true
            transportState = RemoteTransportState.PLAYING
        }
        val hub = PcmStreamHub()
        val server = LocalMediaHttpServer(hub)
        val manager = OutputDeviceManager(
            discoveryClient = fakeDiscovery,
            controlClient = fakeControl,
            mediaServer = server,
            pcmStreamHub = hub,
            pollIntervalMs = 20L,
            connectPollIntervalMs = 5L,
            ioDispatcher = Dispatchers.IO,
        )

        val res = manager.connectToDevice(testSpeaker)
        assertTrue(res.isSuccess)
        assertTrue(fakeControl.actions.any { it.endsWith(".l16") })
        manager.disconnect()
    }

    @Test
    fun `disconnect cancels a stalled connectToDevice immediately without deadlocking`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            setUriDelayMs = 5_000L
        }
        val hub = PcmStreamHub()
        val server = LocalMediaHttpServer(hub)
        val manager = OutputDeviceManager(
            discoveryClient = fakeDiscovery,
            controlClient = fakeControl,
            mediaServer = server,
            pcmStreamHub = hub,
            pollIntervalMs = 20L,
            connectPollIntervalMs = 10L,
            connectTimeoutMs = 8_000L,
            ioDispatcher = Dispatchers.IO,
        )

        val connectJob = launch(Dispatchers.IO) {
            runCatching { manager.connectToDevice(testSpeaker) }
        }
        Thread.sleep(60)
        assertTrue(manager.routeState.value.isConnecting)

        val t0 = System.currentTimeMillis()
        manager.disconnect()
        val elapsedMs = System.currentTimeMillis() - t0

        assertTrue("disconnect() took ${elapsedMs}ms", elapsedMs < 1_000L)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
        assertEquals(CastConnectionState.DISCONNECTED, manager.routeState.value.connectionState)
        assertFalse(server.isRunning)
        connectJob.join()
    }

    @Test
    fun `unexpected renderer stop recovers once then fires onRemoteConnectionLost`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
        }
        val hub = PcmStreamHub()
        val server = LocalMediaHttpServer(hub)
        val manager = OutputDeviceManager(
            discoveryClient = fakeDiscovery,
            controlClient = fakeControl,
            mediaServer = server,
            pcmStreamHub = hub,
            pollIntervalMs = 15L,
            connectPollIntervalMs = 5L,
            ioDispatcher = Dispatchers.IO,
        )

        val lostCalled = AtomicBoolean(false)
        manager.onRemoteConnectionLost = { _, _ -> lostCalled.set(true) }

        assertTrue(manager.connectToDevice(testSpeaker).isSuccess)
        val initialSetUriCount = fakeControl.actions.count { it.startsWith("SetAVTransportURI:") }

        fakeControl.transportState = RemoteTransportState.STOPPED
        Thread.sleep(90)

        assertTrue("Expected one recovery SetAVTransportURI attempt", fakeControl.actions.count { it.startsWith("SetAVTransportURI:") } > initialSetUriCount)
        assertTrue("Expected onRemoteConnectionLost after second STOPPED poll", lostCalled.get())
        assertFalse(manager.isCasting)
        assertEquals(OutputDevice.LOCAL_PHONE, manager.routeState.value.activeDevice)
    }

    @Test
    fun `3 consecutive failed transport polls skip GetPositionInfo and trigger connection lost`() = runTest {
        val fakeDiscovery = FakeDiscoveryClient(listOf(testSpeaker))
        val fakeControl = FakeRemoteControlClient().apply {
            transportState = RemoteTransportState.PLAYING
        }
        val hub = PcmStreamHub()
        val server = LocalMediaHttpServer(hub)
        val manager = OutputDeviceManager(
            discoveryClient = fakeDiscovery,
            controlClient = fakeControl,
            mediaServer = server,
            pcmStreamHub = hub,
            pollIntervalMs = 15L,
            connectPollIntervalMs = 5L,
            ioDispatcher = Dispatchers.IO,
        )

        val lostCalled = AtomicBoolean(false)
        manager.onRemoteConnectionLost = { _, _ -> lostCalled.set(true) }

        assertTrue(manager.connectToDevice(testSpeaker).isSuccess)
        val posBeforeDrop = fakeControl.positionCalls.get()
        fakeControl.failAllPolls.set(true)
        Thread.sleep(100)

        assertTrue(lostCalled.get())
        assertEquals(posBeforeDrop, fakeControl.positionCalls.get())
        assertFalse(manager.isCasting)
    }

    private class FakeDiscoveryClient(
        private val devices: List<OutputDevice>,
    ) : DeviceDiscoveryClient {
        override suspend fun discoverDevices(timeoutMs: Long): List<OutputDevice> = devices
        override fun discoverDevicesFlow(timeoutMs: Long): Flow<OutputDevice> = flow {
            devices.forEach { emit(it) }
        }
        override fun passiveDiscoveryFlow(): Flow<SsdpPassiveEvent> = emptyFlow()
        override suspend fun verifyDevice(locationUrl: String): OutputDevice? = devices.firstOrNull()
        override fun getLocalLanIpv4Address(): InetAddress = InetAddress.getLoopbackAddress()
    }

    private class FakeRemoteControlClient : RemoteControlClient {
        val actions = CopyOnWriteArrayList<String>()
        val positionCalls = AtomicInteger(0)
        val failAllPolls = AtomicBoolean(false)

        @Volatile
        var transportState: RemoteTransportState = RemoteTransportState.PLAYING

        @Volatile
        var positionInfo: RemotePositionInfo = RemotePositionInfo(0L, 0L, null)

        @Volatile
        var volume: Int = 50

        @Volatile
        var setUriDelayMs: Long = 0L

        @Volatile
        var rejectWavWith714: Boolean = false

        override suspend fun setTransportUri(device: OutputDevice, mediaUrl: String, metadataXml: String): Result<Unit> {
            if (setUriDelayMs > 0L) delay(setUriDelayMs)
            actions.add("SetAVTransportURI:$mediaUrl")
            if (rejectWavWith714 && mediaUrl.endsWith(".wav")) {
                return Result.failure(IllegalStateException("DLNA SetAVTransportURI fault: Illegal MIME-type [code 714]"))
            }
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
            positionCalls.incrementAndGet()
            if (failAllPolls.get()) return Result.failure(IllegalStateException("Network down"))
            return Result.success(positionInfo)
        }

        override suspend fun getTransportInfo(device: OutputDevice): Result<RemoteTransportState> {
            if (failAllPolls.get()) return Result.failure(IllegalStateException("Network down"))
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

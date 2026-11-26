package com.music.bitchord.data.connect

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConnectModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun pairLinkRoundTripsUriAndPlainHost() {
        val link = ConnectPairLink(
            host = "192.168.1.42",
            port = 17420,
            pin = "482910",
            deviceId = "pc-abc-123",
            deviceName = "Living Room PC",
        )
        val uri = link.toUriString()
        val parsed = ConnectPairLink.parse(uri)
        assertEquals(link, parsed)

        assertEquals(
            ConnectPairLink(host = "100.64.0.10", port = 17420),
            ConnectPairLink.parse("100.64.0.10"),
        )
        assertEquals(
            ConnectPairLink(host = "192.168.1.8", port = 18000),
            ConnectPairLink.parse("http://192.168.1.8:18000/connect/info"),
        )
        assertNull(ConnectPairLink.parse("   "))
    }

    @Test
    fun playbackStateExtrapolatesPlayheadAndClamps() {
        val playing = ConnectPlaybackState(
            isPlaying = true,
            positionMs = 10_000L,
            anchorMs = 1_000_000L,
            durationMs = 15_000L,
        )
        assertEquals(12_500L, playing.positionAt(1_002_500L))
        assertEquals(15_000L, playing.positionAt(1_020_000L))
        assertEquals(10_000L, playing.positionAt(999_000L))

        val paused = playing.copy(isPlaying = false)
        assertEquals(10_000L, paused.positionAt(1_005_000L))

        val buffering = playing.copy(buffering = true)
        assertEquals(10_000L, buffering.positionAt(1_005_000L))
    }

    @Test
    fun framesSerializeAndDeserialize() {
        val track = ConnectTrack(videoId = "dQw4w9WgXcQ", title = "Song", artist = "Artist", durationMs = 213_000L)
        val cmd = ConnectClientFrame.control(
            action = ConnectProtocol.ACTION_LOAD_QUEUE,
            tracks = listOf(track),
            index = 0,
            positionMs = 5_000L,
            play = true,
            repeatMode = ConnectProtocol.REPEAT_ALL,
        )
        val decodedCmd = json.decodeFromString<ConnectClientFrame>(json.encodeToString(cmd))
        assertEquals(cmd, decodedCmd)

        val server = ConnectServerFrame(
            type = ConnectProtocol.FRAME_WELCOME,
            deviceId = "pc-1",
            deviceName = "My PC",
            serverMs = 123456789L,
            state = ConnectPlaybackState(seq = 2L, queueSeq = 1L, queueIndex = 0, track = track, isPlaying = true),
            queue = ConnectQueueState(seq = 1L, index = 0, items = listOf(track)),
        )
        val decodedServer = json.decodeFromString<ConnectServerFrame>(json.encodeToString(server))
        assertNotNull(decodedServer.state)
        assertEquals(server, decodedServer)
    }
}

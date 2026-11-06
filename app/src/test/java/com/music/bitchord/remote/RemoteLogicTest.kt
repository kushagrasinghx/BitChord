package com.music.bitchord.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The web remote's pure decisions: who queued what, which PIN a start
 * uses, how join names are cleaned, which page a browse card opens,
 * and how the relay address parses — the last of those also decides
 * whether the relay token travels encrypted.
 *
 * Worth their own tests because every one of them fails silently.
 * Attribution consuming out of order still renders a queue — just with
 * the wrong names on it. A PIN precedence slip still starts a server —
 * with a PIN the user did not choose. A browse-prefix slip still opens
 * a page — the wrong kind. None of it crashes, so only a test notices.
 */
class RemoteLogicTest {

    // ── attribution ─────────────────────────────────────────────────

    @Test
    fun attributionLabelsInQueueOrder() {
        val (labels, kept) = attributeIds(
            queueIds = listOf("a", "b"),
            entries = listOf("a" to "Ana", "b" to "Ben"),
        )
        assertEquals(listOf("Ana", "Ben"), labels)
        assertEquals(listOf("a" to "Ana", "b" to "Ben"), kept)
    }

    @Test
    fun attributionConsumesDuplicatesInTheOrderTheyWereAdded() {
        // The same song queued twice by two people: the first copy in
        // the queue belongs to whoever added it first.
        val (labels, kept) = attributeIds(
            queueIds = listOf("a", "x", "a"),
            entries = listOf("a" to "Ana", "a" to "Ben"),
        )
        assertEquals(listOf("Ana", null, "Ben"), labels)
        assertEquals(2, kept.size)
    }

    @Test
    fun attributionDropsEntriesWhoseSongLeftTheQueue() {
        // A kept entry for a song no longer queued would mislabel a
        // later re-add of the same song, so the walk drops it.
        val (labels, kept) = attributeIds(
            queueIds = listOf("b"),
            entries = listOf("a" to "Ana", "b" to "Ben"),
        )
        assertEquals(listOf("Ben"), labels)
        assertEquals(listOf("b" to "Ben"), kept)
    }

    @Test
    fun attributionSurvivesPastTheDisplayCap() {
        // The page shows at most 200 queue items, but the walk runs
        // over the whole queue: a label at position 204 is consumed
        // and kept, not pruned for being off-screen.
        val ids = (0 until 205).map { "v$it" }
        val (labels, kept) = attributeIds(ids, listOf("v204" to "Zed"))
        assertEquals("Zed", labels[204])
        assertEquals(listOf("v204" to "Zed"), kept)
    }

    @Test
    fun attributionOfAnUnlabelledQueueIsAllNull() {
        val (labels, kept) = attributeIds(listOf("a", "b"), emptyList())
        assertEquals(listOf(null, null), labels)
        assertEquals(emptyList<Pair<String, String>>(), kept)
    }

    // ── PIN choice ──────────────────────────────────────────────────

    @Test
    fun customPinAlwaysWins() {
        assertEquals("9876", pickPin(custom = "9876", persistent = true, saved = "1234"))
        assertEquals("9876", pickPin(custom = "9876", persistent = false, saved = ""))
    }

    @Test
    fun savedPinIsUsedOnlyWhenPersistent() {
        assertEquals("1234", pickPin(custom = "", persistent = true, saved = "1234"))
        assertNull(pickPin(custom = "", persistent = false, saved = "1234"))
    }

    @Test
    fun noPinAnywhereMeansGenerateAFreshOne() {
        assertNull(pickPin(custom = "", persistent = true, saved = ""))
        assertNull(pickPin(custom = "", persistent = false, saved = ""))
    }

    // ── name sanitizing ─────────────────────────────────────────────

    @Test
    fun namesAreTrimmedAndCapped() {
        assertEquals("Ana", sanitizeRemoteName("  Ana  "))
        assertEquals(24, sanitizeRemoteName("a".repeat(40)).length)
    }

    @Test
    fun namesLoseControlCharacters() {
        assertEquals("AB", sanitizeRemoteName("A\u0007B"))
    }

    @Test
    fun blankNamesJoinAsGuest() {
        assertEquals("Guest", sanitizeRemoteName(null))
        assertEquals("Guest", sanitizeRemoteName(""))
        assertEquals("Guest", sanitizeRemoteName("   "))
    }

    // ── browse kind resolution ──────────────────────────────────────

    @Test
    fun explicitKindWinsOverPrefixes() {
        assertEquals("artist", resolveBrowseKind("VLPL123", "artist"))
        assertEquals("album", resolveBrowseKind("VLPL123", "album"))
    }

    @Test
    fun artistPrefixIsCheckedBeforeTheAlbumKind() {
        // The app's own classification order: the artist branch comes
        // first, so a UC-prefixed id is an artist even if a caller
        // passes kind "album". Search results never pair the two —
        // kinds and ids agree there — but the order is the app's, so
        // the remote keeps it and this test pins it.
        assertEquals("artist", resolveBrowseKind("UCabc", "album"))
    }

    @Test
    fun browsePrefixesClassifyLikeTheApp() {
        assertEquals("artist", resolveBrowseKind("UCabc", ""))
        assertEquals("album", resolveBrowseKind("MPREb123", ""))
        assertEquals("album", resolveBrowseKind("OLAK123", ""))
        assertEquals("playlist", resolveBrowseKind("VLPL123", ""))
        assertEquals("playlist", resolveBrowseKind("PL123", ""))
        assertEquals("playlist", resolveBrowseKind("RD123", ""))
    }

    @Test
    fun albumWrapperPrefixBeatsThePlaylistPrefix() {
        // VLOLAK is an album delivered in a playlist-shaped id; the
        // whole point of the ordering is that it is not a playlist.
        assertEquals("album", resolveBrowseKind("VLOLAK123", ""))
    }

    // ── relay address ───────────────────────────────────────────────
    // The parser decides whether the relay token crosses the network
    // encrypted. A flipped default here would not crash anything —
    // it would silently send the token in plaintext — so the whole
    // scheme matrix is pinned.

    @Test
    fun bareHostnameIsTlsOn443() {
        assertEquals(
            RemoteRelay.Address("remote.gaspol.men", 443, true),
            RemoteRelay.parseAddress("remote.gaspol.men"),
        )
    }

    @Test
    fun pastedHttpsUrlIsTlsAndDropsThePath() {
        assertEquals(
            RemoteRelay.Address("remote.gaspol.men", 443, true),
            RemoteRelay.parseAddress("https://remote.gaspol.men/r/abc123/"),
        )
    }

    @Test
    fun wsSchemeIsPlaintextOn80() {
        assertEquals(
            RemoteRelay.Address("relay.example.com", 80, false),
            RemoteRelay.parseAddress("ws://relay.example.com"),
        )
    }

    @Test
    fun wsUppercaseSchemeKeepsTheExplicitPort() {
        assertEquals(
            RemoteRelay.Address("103.103.23.1", 8890, false),
            RemoteRelay.parseAddress("WS://103.103.23.1:8890"),
        )
    }

    @Test
    fun httpSchemeIsPlaintextToo() {
        assertEquals(
            RemoteRelay.Address("192.168.1.5", 8890, false),
            RemoteRelay.parseAddress("http://192.168.1.5:8890"),
        )
    }

    @Test
    fun wssKeepsItsExplicitPort() {
        assertEquals(
            RemoteRelay.Address("relay.example.com", 8443, true),
            RemoteRelay.parseAddress("wss://relay.example.com:8443"),
        )
    }

    @Test
    fun explicitPortWithoutSchemeStaysTls() {
        assertEquals(
            RemoteRelay.Address("relay.example.com", 8443, true),
            RemoteRelay.parseAddress("relay.example.com:8443"),
        )
    }

    @Test
    fun unknownSchemeStaysTls() {
        assertEquals(
            RemoteRelay.Address("relay.example.com", 443, true),
            RemoteRelay.parseAddress("gopher://relay.example.com"),
        )
    }

    @Test
    fun hostIsLowercasedAndWhitespaceTrimmed() {
        assertEquals(
            RemoteRelay.Address("relay.example.com", 443, true),
            RemoteRelay.parseAddress("  WSS://Relay.Example.COM  "),
        )
    }

    @Test
    fun blankFallsBackToTheFullDefaults() {
        val defaults = RemoteRelay.Address("remote.gaspol.men", 443, true)
        assertEquals(defaults, RemoteRelay.parseAddress(""))
        assertEquals(defaults, RemoteRelay.parseAddress("   "))
    }

    @Test
    fun schemeOnlyInputFallsBackToTheFullDefaults() {
        // A stray "ws://" must not pair the default host with a
        // plaintext choice the user never made.
        assertEquals(
            RemoteRelay.Address("remote.gaspol.men", 443, true),
            RemoteRelay.parseAddress("ws://"),
        )
    }

    // ── mux framing ─────────────────────────────────────────────────
    // Every request and response chunk on a relay channel wears this
    // five-byte header. A slip here does not crash anything — it
    // delivers one browser's response to another — so the layout is
    // pinned byte for byte.

    @Test
    fun muxRoundTripPreservesIdTypeAndPayload() {
        val payload = "hello".toByteArray()
        val decoded = RemoteRelay.muxDecode(
            RemoteRelay.muxEncode(7, RemoteRelay.MUX_DATA, payload),
        )!!
        assertEquals(7, decoded.id)
        assertEquals(RemoteRelay.MUX_DATA, decoded.type)
        assertEquals("hello", String(decoded.payload))
    }

    @Test
    fun muxHeaderIsBigEndianIdThenType() {
        val frame = RemoteRelay.muxEncode(0x01020304, RemoteRelay.MUX_END, ByteArray(0))
        assertEquals(5, frame.size)
        assertEquals(0x01.toByte(), frame[0])
        assertEquals(0x02.toByte(), frame[1])
        assertEquals(0x03.toByte(), frame[2])
        assertEquals(0x04.toByte(), frame[3])
        assertEquals(RemoteRelay.MUX_END.toByte(), frame[4])
    }

    @Test
    fun muxEmptyPayloadDecodesToTheBareHeader() {
        val decoded = RemoteRelay.muxDecode(
            RemoteRelay.muxEncode(42, RemoteRelay.MUX_REQUEST, ByteArray(0)),
        )!!
        assertEquals(42, decoded.id)
        assertEquals(RemoteRelay.MUX_REQUEST, decoded.type)
        assertEquals(0, decoded.payload.size)
    }

    @Test
    fun muxDecodeRejectsShortMessages() {
        assertNull(RemoteRelay.muxDecode(ByteArray(0)))
        assertNull(RemoteRelay.muxDecode(ByteArray(4)))
    }
}

package com.music.bitchord

import com.music.bitchord.data.webdav.WebDavClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * PROPFIND responses are XML chosen by the server, so they are parsed as
 * untrusted input. Before the parser was hardened, an external entity in a
 * listing response was resolved against the device and its contents came back
 * as a `displayname` — a file read dressed up as a folder name.
 */
class WebDavXxeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dir = "https://dav.example.com/music"

    @Test
    fun `external entity is never resolved into a listing`() {
        val secret = temp.newFile("secret.txt").apply { writeText("BitChord-LOCAL-FILE-LEAK") }

        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE multistatus [ <!ENTITY xxe SYSTEM "${secret.toURI()}"> ]>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/music/song.mp3</d:href>
                <d:displayname>&xxe;</d:displayname>
                <d:getcontenttype>audio/mpeg</d:getcontenttype>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavClient.parseMultistatus(xml, dir)

        // Either the document is refused (empty) or the entity is left
        // unexpanded — what must not happen is the file being read.
        assertTrue(
            "an external entity must not become an entry: $entries",
            entries.none { it.displayName.contains("LOCAL-FILE-LEAK") },
        )
        assertTrue("a document with a DOCTYPE must be refused", entries.isEmpty())
    }

    @Test
    fun `nested entity expansion cannot be used to exhaust the parser`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE lolz [
              <!ENTITY lol "lol">
              <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
              <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
              <!ENTITY lol4 "&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;">
            ]>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/music/song.mp3</d:href>
                <d:displayname>&lol4;</d:displayname>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        assertTrue("an expanding-entity document must be refused", WebDavClient.parseMultistatus(xml, dir).isEmpty())
    }

    @Test
    fun `an ordinary multistatus response still parses`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/music/</d:href>
                <d:displayname>music</d:displayname>
                <d:resourcetype><d:collection/></d:resourcetype>
              </d:response>
              <d:response>
                <d:href>/music/Artist%20-%20Title.flac</d:href>
                <d:displayname>Artist - Title.flac</d:displayname>
                <d:getcontenttype>audio/flac</d:getcontenttype>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavClient.parseMultistatus(xml, dir)

        assertEquals(1, entries.size)
        assertEquals("Artist - Title.flac", entries.first().displayName)
        assertEquals("audio/flac", entries.first().contentType)
    }
}

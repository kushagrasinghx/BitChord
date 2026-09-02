package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the channels a login can act as out of an account-switcher response.
 *
 * Pinned because the two things each row must yield — the `pageId` header and
 * the `datasyncIdToken` body value — arrive as single-key objects in a list
 * Google never promised an order for, alongside tokens meant for other
 * purposes. A positional read of that list works right up until Google adds a
 * token, and then it silently switches the listener to a channel that isn't
 * the one they tapped.
 *
 * The fixtures are trimmed copies of the shape both `account/accounts_list`
 * and `getAccountSwitcherEndpoint` return: the same `accountItem` renderer in
 * different envelopes, which is why the parser scans for the item rather than
 * walking a path to it.
 */
class AccountChannelTest {

    private fun parse(json: String) =
        InnertubeParser.parseAccountChannels(Json.parseToJsonElement(json))

    @Test
    fun `personal channel and brand channel are both read`() {
        val channels = parse(SWITCHER)

        assertEquals(2, channels.size)

        val personal = channels[0]
        assertEquals("Ada Lovelace", personal.name)
        assertEquals("@ada", personal.subtitle)
        // Not a delegated page, and must not be given one — a `X-Goog-PageId`
        // on the account's own channel is a different request entirely.
        assertNull(personal.pageId)
        assertEquals("SYNC_PERSONAL", personal.dataSyncId)
        assertTrue(personal.activeOnWeb)

        val brand = channels[1]
        assertEquals("Analytical Engine Radio", brand.name)
        assertEquals("113355", brand.pageId)
        assertEquals("SYNC_BRAND", brand.dataSyncId)
        assertEquals(false, brand.activeOnWeb)
    }

    @Test
    fun `token order is not assumed`() {
        // Same brand channel, tokens listed the other way round and with an
        // unrelated one in front of both.
        val channels = parse(SHUFFLED_TOKENS)
        assertEquals(1, channels.size)
        assertEquals("113355", channels[0].pageId)
        assertEquals("SYNC_BRAND", channels[0].dataSyncId)
    }

    @Test
    fun `only the account half of the datasync id is kept`() {
        // `<accountSyncId>||<sessionSyncId>` — the second half changes on its
        // own schedule and names the session, not the account.
        assertEquals("SYNC_BRAND", parse(SHUFFLED_TOKENS)[0].dataSyncId)
    }

    @Test
    fun `an entry with nothing to send is dropped`() {
        // Offering a channel that would quietly leave the current one selected
        // is worse than not listing it.
        assertTrue(parse(NO_TOKENS).isEmpty())
    }

    @Test
    fun `an unrecognised envelope yields nothing rather than throwing`() {
        assertTrue(parse("""{"responseContext":{},"contents":{}}""").isEmpty())
    }

    private companion object {
        const val SWITCHER = """
        {"data":{"actions":[{"getMultiPageMenuAction":{"menu":{"multiPageMenuRenderer":{
          "sections":[{"accountSectionListRenderer":{"contents":[{"accountItemSectionRenderer":{
            "contents":[
              {"accountItem":{
                "accountName":{"simpleText":"Ada Lovelace"},
                "accountPhoto":{"thumbnails":[{"url":"https://x/s40","width":40}]},
                "isSelected":true,
                "channelHandle":{"simpleText":"@ada"},
                "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                  {"accountSigninToken":{"signinUrl":"https://accounts.google.com/x"}},
                  {"datasyncIdToken":{"datasyncIdToken":"SYNC_PERSONAL||SESSION_A"}}
                ]}}
              }},
              {"accountItem":{
                "accountName":{"runs":[{"text":"Analytical Engine Radio"}]},
                "accountPhoto":{"thumbnails":[{"url":"https://y/s40","width":40}]},
                "isSelected":false,
                "accountByline":{"simpleText":"Brand account"},
                "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                  {"pageIdToken":{"pageId":"113355"}},
                  {"datasyncIdToken":{"datasyncIdToken":"SYNC_BRAND||SESSION_B"}}
                ]}}
              }}
            ]}}]}}]}}}}]}}
        """

        const val SHUFFLED_TOKENS = """
        {"contents":[{"accountItem":{
          "accountName":{"simpleText":"Analytical Engine Radio"},
          "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
            {"offlineCacheKeyToken":{"clientCacheKey":"CACHE"}},
            {"datasyncIdToken":{"datasyncIdToken":"SYNC_BRAND||SESSION_B"}},
            {"pageIdToken":{"pageId":"113355"}}
          ]}}
        }}]}
        """

        const val NO_TOKENS = """
        {"contents":[{"accountItem":{
          "accountName":{"simpleText":"Ada Lovelace"},
          "serviceEndpoint":{"signalServiceEndpoint":{"signal":"CLIENT_SIGNAL"}}
        }}]}
        """
    }
}

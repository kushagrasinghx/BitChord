package com.music.bitchord.data.innertube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The identity a request may claim. The regression these guard: a stored
 * channel outlived its cookie, and the app kept sending `onBehalfOfUser` from
 * it after the shell had gone signed-out — which Google answers 401 on every
 * call, Home included.
 */
class InnertubeIdentityTest {

    private val picked = Innertube.ChannelSelection(
        pageId = "BRAND_PAGE",
        dataSyncId = "BRAND_SYNC",
        authUser = "1",
    )

    @Test
    fun signedOutShellClaimsNothing() {
        val identity = Innertube.requestIdentity(
            shellSignedIn = false,
            shellDataSyncId = null,
            shellPageId = null,
            shellAuthUser = "0",
            chosen = picked,
        )
        assertNull(identity.dataSyncId)
        assertNull(identity.pageId)
        assertEquals("0", identity.authUser)
    }

    @Test
    fun unreadShellClaimsNothing() {
        // ensureSessionScope leaves the scope null when the shell cannot be
        // fetched. Guessing a stored channel there is the same 401 by another
        // route.
        val identity = Innertube.requestIdentity(
            shellSignedIn = false,
            shellDataSyncId = "STORED_SYNC",
            shellPageId = "STORED_PAGE",
            shellAuthUser = "1",
            chosen = picked,
        )
        assertNull(identity.dataSyncId)
        assertNull(identity.pageId)
        assertEquals("0", identity.authUser)
    }

    @Test
    fun signedInShellUsesThePickedChannel() {
        val identity = Innertube.requestIdentity(
            shellSignedIn = true,
            shellDataSyncId = "ACCOUNT_SYNC",
            shellPageId = null,
            shellAuthUser = "0",
            chosen = picked,
        )
        assertEquals("BRAND_SYNC", identity.dataSyncId)
        assertEquals("BRAND_PAGE", identity.pageId)
        assertEquals("1", identity.authUser)
    }

    @Test
    fun signedInShellWithoutAChoiceUsesTheShellsIdentity() {
        val identity = Innertube.requestIdentity(
            shellSignedIn = true,
            shellDataSyncId = "ACCOUNT_SYNC",
            shellPageId = "PAGE",
            shellAuthUser = "2",
            chosen = null,
        )
        assertEquals("ACCOUNT_SYNC", identity.dataSyncId)
        assertEquals("PAGE", identity.pageId)
        assertEquals("2", identity.authUser)
    }

    @Test
    fun aChoiceWithNoAuthUserKeepsTheShellsIndex() {
        val identity = Innertube.requestIdentity(
            shellSignedIn = true,
            shellDataSyncId = "ACCOUNT_SYNC",
            shellPageId = null,
            shellAuthUser = "3",
            chosen = Innertube.ChannelSelection(pageId = null, dataSyncId = "BRAND_SYNC"),
        )
        assertEquals("BRAND_SYNC", identity.dataSyncId)
        assertEquals("3", identity.authUser)
    }
}

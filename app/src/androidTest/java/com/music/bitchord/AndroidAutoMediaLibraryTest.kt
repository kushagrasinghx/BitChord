package com.music.bitchord

import android.content.ComponentName
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoMediaLibraryTest {

    private lateinit var mediaBrowser: MediaBrowser

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val latch = CountDownLatch(1)
        var browserInstance: MediaBrowser? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val future = MediaBrowser.Builder(context, token).buildAsync()
            future.addListener(
                {
                    browserInstance = future.get()
                    latch.countDown()
                },
                { command -> command.run() },
            )
        }

        assertTrue("MediaBrowser connection timed out", latch.await(10, TimeUnit.SECONDS))
        assertNotNull("MediaBrowser instance must not be null", browserInstance)
        mediaBrowser = browserInstance!!
    }

    @After
    fun tearDown() {
        if (::mediaBrowser.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                mediaBrowser.release()
            }
        }
    }

    @Test
    fun testGetLibraryRoot() = runBlocking {
        val rootResult = withContext(Dispatchers.Main) {
            mediaBrowser.getLibraryRoot(/* params = */ null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull("Library root result must not be null", rootResult)
        assertNotNull("Library root item must not be null", rootResult.value)
        assertEquals("root", rootResult.value?.mediaId)
        assertTrue("Root must be browsable", rootResult.value?.mediaMetadata?.isBrowsable == true)
    }

    @Test
    fun testGetTopLevelChildren() = runBlocking {
        val childrenResult = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("root", /* page = */ 0, /* pageSize = */ 20, /* params = */ null)
                .get(10, TimeUnit.SECONDS)
        }
        assertNotNull("Children result must not be null", childrenResult)
        val items = childrenResult.value
        assertNotNull("Children list must not be null", items)
        assertTrue("Top-level children must not be empty", items!!.isNotEmpty())

        // Pinned root contract: exactly these 5 folders, in this order.
        val expectedRootOrder = listOf("quick_picks", "recents", "playlists", "liked", "more")
        val mediaIds = items.map { it.mediaId }
        assertEquals("Root must expose exactly the 5 pinned folders", expectedRootOrder, mediaIds)
        items.forEach {
            assertTrue("Root child ${it.mediaId} must be browsable", it.mediaMetadata?.isBrowsable == true)
        }
    }

    @Test
    fun testGetItem() = runBlocking {
        val expectedIds = listOf("quick_picks", "recents", "playlists", "liked", "more")
        for (id in expectedIds) {
            val itemResult = withContext(Dispatchers.Main) {
                mediaBrowser.getItem(id).get(10, TimeUnit.SECONDS)
            }
            assertNotNull("Item result for $id must not be null", itemResult)
            assertNotNull("Item for $id must not be null", itemResult.value)
            assertEquals(id, itemResult.value?.mediaId)
        }
    }

    @Test
    fun testPaginationDoesNotCrash() = runBlocking {
        val firstPage = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("root", /* page = */ 0, /* pageSize = */ 2, /* params = */ null)
                .get(10, TimeUnit.SECONDS)
        }
        assertNotNull("First page result must not be null", firstPage)
        val items = firstPage.value
        assertNotNull("First page list must not be null", items)
        assertTrue("Page size must be honored (<= 2 items)", items!!.size <= 2)

        val outOfRange = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("root", /* page = */ 99, /* pageSize = */ 20, /* params = */ null)
                .get(10, TimeUnit.SECONDS)
        }
        assertNotNull("Out-of-range page result must not be null", outOfRange)
        assertNotNull("Out-of-range page list must not be null", outOfRange.value)
        assertTrue("Out-of-range page must be empty", outOfRange.value!!.isEmpty())
    }

    @Test
    fun testSearchAndGetSearchResult() = runBlocking {
        val searchLatch = CountDownLatch(1)
        var resultCount = 0

        val listener = object : MediaBrowser.Listener {
            override fun onSearchResultChanged(
                browser: MediaBrowser,
                query: String,
                itemCount: Int,
                params: androidx.media3.session.MediaLibraryService.LibraryParams?,
            ) {
                if (query == "Adele") {
                    resultCount = itemCount
                    searchLatch.countDown()
                }
            }
        }

        withContext(Dispatchers.Main) {
            mediaBrowser.search("Adele", null)
        }

        // Wait for search result notification
        searchLatch.await(10, TimeUnit.SECONDS)

        val results = withContext(Dispatchers.Main) {
            mediaBrowser.getSearchResult("Adele", 0, 10, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull("Search result should not be null", results)
        assertNotNull("Search result items should not be null", results.value)
    }
}

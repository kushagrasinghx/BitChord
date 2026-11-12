package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.shelfLayoutFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the rendered Home feed shelves to local storage so the TV and mobile UI
 * can render instantly on cold start without waiting for network round-trips.
 */
object HomeFeedCache {
    private const val FILE_NAME = "home_feed_cache.json"

    suspend fun read(context: Context): List<HomeShelf>? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, FILE_NAME)
            if (!file.exists() || file.length() == 0L) return@runCatching null
            val text = file.readText()
            val array = JSONArray(text)
            val shelves = mutableListOf<HomeShelf>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "")
                val subtitle = obj.optString("subtitle", "")
                val moreBrowseId = obj.optString("moreBrowseId").takeIf { it.isNotBlank() }
                val moreParams = obj.optString("moreParams").takeIf { it.isNotBlank() }
                val layoutStr = obj.optString("layout", "")
                val itemsArray = obj.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<ShelfItem>()
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    items.add(
                        ShelfItem(
                            title = itemObj.optString("title", ""),
                            subtitle = itemObj.optString("subtitle", ""),
                            thumbnailUrl = itemObj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                            videoId = itemObj.optString("videoId").takeIf { it.isNotBlank() },
                            browseId = itemObj.optString("browseId").takeIf { it.isNotBlank() },
                            isVideo = itemObj.optBoolean("isVideo", false),
                        )
                    )
                }
                val layout = if (layoutStr.isNotBlank()) {
                    runCatching { ShelfLayout.valueOf(layoutStr) }.getOrDefault(ShelfLayout.CARDS)
                } else {
                    shelfLayoutFor(title, items, fromList = false)
                }
                if (title.isNotBlank() && items.isNotEmpty()) {
                    shelves.add(
                        HomeShelf(
                            title = title,
                            items = items,
                            subtitle = subtitle,
                            moreBrowseId = moreBrowseId,
                            moreParams = moreParams,
                            layout = layout,
                        )
                    )
                }
            }
            shelves.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    suspend fun write(context: Context, shelves: List<HomeShelf>) = withContext(Dispatchers.IO) {
        if (shelves.isEmpty()) return@withContext
        runCatching {
            val array = JSONArray()
            for (shelf in shelves) {
                val obj = JSONObject()
                obj.put("title", shelf.title)
                obj.put("subtitle", shelf.subtitle)
                obj.put("layout", shelf.layout.name)
                shelf.moreBrowseId?.let { obj.put("moreBrowseId", it) }
                shelf.moreParams?.let { obj.put("moreParams", it) }
                val itemsArray = JSONArray()
                for (item in shelf.items) {
                    val itemObj = JSONObject()
                    itemObj.put("title", item.title)
                    itemObj.put("subtitle", item.subtitle)
                    itemObj.put("isVideo", item.isVideo)
                    item.thumbnailUrl?.let { itemObj.put("thumbnailUrl", it) }
                    item.videoId?.let { itemObj.put("videoId", it) }
                    item.browseId?.let { itemObj.put("browseId", it) }
                    itemsArray.put(itemObj)
                }
                obj.put("items", itemsArray)
                array.put(obj)
            }
            val file = File(context.cacheDir, FILE_NAME)
            file.writeText(array.toString())
        }
    }
}

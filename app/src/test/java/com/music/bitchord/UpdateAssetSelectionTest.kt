package com.music.bitchord

import com.music.bitchord.data.AppUpdateChecker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateAssetSelectionTest {

    private val release = Json.parseToJsonElement(
        """
        {"assets":[
          {"name":"BitChord-v1.9.1.apk","browser_download_url":"https://x/universal","state":"uploaded","digest":"sha256:${"a".repeat(64)}","size":100},
          {"name":"BitChord-v1.9.1_arm64-v8a.apk","browser_download_url":"https://x/arm64","state":"uploaded","digest":"sha256:${"b".repeat(64)}","size":70},
          {"name":"BitChord-v1.9.1_armeabi-v7a.apk","browser_download_url":"https://x/arm","state":"uploaded","digest":"sha256:${"c".repeat(64)}","size":60},
          {"name":"BitChordTV-v1.9.1.apk","browser_download_url":"https://x/tv","state":"uploaded","digest":"sha256:${"d".repeat(64)}","size":90}
        ]}
        """.trimIndent(),
    ).jsonObject

    @Test
    fun `the device ABI split wins over the universal APK`() {
        val asset = AppUpdateChecker.selectUpdateApk(release, listOf("arm64-v8a", "armeabi-v7a"), "BitChord-")
        assertEquals("BitChord-v1.9.1_arm64-v8a.apk", asset?.name)
        assertEquals("https://x/arm64", asset?.url)
        assertEquals("b".repeat(64), asset?.sha256)
        assertEquals(70L, asset?.size)
    }

    @Test
    fun `the universal APK is the fallback when no ABI matches`() {
        val asset = AppUpdateChecker.selectUpdateApk(release, listOf("x86_64"), "BitChord-")
        assertEquals("BitChord-v1.9.1.apk", asset?.name)
    }

    @Test
    fun `the TV APK is never selected by the phone prefix`() {
        val asset = AppUpdateChecker.selectUpdateApk(release, listOf("arm64-v8a"), "BitChord-")
        assertEquals("BitChord-v1.9.1_arm64-v8a.apk", asset?.name)
        assertNull(AppUpdateChecker.selectUpdateApk(release, emptyList(), "NoSuchPrefix-"))
    }
}

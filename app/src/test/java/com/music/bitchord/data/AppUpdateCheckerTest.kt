package com.music.bitchord.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [AppUpdateChecker] selects the exact APK filenames emitted by the
 * phone-only release workflow (`BitVinyl-prod-<abi>-release.apk` /
 * `BitVinyl-prod-universal-release.apk`) and excludes legacy TV/Wear, debug,
 * and unsigned artifacts.
 */
class AppUpdateCheckerTest {

    private val workflowReleaseAssets = listOf(
        "BitVinylTV-prod-universal-release.apk" to "https://example.com/BitVinylTV-prod-universal-release.apk",
        "BitVinylWear-prod-release.apk" to "https://example.com/BitVinylWear-prod-release.apk",
        "BitVinyl-prod-arm64-v8a-release.apk" to "https://example.com/BitVinyl-prod-arm64-v8a-release.apk",
        "BitVinyl-prod-armeabi-v7a-release.apk" to "https://example.com/BitVinyl-prod-armeabi-v7a-release.apk",
        "BitVinyl-prod-universal-release.apk" to "https://example.com/BitVinyl-prod-universal-release.apk",
        "BitVinyl-prod-x86_64-release.apk" to "https://example.com/BitVinyl-prod-x86_64-release.apk",
    )

    @Test
    fun `matches arm64-v8a workflow APK first for 64-bit phones`() {
        val url = AppUpdateChecker.selectApkAssetUrl(
            assets = workflowReleaseAssets,
            supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi"),
            prefix = "BitVinyl-",
        )
        assertEquals("https://example.com/BitVinyl-prod-arm64-v8a-release.apk", url)
    }

    @Test
    fun `matches armeabi-v7a workflow APK for 32-bit phones`() {
        val url = AppUpdateChecker.selectApkAssetUrl(
            assets = workflowReleaseAssets,
            supportedAbis = arrayOf("armeabi-v7a", "armeabi"),
            prefix = "BitVinyl-",
        )
        assertEquals("https://example.com/BitVinyl-prod-armeabi-v7a-release.apk", url)
    }

    @Test
    fun `matches x86_64 workflow APK without confusing x86`() {
        val url64 = AppUpdateChecker.selectApkAssetUrl(
            assets = workflowReleaseAssets,
            supportedAbis = arrayOf("x86_64"),
            prefix = "BitVinyl-",
        )
        assertEquals("https://example.com/BitVinyl-prod-x86_64-release.apk", url64)

        val urlX86Only = AppUpdateChecker.selectApkAssetUrl(
            assets = workflowReleaseAssets,
            supportedAbis = arrayOf("x86"),
            prefix = "BitVinyl-",
        )
        assertEquals("https://example.com/BitVinyl-prod-universal-release.apk", urlX86Only)
    }

    @Test
    fun `falls back to universal workflow APK when device ABI split is absent`() {
        val onlyUniversal = listOf(
            "BitVinyl-prod-universal-release.apk" to "https://example.com/BitVinyl-prod-universal-release.apk",
        )
        val url = AppUpdateChecker.selectApkAssetUrl(
            assets = onlyUniversal,
            supportedAbis = arrayOf("arm64-v8a"),
            prefix = "BitVinyl-",
        )
        assertEquals("https://example.com/BitVinyl-prod-universal-release.apk", url)
    }

    @Test
    fun `ignores unsigned and debug APKs`() {
        val invalidOnly = listOf(
            "BitVinyl-prod-universal-release-unsigned.apk" to "https://example.com/unsigned.apk",
            "BitVinyl-dev-arm64-v8a-debug.apk" to "https://example.com/debug.apk",
            "BitVinylTV-prod-arm64-v8a-release.apk" to "https://example.com/tv.apk",
            "BitVinylWear-prod-release.apk" to "https://example.com/wear.apk",
        )
        val url = AppUpdateChecker.selectApkAssetUrl(
            assets = invalidOnly,
            supportedAbis = arrayOf("arm64-v8a"),
            prefix = "BitVinyl-",
        )
        assertNull(url)
    }

    @Test
    fun `supports versioned release-dist and underscore legacy names`() {
        val versionedAssets = listOf(
            "BitVinyl-v1.9.1-hotfix-arm64-v8a.apk" to "https://example.com/BitVinyl-v1.9.1-hotfix-arm64-v8a.apk",
            "BitVinyl-v1.9.1-hotfix-universal.apk" to "https://example.com/BitVinyl-v1.9.1-hotfix-universal.apk",
        )
        assertEquals(
            "https://example.com/BitVinyl-v1.9.1-hotfix-arm64-v8a.apk",
            AppUpdateChecker.selectApkAssetUrl(versionedAssets, arrayOf("arm64-v8a"), "BitVinyl-"),
        )
        assertEquals(
            "https://example.com/BitVinyl-v1.9.1-hotfix-universal.apk",
            AppUpdateChecker.selectApkAssetUrl(versionedAssets, arrayOf("riscv64"), "BitVinyl-"),
        )
    }

    @Test
    fun `version comparison handles releases and beta suffixes`() {
        assertTrue(AppUpdateChecker.isNewer("1.9.3", "1.9.2"))
        assertTrue(AppUpdateChecker.isNewer("1.10.0", "1.9.2"))
        assertTrue(AppUpdateChecker.isNewer("1.9.2", "1.9.2-beta1"))
        assertFalse(AppUpdateChecker.isNewer("1.9.2", "1.9.2"))
        assertFalse(AppUpdateChecker.isNewer("1.9.1", "1.9.2"))
    }
}

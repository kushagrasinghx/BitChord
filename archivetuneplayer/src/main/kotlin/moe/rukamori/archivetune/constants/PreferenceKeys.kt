/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val CustomThemeColorKey = stringPreferencesKey("customThemeColor")
val RandomThemeOnStartupKey = booleanPreferencesKey("randomThemeOnStartup")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val DisableAnimationsKey = booleanPreferencesKey("disableAnimations")
val ForceHighRefreshRateKey = booleanPreferencesKey("forceHighRefreshRate")
val WallpaperExtractionFailedKey = booleanPreferencesKey("wallpaperExtractionFailed")
val HideStatusBarKey = booleanPreferencesKey("hideStatusBar")

val UiScaleFactorKey = floatPreferencesKey("uiScaleFactor")

val TabletModeEnabledKey = booleanPreferencesKey("tabletModeEnabled")
val EnableHapticFeedbackKey = booleanPreferencesKey("enableHapticFeedback")
val UseSystemFontKey = booleanPreferencesKey("useSystemFont")
val FontPreferenceKey = stringPreferencesKey("fontPreference")
val CustomFontUriKey = stringPreferencesKey("customFontUri")
val CustomFontNameKey = stringPreferencesKey("customFontName")
val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val PlayerDesignStyleKey = stringPreferencesKey("playerDesignStyle")

val ShowPlayerVolumeBarKey = booleanPreferencesKey("showPlayerVolumeBar")
val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val ArchiveTuneCanvasKey = booleanPreferencesKey("archiveTuneCanvas")
val SpotifyCanvasKey = booleanPreferencesKey("spotifyCanvas")
val SongCanvasDisabledKey = stringPreferencesKey("songCanvasDisabled")
val AndroidAutoOnlineRecommendationsKey = booleanPreferencesKey("androidAutoOnlineRecommendations")
val AndroidAutoOnlineVoiceSearchKey = booleanPreferencesKey("androidAutoOnlineVoiceSearch")
val AndroidAutoLocalSongsKey = booleanPreferencesKey("androidAutoLocalSongs")
val AndroidAutoMeteredPlaybackKey = booleanPreferencesKey("androidAutoMeteredPlayback")
val AndroidAutoMeteredArtworkKey = booleanPreferencesKey("androidAutoMeteredArtwork")
val AndroidAutoPrimaryActionKey = stringPreferencesKey("androidAutoPrimaryAction")
val AndroidAutoSecondaryActionKey = stringPreferencesKey("androidAutoSecondaryAction")

val AlbumCanvasEnabledKey = booleanPreferencesKey("albumCanvasEnabled")
val AppleMusicAnimatedArtworkKey = booleanPreferencesKey("appleMusicAnimatedArtwork")

val CanvasResolverEndpointsKey = stringPreferencesKey("canvasResolverEndpoints")

val DolbyFfmpegDecoderKey = booleanPreferencesKey("dolbyFfmpegDecoder")

val ProceduralArtworkEnabledKey = booleanPreferencesKey("proceduralArtworkEnabled")
val ProceduralArtworkStyleKey = stringPreferencesKey("proceduralArtworkStyle")
val CanvasAudioReactiveKey = booleanPreferencesKey("canvasAudioReactive")

enum class ProceduralCanvasStyle {
    KAWARP,
    SQUARE_TUNNEL,
    ;

    companion object {
        fun fromPreference(value: String?): ProceduralCanvasStyle {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: KAWARP
        }
    }
}

val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")
val CropThumbnailToSquareKey = booleanPreferencesKey("cropThumbnailToSquare")

val AodThumbnailShapeKey = stringPreferencesKey("aodThumbnailShape")
val AodThumbnailSizeKey = floatPreferencesKey("aodThumbnailSize")
val AodThumbnailShapeRotationKey = intPreferencesKey("aodThumbnailShapeRotation")
val AodShowThumbnailKey = booleanPreferencesKey("aodShowThumbnail")
val AodShowArtistKey = booleanPreferencesKey("aodShowArtist")
val AodShowAlbumKey = booleanPreferencesKey("aodShowAlbum")
val AodShowProgressKey = booleanPreferencesKey("aodShowProgress")
val AodShowTimeLabelsKey = booleanPreferencesKey("aodShowTimeLabels")
val AodShowControlsKey = booleanPreferencesKey("aodShowControls")
val AodShowExitButtonKey = booleanPreferencesKey("aodShowExitButton")
val AodArtworkGlowKey = booleanPreferencesKey("aodArtworkGlow")
val AodBackgroundStyleKey = stringPreferencesKey("aodBackgroundStyle")
val AodAccentStyleKey = stringPreferencesKey("aodAccentStyle")
val AodContentPositionKey = stringPreferencesKey("aodContentPosition")
val AodTextAlignmentKey = stringPreferencesKey("aodTextAlignment")
val AodControlStyleKey = stringPreferencesKey("aodControlStyle")
val AodControlSizeKey = floatPreferencesKey("aodControlSize")

val AodSliderStyleKey = stringPreferencesKey("aodSliderStyle")
val AodHorizontalPaddingKey = floatPreferencesKey("aodHorizontalPadding")
val AodVerticalSpacingKey = floatPreferencesKey("aodVerticalSpacing")
val AodTitleMaxLinesKey = intPreferencesKey("aodTitleMaxLines")
val AodAmbientIntensityKey = floatPreferencesKey("aodAmbientIntensity")
val AodModeEnabledKey = booleanPreferencesKey("aodModeEnabled")
val AodAutoStartScreenOffKey = booleanPreferencesKey("aodAutoStartScreenOff")
val AodTouchLockEnabledKey = booleanPreferencesKey("aodTouchLockEnabled")
val AodUnlockMethodKey = stringPreferencesKey("aodUnlockMethod")
val AodShowClockKey = booleanPreferencesKey("aodShowClock")
val AodClockStyleKey = stringPreferencesKey("aodClockStyle")
val AodShowBatteryKey = booleanPreferencesKey("aodShowBattery")
val AodPixelShiftEnabledKey = booleanPreferencesKey("aodPixelShiftEnabled")
val AodAutoDimmingKey = booleanPreferencesKey("aodAutoDimming")
val AodAutoDimTimeoutKey = intPreferencesKey("aodAutoDimTimeout")
val AodGesturesEnabledKey = booleanPreferencesKey("aodGesturesEnabled")
val AodShakeToUnlockKey = booleanPreferencesKey("aodShakeToUnlock")
val AodAutoLockEnabledKey = booleanPreferencesKey("aodAutoLockEnabled")
val AodAutoLockTimeoutKey = intPreferencesKey("aodAutoLockTimeout")
val AodMarqueeTitlesKey = booleanPreferencesKey("aodMarqueeTitles")
val AodMinimalLockedStateKey = booleanPreferencesKey("aodMinimalLockedState")
val AodBrightnessKey = floatPreferencesKey("aodBrightness")
val AodProximityBlackoutKey = booleanPreferencesKey("aodProximityBlackout")
val AodTrueAmbientModeKey = booleanPreferencesKey("aodTrueAmbientMode")

val AodShowLyricsKey = booleanPreferencesKey("aodShowLyrics")

val AodAutoTimerSecondsKey = intPreferencesKey("aodAutoTimerSeconds")

val EnableMusixmatchExperimentalKey = booleanPreferencesKey("enableMusixmatchExperimental")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val DisableBlurKey = booleanPreferencesKey("disableBlur")
val BlurRadiusKey = floatPreferencesKey("blurRadius")

val BackdropEnabledKey = booleanPreferencesKey("backdropEnabled")
val BackdropBlurAmountKey = intPreferencesKey("backdropBlurAmount")
val MiniPlayerLastAnchorKey = intPreferencesKey("miniPlayerLastAnchor")
val MiniPlayerBackgroundStyleKey = stringPreferencesKey("miniPlayerBackgroundStyle")

val LiquidGlassEnabledKey = booleanPreferencesKey("liquidGlassEnabled")
val LiquidGlassNavBarEnabledKey = booleanPreferencesKey("liquidGlassNavBarEnabled")
val NavigationBarBitchordKey = booleanPreferencesKey("navigationBarBitchord")
val SukisuLiquidGlassNavBarKey = booleanPreferencesKey("sukisuLiquidGlassNavBar")

enum class LiquidGlassIntensity { SUBTLE, STANDARD, VIVID }

val LiquidGlassIntensityKey = stringPreferencesKey("liquidGlassIntensity")
const val LIQUID_GLASS_INTENSITY_DEFAULT = "STANDARD"

val LiquidGlassRefractionHeightKey = floatPreferencesKey("liquidGlassRefractionHeight")
const val LIQUID_GLASS_REFRACTION_HEIGHT_DEFAULT = 0.30f

val LiquidGlassRefractionAmountKey = floatPreferencesKey("liquidGlassRefractionAmount")
const val LIQUID_GLASS_REFRACTION_AMOUNT_DEFAULT = 0.75f

val LiquidGlassBlurRadiusKey = floatPreferencesKey("liquidGlassBlurRadius")
const val LIQUID_GLASS_BLUR_RADIUS_DEFAULT = 0.65f

val LiquidGlassTintOpacityKey = floatPreferencesKey("liquidGlassTintOpacity")
const val LIQUID_GLASS_TINT_OPACITY_DEFAULT = 1.40f

val LiquidGlassShadowDepthKey = floatPreferencesKey("liquidGlassShadowDepth")
const val LIQUID_GLASS_SHADOW_DEPTH_DEFAULT = 0.45f

val LiquidGlassDepth3DKey = booleanPreferencesKey("liquidGlassDepth3D")
const val LIQUID_GLASS_DEPTH_3D_DEFAULT = true

val LiquidGlassChromaticAberrationKey = booleanPreferencesKey("liquidGlassChromaticAberration")
const val LIQUID_GLASS_CHROMATIC_ABERRATION_DEFAULT = true

val LiquidGlassBackdropVibrancyKey = booleanPreferencesKey("liquidGlassBackdropVibrancy")
const val LIQUID_GLASS_BACKDROP_VIBRANCY_DEFAULT = true

val LiquidGlassAdaptiveLuminanceKey = booleanPreferencesKey("liquidGlassAdaptiveLuminance")
const val LIQUID_GLASS_ADAPTIVE_LUMINANCE_DEFAULT = true

enum class AodThumbnailShape {
    ROUNDED,
    SQUARE,
    CIRCLE,
    PILL,
    ARCH,
    SLANTED,
    DIAMOND,
    PENTAGON,
    TRIANGLE,
    HEART,
    FLOWER,
    CLOVER_4,
    COOKIE_6,
    COOKIE_9,
    SUNNY,
    SOFT_BURST,
    GHOSTISH,
    PIXEL_CIRCLE,
}

enum class AodBackgroundStyle {
    PURE_BLACK,
    SOFT_RADIAL,
    TONAL_EDGE,
    AMBIENT_GLOW,
    ADAPTIVE_ART,
    FROSTED_WALLPAPER,
    ADAPTIVE_FROSTED,
}

enum class AodAccentStyle {
    MONOCHROME,
    THEME,
}

enum class AodUnlockMethod {
    SLIDE,
    HOLD,
}

enum class AodClockStyle {
    BOLD_DIGITAL,
    MINIMAL,
    ELEGANT_THIN,
    PIXEL_STACKED,
}

enum class AodContentPosition {
    TOP,
    CENTER,
    BOTTOM,
}

enum class AodTextAlignment {
    START,
    CENTER,
    END,
}

enum class AodControlStyle {
    FILLED,
    TONAL,
    MINIMAL,
}

enum class SliderStyle {
    Standard,
    Wavy,
    Thick,
    Circular,
    Simple,
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"

enum class AppFontPreference {
    DEFAULT,
    SYSTEM,
    CUSTOM,
}

enum class PlaylistSuggestionSource {
    PLAYLIST_TITLE,
    PLAYLIST_CONTENT,
    BOTH,
}

val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")

val YouTubeMusicRegionKey = stringPreferencesKey("youtubeMusicRegion")
val PlaylistSuggestionSourceKey = stringPreferencesKey("playlistSuggestionSource")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val EnableBetterLyricsPortatoKey = booleanPreferencesKey("enableBetterLyricsPortato")
val EnableYouLyPlusLyricsKey = booleanPreferencesKey("enableYouLyPlusLyrics")

val EnableMegalobizLyricsKey = booleanPreferencesKey("enableMegalobizLyrics")
val EnableBiniLyricsKey = booleanPreferencesKey("enableBiniLyrics")
val EnableLrcRedKey = booleanPreferencesKey("enableLrcRed")
val EnableSimpMusicLyricsKey = booleanPreferencesKey("enableSimpMusicLyrics")
val EnableGeniusLyricsKey = booleanPreferencesKey("enableGeniusLyrics")

val PaxsenixApiKeyKey = stringPreferencesKey("paxsenixApiKey")
val PaxsenixEndpointKey = stringPreferencesKey("paxsenixEndpoint")
val EnableUnisonLyricsKey = booleanPreferencesKey("enableUnisonLyrics")

val EnablePaxsenixAppleMusicLyricsKey = booleanPreferencesKey("enablePaxsenixAppleMusicLyrics")
val EnablePaxsenixSpotifyLyricsKey = booleanPreferencesKey("enablePaxsenixSpotifyLyrics")
val EnablePaxsenixMusixmatchLyricsKey = booleanPreferencesKey("enablePaxsenixMusixmatchLyrics")

val PrioritizeWordSyncedLyricsKey = booleanPreferencesKey("prioritizeWordSyncedLyrics")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoKey = booleanPreferencesKey("hideVideo")

val HomeCatalogueSwitchKey = booleanPreferencesKey("homeCatalogueSwitch")

val EnableVideoPlaybackKey = booleanPreferencesKey("enableVideoPlayback")

val EnablePipModeKey = booleanPreferencesKey("enablePipMode")
val AllowAgeRestrictedKey = booleanPreferencesKey("allowAgeRestricted")
enum class DownloadSource {
    AUTO,

    QOBUZ,

    QOBUZ_BACKUP,
    TIDAL,

    DEEZER,

    JIOSAAVN,

    YOUTUBE_MUSIC,
}

val DownloadSourceKey = stringPreferencesKey("downloadSource")

val DownloadSourceOrderKey = stringPreferencesKey("downloadSourceOrder")

object DownloadSourceConfig {
    val DEFAULT_ORDER: List<DownloadSource> =
        listOf(
            DownloadSource.QOBUZ,
            DownloadSource.QOBUZ_BACKUP,
            DownloadSource.TIDAL,
            DownloadSource.DEEZER,
            DownloadSource.JIOSAAVN,
            DownloadSource.YOUTUBE_MUSIC,
        )

    val REQUIRES_POOL: Set<DownloadSource> =
        setOf(DownloadSource.QOBUZ, DownloadSource.TIDAL, DownloadSource.DEEZER)

    val YOUTUBE_MUSIC_CACHE_KEY_PREFIX = "ytm:"

    val CACHE_KEY_PREFIXES: List<String> =
        DownloadSource.entries
            .filterNot { it == DownloadSource.AUTO }
            .map { "${it.name.lowercase(Locale.US)}:" }

    fun cacheKeyPrefix(source: DownloadSource): String? =
        when (source) {
            DownloadSource.AUTO -> null
            DownloadSource.YOUTUBE_MUSIC -> YOUTUBE_MUSIC_CACHE_KEY_PREFIX
            else -> "${source.name.lowercase(Locale.US)}:"
        }

    fun downloadCacheKey(source: DownloadSource, mediaId: String): String =
        cacheKeyPrefix(source)?.let { "$it$mediaId" } ?: mediaId

    fun downloadIdToSongId(id: String): String {
        for (prefix in CACHE_KEY_PREFIXES) {
            if (id.startsWith(prefix)) return id.removePrefix(prefix)
        }
        return id
    }

    fun songIdToDownloadIds(songId: String): List<String> =
        CACHE_KEY_PREFIXES.map { "$it$songId" } + songId

    fun downloadSourceForCacheKey(key: String): DownloadSource? {
        if (!key.contains(':')) return DownloadSource.YOUTUBE_MUSIC
        val prefix = key.substringBefore(":") + ":"
        return DownloadSource.entries.firstOrNull {
            it != DownloadSource.AUTO && cacheKeyPrefix(it) == prefix
        }
    }

    private fun parseType(name: String): DownloadSource? =
        runCatching { DownloadSource.valueOf(name.trim().uppercase()) }.getOrNull()

    fun parseOrder(rawOrder: String?): List<DownloadSource> {
        val stored =
            rawOrder
                ?.split(',')
                ?.mapNotNull { parseType(it) }
                ?.distinct()
                .orEmpty()
        if (stored.isEmpty()) return DEFAULT_ORDER

        val missing = DEFAULT_ORDER.filterNot { it in stored }
        if (missing.isEmpty()) return stored

        val merged = mutableListOf<DownloadSource>()
        var inserted = false
        for (source in stored) {
            if (!inserted && source == DownloadSource.YOUTUBE_MUSIC) {
                merged.addAll(missing)
                inserted = true
            }
            merged.add(source)
        }

        if (!inserted) merged.addAll(missing)
        return merged
    }

    fun serialize(order: List<DownloadSource>): String = order.joinToString(",") { it.name }
}

val AiContentFilterEnabledKey = booleanPreferencesKey("aiContentFilterEnabled")
val AiContentFilterIncludeModerateKey = booleanPreferencesKey("aiContentFilterIncludeModerate")
val AiContentFilterLastUpdatedKey = longPreferencesKey("aiContentFilterLastUpdated")
val AiMixLastGeneratedAtKey = longPreferencesKey("aiMixLastGeneratedAt")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyHostKey = stringPreferencesKey("proxyHost")
val ProxyPortKey = intPreferencesKey("proxyPort")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val EnableDnsOverHttpsKey = booleanPreferencesKey("enableDnsOverHttps")
val DnsOverHttpsProviderKey = stringPreferencesKey("dnsOverHttpsProvider")
val StreamBypassProxyKey = booleanPreferencesKey("streamBypassProxy")
val IpRotationEnabledKey = booleanPreferencesKey("ipRotationEnabled")
val YtmSyncKey = booleanPreferencesKey("ytmSync")
val ForceSyncOnAccountSwitchKey = booleanPreferencesKey("forceSyncOnAccountSwitch")
val SelectedYtmPlaylistsKey = stringPreferencesKey("ytm_selected_playlists")
val LocalSongsMinDurationSecondsKey = intPreferencesKey("local_songs_min_duration_seconds")
val LocalSongsIncludedFoldersKey = stringSetPreferencesKey("local_songs_included_folders")
val LocalSongsExcludedFoldersKey = stringSetPreferencesKey("local_songs_excluded_folders")
val LocalSongsSortTypeKey = stringPreferencesKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = booleanPreferencesKey("local_songs_sort_descending")

val LocalScanExtractionVersionKey = intPreferencesKey("local_scan_extraction_version")

val ListenBrainzEnabledKey = booleanPreferencesKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringPreferencesKey("listenbrainz_token")

val AiProviderKey = stringPreferencesKey("ai_provider")
val AiCustomEndpointKey = stringPreferencesKey("ai_custom_endpoint")
val AiApiKeyKey = stringPreferencesKey("ai_api_key")
val AiApiValidationStatusKey = stringPreferencesKey("ai_api_validation_status")
val AiSelectedModelKey = stringPreferencesKey("ai_selected_model")
val AiCustomModelKey = stringPreferencesKey("ai_custom_model")

val AiRomanizeSeparateProviderKey = booleanPreferencesKey("ai_romanize_separate_provider")
val AiRomanizeProviderKey = stringPreferencesKey("ai_romanize_provider")
val AiRomanizeCustomEndpointKey = stringPreferencesKey("ai_romanize_custom_endpoint")
val AiRomanizeApiKeyKey = stringPreferencesKey("ai_romanize_api_key")
val AiRomanizeApiValidationStatusKey = stringPreferencesKey("ai_romanize_api_validation_status")
val AiRomanizeSelectedModelKey = stringPreferencesKey("ai_romanize_selected_model")
val AiRomanizeCustomModelKey = stringPreferencesKey("ai_romanize_custom_model")

val DeeplApiKeyKey = stringPreferencesKey("deeplApiKey")

val DeeplFormalityKey = stringPreferencesKey("deeplFormality")

val OpenRouterApiKeyKey = stringPreferencesKey("openRouterApiKey")
val OpenRouterBaseUrlKey = stringPreferencesKey("openRouterBaseUrl")
val OpenRouterModelKey = stringPreferencesKey("openRouterModel")

val TranslateLanguageKey = stringPreferencesKey("translateLanguage")

val TranslateSourceLanguageKey = stringPreferencesKey("translateSourceLanguage")

val HideAiMixKey = booleanPreferencesKey("hide_ai_mix")

val LogcatPausedKey = booleanPreferencesKey("logcatPaused")

val AutoTranslateLyricsKey = booleanPreferencesKey("autoTranslateLyrics")

val AutoTranslateExcludedLanguagesKey = stringSetPreferencesKey("autoTranslateExcludedLanguages")

val AiRomanizeLyricsKey = booleanPreferencesKey("aiRomanizeLyrics")

val AutoAiRomanizeLyricsKey = booleanPreferencesKey("autoAiRomanizeLyrics")

val AiRomanizeExcludedLanguagesKey = stringSetPreferencesKey("aiRomanizeExcludedLanguages")

val NeverShowUpdatePopupKey = stringPreferencesKey("neverShowUpdatePopupVersion")

val HideLikedSongsCardKey = booleanPreferencesKey("hide_liked_songs_card")
val HideOfflineCardKey = booleanPreferencesKey("hide_offline_card")
val HideCachedCardKey = booleanPreferencesKey("hide_cached_card")
val HideLocalFilesCardKey = booleanPreferencesKey("hide_local_files_card")
val HideTop50CardKey = booleanPreferencesKey("hide_top50_card")

enum class AiProvider {
    CHATGPT,
    GEMINI,
    OPENROUTER,
    CUSTOM,
    DEEPL,
    MISTRAL,
    NONE,
}

enum class AiApiValidationStatus {
    UNKNOWN,
    SUCCESS,
    FAILED,
}

val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUsernameKey = stringPreferencesKey("lastfmUsername")
val LastFMProviderKey = stringPreferencesKey("lastfmProvider")
val LastFMCustomEndpointKey = stringPreferencesKey("lastfmCustomEndpoint")
val LastFMApiKeyOverrideKey = stringPreferencesKey("lastfmApiKeyOverride")
val LastFMSecretOverrideKey = stringPreferencesKey("lastfmSecretOverride")
val LibreFMApiKeyOverrideKey = stringPreferencesKey("librefmApiKeyOverride")
val LibreFMSecretOverrideKey = stringPreferencesKey("librefmSecretOverride")
val CustomScrobbleApiKeyOverrideKey = stringPreferencesKey("customScrobbleApiKeyOverride")
val CustomScrobbleSecretOverrideKey = stringPreferencesKey("customScrobbleSecretOverride")
val LastFMCredentialsMigratedKey = booleanPreferencesKey("lastfmCredentialsMigrated")
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")
val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")

enum class LastFmProvider {
    LASTFM,
    LIBREFM,
    CUSTOM,
}

val AudioQualityKey = stringPreferencesKey("audioQuality")

val LowDataModeKey = booleanPreferencesKey("networkMetered")

enum class AudioQuality {
    AUTO,
    HIGH,
    HIGHEST,
    LOW,
}

val PlayerStreamClientKey = stringPreferencesKey("playerStreamClient")
val AutoChoosePlaybackClientKey = booleanPreferencesKey("autoChoosePlaybackClient")

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
    ARCHIVETUNE_EXTRACTOR,
    HI_RES_LOSSLESS,
    IOS,
    TVHTML5,
    ANDROID_MUSIC,
}

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PermanentShuffleKey = booleanPreferencesKey("permanentShuffle")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val AudioPlaybackSpeedKey = floatPreferencesKey("audioPlaybackSpeed")
val AudioPlaybackSpeedPitchMatchKey = booleanPreferencesKey("audioPlaybackSpeedPitchMatch")
val AudioPlaybackPitchKey = floatPreferencesKey("audioPlaybackPitch")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val ReplayGainModeKey = stringPreferencesKey("replayGainMode")
val AudioOffload = booleanPreferencesKey("audioOffload")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfadeEnabled")
val CrossfadeDurationKey = floatPreferencesKey("crossfadeDuration")
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")

enum class AutomixPerformanceMode(val inferenceThreads: Int) {
    EFFICIENT(1),
    BALANCED(2),
    PERFORMANCE(4),
}

val AutomixEnabledKey = booleanPreferencesKey("automixEnabled")
val AutomixPerformanceModeKey = stringPreferencesKey("automixPerformanceMode")

val FloatDspEnabledKey = booleanPreferencesKey("floatDspEnabled")

val UsbExclusiveAudioKey = booleanPreferencesKey("usbExclusiveAudio")

val BitPerfectOutputKey = booleanPreferencesKey("bitPerfectOutput")
const val BIT_PERFECT_OUTPUT_DEFAULT = false

val BitPerfectNativeRateKey = booleanPreferencesKey("bitPerfectNativeRate")
const val BIT_PERFECT_NATIVE_RATE_DEFAULT = true

val TryptifyAudioProcessingKey = booleanPreferencesKey("tryptifyAudioProcessing")

val LastwaveAudioProcessingKey = booleanPreferencesKey("lastwaveAudioProcessing")

val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val PauseOnDeviceMuteKey = booleanPreferencesKey("pauseOnDeviceMute")
val DeviceMutePlaybackRecoveryVolumeKey = intPreferencesKey("deviceMutePlaybackRecoveryVolume")
val AutoStartOnBluetoothKey = booleanPreferencesKey("autoStartOnBluetooth")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val WakelockKey = booleanPreferencesKey("wakelock")

val SponsorBlockEnabledKey = booleanPreferencesKey("sponsorBlockEnabled")
val SponsorBlockCategoriesKey = stringSetPreferencesKey("sponsorBlockCategories")
val SponsorBlockApiUrlKey = stringPreferencesKey("sponsorBlockApiUrl")

val ArtistSeparatorsKey = stringPreferencesKey("artistSeparators")
val ExternalDownloaderEnabledKey = booleanPreferencesKey("externalDownloaderEnabled")
val ExternalDownloaderPackageKey = stringPreferencesKey("externalDownloaderPackage")
val PlaylistTagsFilterKey = stringPreferencesKey("playlistTagsFilter")
val ShowHomeCategoryChipsKey = booleanPreferencesKey("showHomeCategoryChips")
val ShowTagsInLibraryKey = booleanPreferencesKey("showTagsInLibrary")

val MinimalHomeModeKey = booleanPreferencesKey("minimalHomeMode")

val EqualizerEnabledKey = booleanPreferencesKey("equalizerEnabled")
val EqualizerAudioEffectsEnabledKey = booleanPreferencesKey("audioEffectsEnabled")
val EqualizerControlModeKey = stringPreferencesKey("equalizerControlMode")
val EqualizerBandLevelsMbKey = stringPreferencesKey("equalizerBandLevelsMb")
val EqualizerBandFreqsKey = stringPreferencesKey("equalizerBandFreqsHz")
val EqualizerAutoHeadroomEnabledKey = booleanPreferencesKey("equalizerAutoHeadroomEnabled")
val EqualizerOutputGainEnabledKey = booleanPreferencesKey("equalizerOutputGainEnabled")
val EqualizerOutputGainMbKey = intPreferencesKey("equalizerOutputGainMb")
val EqualizerBassBoostEnabledKey = booleanPreferencesKey("equalizerBassBoostEnabled")
val EqualizerBassBoostStrengthKey = intPreferencesKey("equalizerBassBoostStrength")
val EqualizerVirtualizerEnabledKey = booleanPreferencesKey("equalizerVirtualizerEnabled")
val EqualizerVirtualizerStrengthKey = intPreferencesKey("equalizerVirtualizerStrength")
val EqualizerSelectedProfileIdKey = stringPreferencesKey("equalizerSelectedProfileId")
val EqualizerCustomProfilesJsonKey = stringPreferencesKey("equalizerCustomProfilesJson")
val EqualizerReverbEnabledKey = booleanPreferencesKey("equalizerReverbEnabled")
val EqualizerReverbPresetKey = intPreferencesKey("equalizerReverbPreset")
val EqualizerBalanceKey = floatPreferencesKey("equalizerBalance")
val Equalizer8DEnabledKey = booleanPreferencesKey("equalizer8DEnabled")
val Equalizer8DSpeedKey = floatPreferencesKey("equalizer8DSpeedHz")

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val SmartTrimmerKey = booleanPreferencesKey("smartTrimmer")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")
val MaxCanvasCacheSizeKey = intPreferencesKey("maxCanvasCacheSize")

val MaxVideoCacheSizeKey = intPreferencesKey("maxVideoCacheSize")
val StorageFolderIdKey = stringPreferencesKey("storageFolderId")
val StorageFolderTreeUriKey = stringPreferencesKey("storageFolderTreeUri")
val StorageFolderPathKey = stringPreferencesKey("storageFolderPath")
val StorageFolderDisplayNameKey = stringPreferencesKey("storageFolderDisplayName")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")

val SyncPlaybackToYouTubeHistoryKey = booleanPreferencesKey("syncPlaybackToYouTubeHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

val LastFmPreferYtThumbnailsKey = booleanPreferencesKey("lastfmPreferYtThumbnails")

val DiscordTokenKey = stringPreferencesKey("discordToken")
val DiscordRefreshTokenKey = stringPreferencesKey("discordRefreshToken")
val DiscordTokenExpiresAtKey = longPreferencesKey("discordTokenExpiresAt")

val DiscordPendingAuthStateKey = stringPreferencesKey("discordPendingAuthState")
val DiscordPendingAuthVerifierKey = stringPreferencesKey("discordPendingAuthVerifier")
val DiscordPendingAuthStartedAtKey = longPreferencesKey("discordPendingAuthStartedAt")
val DiscordInfoDismissedKey = booleanPreferencesKey("discordInfoDismissed")
val DiscordUsernameKey = stringPreferencesKey("discordUsername")
val DiscordNameKey = stringPreferencesKey("discordName")
val DiscordAvatarUrlKey = stringPreferencesKey("discordAvatarUrl")
val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")

val DiscordActivityNameKey = stringPreferencesKey("discordActivityName")
val DiscordActivityDetailsKey = stringPreferencesKey("discordActivityDetails")
val DiscordActivityStateKey = stringPreferencesKey("discordActivityState")

val DiscordActivityButton1LabelKey = stringPreferencesKey("discordActivityButton1Label")
val DiscordActivityButton1UrlSourceKey = stringPreferencesKey("discordActivityButton1UrlSource")
val DiscordActivityButton1CustomUrlKey = stringPreferencesKey("discordActivityButton1CustomUrl")
val DiscordActivityButton2LabelKey = stringPreferencesKey("discordActivityButton2Label")
val DiscordActivityButton2UrlSourceKey = stringPreferencesKey("discordActivityButton2UrlSource")
val DiscordActivityButton2CustomUrlKey = stringPreferencesKey("discordActivityButton2CustomUrl")
val DiscordActivityButton1EnabledKey = booleanPreferencesKey("discordActivityButton1Enabled")
val DiscordActivityButton2EnabledKey = booleanPreferencesKey("discordActivityButton2Enabled")
val DiscordShowWhenPausedKey = booleanPreferencesKey("discordShowWhenPaused")

val DiscordActivityTypeKey = stringPreferencesKey("discordActivityType")
val DiscordPresenceStatusKey = stringPreferencesKey("discordPresenceStatus")

val DiscordLargeImageTypeKey = stringPreferencesKey("discordLargeImageType")
val DiscordLargeTextSourceKey = stringPreferencesKey("discordLargeTextSource")
val DiscordLargeTextCustomKey = stringPreferencesKey("discordLargeTextCustom")
val DiscordLargeImageCustomUrlKey = stringPreferencesKey("discordLargeImageCustomUrl")
val DiscordSmallImageTypeKey = stringPreferencesKey("discordSmallImageType")
val DiscordSmallImageCustomUrlKey = stringPreferencesKey("discordSmallImageCustomUrl")

val DiscordActivityPlatformKey = stringPreferencesKey("discordActivityPlatform")

val TranslatorContextsKey = stringPreferencesKey("translatorContexts")
val TranslatorTargetLangKey = stringPreferencesKey("translatorTargetLang")
val EnableTranslatorKey = booleanPreferencesKey("enableTranslator")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringPreferencesKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanPreferencesKey("autoPlaylistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val LastLikeSongSyncKey = longPreferencesKey("last_like_song_sync")
val LastLibSongSyncKey = longPreferencesKey("last_library_song_sync")
val LastAlbumSyncKey = longPreferencesKey("last_album_sync")
val LastArtistSyncKey = longPreferencesKey("last_artist_sync")
val LastPlaylistSyncKey = longPreferencesKey("last_playlist_sync")

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")

val NewsLastReadTimestampKey = longPreferencesKey("news_last_read_timestamp")
val SpeedDialSongIdsKey = stringPreferencesKey("speedDialSongIds")
val LyricsProviderOrderKey = stringPreferencesKey("lyricsProviderOrder")
val ArtworkProviderOrderKey = stringPreferencesKey("artworkProviderOrder")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")

val ShowCodecOnPlayerKey = booleanPreferencesKey("show_codec_on_player")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class QuickPicksDisplayMode {
    CARD,
    LIST,
}

val QuickPicksDisplayModeKey = stringPreferencesKey("quickPicksDisplayMode")

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
}

enum class ArtistFilter {
    LIBRARY,
    LIKED,
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    DOWNLOADED_FULL,
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
    CUSTOM,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY -> {
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            WEEK -> {
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH -> {
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            YEAR -> {
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            ALL_TIME -> {
                0
            }
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
    DONT_SHOW,
}

enum class PreferredLyricsProvider {
    LRC_RED,
    BINI_LYRICS,
    BETTER_LYRICS,
    BETTER_LYRICS_PORTATO,
    PAXSENIX,
    PAXSENIX_SPOTIFY,
    PAXSENIX_MUSIXMATCH,
    YOULY_PLUS,
    SIMP_MUSIC,

    UNISON,

    LRCLIB,
    KUGOU,
    MEGALOBIZ,

    APPLE_MUSIC,
    MUSIXMATCH_EXPERIMENTAL,
    GENIUS,
}

val DefaultLyricsProviderOrder =
    listOf(
        PreferredLyricsProvider.LRC_RED,
        PreferredLyricsProvider.BINI_LYRICS,
        PreferredLyricsProvider.BETTER_LYRICS,
        PreferredLyricsProvider.BETTER_LYRICS_PORTATO,
        PreferredLyricsProvider.PAXSENIX,
        PreferredLyricsProvider.PAXSENIX_SPOTIFY,
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH,
        PreferredLyricsProvider.YOULY_PLUS,
        PreferredLyricsProvider.SIMP_MUSIC,

        PreferredLyricsProvider.UNISON,

        PreferredLyricsProvider.LRCLIB,
        PreferredLyricsProvider.KUGOU,
        PreferredLyricsProvider.MEGALOBIZ,

        PreferredLyricsProvider.APPLE_MUSIC,
        PreferredLyricsProvider.MUSIXMATCH_EXPERIMENTAL,

        PreferredLyricsProvider.GENIUS,
    )

fun deserializeLyricsProviderOrder(orderStr: String?): List<PreferredLyricsProvider> {
    if (orderStr.isNullOrBlank()) return DefaultLyricsProviderOrder

    val parsed =
        orderStr
            .split(",")
            .mapNotNull { name ->
                PreferredLyricsProvider.entries.find { it.name == name.trim() }
            }.distinct()

    val normalized =
        when {
            parsed.take(3) ==
                listOf(
                    PreferredLyricsProvider.LRCLIB,
                    PreferredLyricsProvider.KUGOU,
                    PreferredLyricsProvider.BETTER_LYRICS,
                )
            -> listOf(PreferredLyricsProvider.BETTER_LYRICS) + parsed.filterNot { it == PreferredLyricsProvider.BETTER_LYRICS }

            else -> parsed
        }

    val missing = DefaultLyricsProviderOrder.filterNot { it in normalized }
    return normalized + missing
}

enum class PreferredArtworkProvider {
    LOCAL_EMBEDDED,
    ORIGINAL_METADATA,
    TIDAL,
    SPOTIFY_CANVAS,
    ARCHIVETUNE_CANVAS,
}

val DefaultArtworkProviderOrder =
    listOf(
        PreferredArtworkProvider.LOCAL_EMBEDDED,
        PreferredArtworkProvider.ORIGINAL_METADATA,
        PreferredArtworkProvider.TIDAL,
        PreferredArtworkProvider.SPOTIFY_CANVAS,
        PreferredArtworkProvider.ARCHIVETUNE_CANVAS,
    )

fun deserializeArtworkProviderOrder(orderStr: String?): List<PreferredArtworkProvider> {
    if (orderStr.isNullOrBlank()) return DefaultArtworkProviderOrder

    val parsed =
        orderStr
            .split(",")
            .mapNotNull { name ->
                PreferredArtworkProvider.entries.find { it.name == name.trim() }
            }.distinct()

    val missing = DefaultArtworkProviderOrder.filterNot { it in parsed }
    return parsed + missing
}

enum class PlayerButtonsStyle {
    DEFAULT,
    SECONDARY,
}

enum class HomeSource {
    YOUTUBE,
    SPOTIFY,
}

val HomeSourceKey = stringPreferencesKey("homeSource")

enum class PlayerDesignStyle {
    V4,
    V5,
    V7,
    V9,
    APPLE_MUSIC,
    V10,

    BITCHORD,
    TIKTOK,
    SIMPMUSIC,
    SPATIALFLOW,
    LOOPER,
}

val FlamingoShowVolumeBarKey = booleanPreferencesKey("flamingoNowPlayingShowVolumeBar")
val FlamingoHideSlideBarKey = booleanPreferencesKey("flamingoNowPlayingHideSlideBar")
val FlamingoBackgroundEffectKey = booleanPreferencesKey("flamingoNowPlayingBackgroundEffect")

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    CUSTOM,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
}

enum class LyricsBackgroundStyle {
    DEFAULT,
    FOLLOW_THEME,
    COLORING,
    MOVING_BLUR,
    CUSTOM;

    fun resolveFor(playerBackgroundStyle: PlayerBackgroundStyle): LyricsBackgroundStyle =
        when {
            playerBackgroundStyle == PlayerBackgroundStyle.CUSTOM -> CUSTOM
            this == CUSTOM -> DEFAULT
            else -> this
        }
}

enum class MiniPlayerBackgroundStyle {
    THEME,
    GRADIENT,
    GLOW,
    FROSTED,
    LIQUID_GLASS,
}

val NavigationBarFrostedBlurKey = booleanPreferencesKey("navigationBarFrostedBlur")

val NavigationBarTintFrostedBlurKey = booleanPreferencesKey("navigationBarTintFrostedBlur")
val HideNavigationBarLabelsKey = booleanPreferencesKey("hideNavigationBarLabels")
val AddToPlaylistSortKey = stringPreferencesKey("addToPlaylistSort")

enum class NavigationBarCompactBehavior {
    ADAPTIVE,
    ALWAYS_EXPANDED,
    ALWAYS_COMPACT,
}

val NavigationBarCompactBehaviorKey = stringPreferencesKey("navigationBarCompactBehavior")

val NavigationBarWidthKey = floatPreferencesKey("navigationBarWidth")
const val NAVIGATION_BAR_WIDTH_DEFAULT = 0.8f

val NavigationBarHeightKey = floatPreferencesKey("navigationBarHeight")
const val NAVIGATION_BAR_HEIGHT_DEFAULT = 1.0f

val NavigationBarOpacityKey = floatPreferencesKey("navigationBarOpacity")
const val NAVIGATION_BAR_OPACITY_DEFAULT = 1.0f

val NavigationBarTransparencyKey = floatPreferencesKey("navigationBarTransparency")
const val NAVIGATION_BAR_TRANSPARENCY_DEFAULT = 0.0f

val NavigationBarLabelSpacingKey = floatPreferencesKey("navigationBarLabelSpacing")
const val NAVIGATION_BAR_LABEL_SPACING_DEFAULT = 4f

val NavigationBarCornerRadiusKey = floatPreferencesKey("navigationBarCornerRadius")
const val NAVIGATION_BAR_CORNER_RADIUS_DEFAULT = 28f

val HideScrollbarKey = booleanPreferencesKey("hideScrollbar")


val PlayerCustomImageUriKey = stringPreferencesKey("playerCustomImageUri")
val PlayerCustomBlurKey = floatPreferencesKey("playerCustomBlur")
val PlayerCustomContrastKey = floatPreferencesKey("playerCustomContrast")
val PlayerCustomBrightnessKey = floatPreferencesKey("playerCustomBrightness")

val LyricsTextSizeKey = floatPreferencesKey("lyricsTextSize")
val LyricsLineSpacingKey = floatPreferencesKey("lyricsLineSpacing")
val LyricsLineBlurKey = booleanPreferencesKey("lyricsLineBlur")

val EnhancedLyricsStyleKey = stringPreferencesKey("enhancedLyricsStyle")

enum class EnhancedLyricsStyle(val displayName: String) {
    ACCOMPANIST("Accompanist"),
    SPICY_MOBILE("Spicy Mobile"),
}

val OverscrollStyleKey = stringPreferencesKey("overscrollStyle")

enum class OverscrollStyle {
    IOS_RUBBER_BAND,
    ANDROID_STRETCH,
    OFF,
}

val OverscrollRubberBandTensionKey = floatPreferencesKey("overscrollRubberBandTension")
val OverscrollBounceStiffnessKey = floatPreferencesKey("overscrollBounceStiffness")

val BackAnimationStyleKey = stringPreferencesKey("backAnimationStyle")

enum class BackAnimationStyle { DEFAULT, PREDICTIVE }

val ShowLyricsPlayerControlsKey = booleanPreferencesKey("showLyricsPlayerControls")
val AutoHideLyricsPlayerControlsKey = booleanPreferencesKey("autoHideLyricsPlayerControls")

val TopSize = stringPreferencesKey("topSize")

const val HISTORY_DURATION_DEFAULT = 30
const val HISTORY_DURATION_MIN = 1
const val HISTORY_DURATION_MAX = 60
val HISTORY_DURATION_RANGE = HISTORY_DURATION_MIN.toFloat()..HISTORY_DURATION_MAX.toFloat()
val HISTORY_DURATION_LEGACY_FLOAT_KEY = floatPreferencesKey("historyDuration")
val HistoryDuration = intPreferencesKey("historyDuration")

const val PRELOAD_SONGS_MAX = 10
val PRELOAD_SONGS_RANGE = 0f..PRELOAD_SONGS_MAX.toFloat()
val PreloadSongsCountKey = intPreferencesKey("preloadSongsCount")

const val DEFAULT_PRELOAD_SONGS_COUNT = 2

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val LyricsBackgroundStyleKey = stringPreferencesKey("lyricsBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizeOtherLanguagesKey = booleanPreferencesKey("lyricsRomanizeOtherLanguages")
val LyricsModeKey = stringPreferencesKey("lyricsMode")
val LyricsV2BounceFactorKey = floatPreferencesKey("lyricsV2BounceFactor")
val LyricsV2GlowFactorKey = floatPreferencesKey("lyricsV2GlowFactor")
val LyricsV2FillTransitionWidthKey = floatPreferencesKey("lyricsV2FillTransitionWidth")
val LyricsV2LrcBounceEnabledKey = booleanPreferencesKey("lyricsV2LrcBounceEnabled")

enum class LyricsMode {
    V2,
    ENHANCED,
    SPOTIFY,
}

val QueueLyricsPreloadCountKey = intPreferencesKey("queue_lyrics_preload_count")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")

val DefaultMetadataSourceKey = stringPreferencesKey("defaultMetadataSource")
val DefaultSearchSourceKey = stringPreferencesKey("defaultSearchSource")

enum class MetadataSource {
    YOUTUBE,
    SPOTIFY,
}

enum class SearchProvider {
    YOUTUBE,
    SPOTIFY,
    APPLE_MUSIC,
    SOUNDCLOUD,
}

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle() =
        when (this) {
            LOCAL -> ONLINE
            ONLINE -> LOCAL
        }
}

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")

val InnerTubeOAuthTokenKey = stringPreferencesKey("innerTubeOAuthToken")
val InnerTubeOAuthRefreshTokenKey = stringPreferencesKey("innerTubeOAuthRefreshToken")

val InnerTubeOAuthExpiresAtKey = longPreferencesKey("innerTubeOAuthExpiresAt")

val PoTokenKey = stringPreferencesKey("poToken")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")

val AccountImageUrlKey = stringPreferencesKey("accountImageUrl")
val SavedAccountsKey = stringPreferencesKey("savedAccounts")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")
val SpotifySpDcKey = stringPreferencesKey("spotify_sp_dc")
val SpotifySpKeyKey = stringPreferencesKey("spotify_sp_key")
val SpotifyAccessTokenKey = stringPreferencesKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longPreferencesKey("spotify_access_token_expires_at")
val SpotifyAccountNameKey = stringPreferencesKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringPreferencesKey("spotify_account_avatar_url")
val ShowSpotifyPlaylistsKey = booleanPreferencesKey("show_spotify_playlists")

val LibrarySourceKey = stringPreferencesKey("library_source")

val AppleMusicExperienceKey = booleanPreferencesKey("apple_music_experience")

val StyleBeforeAppleMusicKey = stringPreferencesKey("style_before_apple_music")
val SpotifyLibraryPlaylistsCacheKey = stringPreferencesKey("spotify_library_playlists_cache")

val SpotifyHiddenPlaylistIdsKey = stringSetPreferencesKey("spotify_hidden_playlist_ids")

val HiddenHomeItemsKey = stringSetPreferencesKey("hidden_home_items")

val TidalCookieKey = stringPreferencesKey("tidalCookie")
val TidalEnabledKey = booleanPreferencesKey("tidalEnabled")
val TidalAudioQualityKey = stringPreferencesKey("tidalAudioQuality")
val TidalArtworkFallbackEnabledKey = booleanPreferencesKey("tidalArtworkFallbackEnabled")
val TidalAccountNameKey = stringPreferencesKey("tidal_account_name")

val TidalInstancesKey = stringPreferencesKey("tidalInstances")

val TidalVerifiedInstancesKey = stringPreferencesKey("tidalVerifiedInstances")

val TidalLastProbeTrackKey = stringPreferencesKey("tidalLastProbeTrack")

val TidalAccessTokenKey = stringPreferencesKey("tidalAccessToken")
val TidalRefreshTokenKey = stringPreferencesKey("tidalRefreshToken")
val TidalTokenExpiryKey = longPreferencesKey("tidalTokenExpiry")
val TidalSubscriptionKey = stringPreferencesKey("tidalSubscription")

val TidalAuthFlowKey = stringPreferencesKey("tidalAuthFlow")

val TidalCountryCodeKey = stringPreferencesKey("tidalCountryCode")
val TidalUserIdKey = longPreferencesKey("tidalUserId")

val TidalNeedsReloginKey = booleanPreferencesKey("tidalNeedsRelogin")

enum class TidalSubscriptionStatus {
    UNKNOWN,
    PREMIUM,
    FREE,
}

enum class TidalAudioQuality {
    AAC_320,
    FLAC,
    HI_RES_LOSSLESS,
}

enum class ReplayGainMode {
    OFF,
    TRACK,
    ALBUM,
}

enum class AppleMusicQuality {
    AAC,
    LOSSLESS,
    HI_RES_LOSSLESS,
}

val AppleMusicQualityKey = stringPreferencesKey("appleMusicQuality")

val AppleMusicSourceEnabledKey = booleanPreferencesKey("appleMusicSourceEnabled")

val TidalAudioQualityOptions =
    listOf(
        TidalAudioQuality.AAC_320,
        TidalAudioQuality.FLAC,
        TidalAudioQuality.HI_RES_LOSSLESS,
    )

val QobuzEnabledKey = booleanPreferencesKey("qobuzEnabled")

val QobuzBackupEnabledKey = booleanPreferencesKey("qobuzBackupEnabled")

val QobuzBackupEndpointsKey = stringPreferencesKey("qobuzBackupEndpoints")

val QobuzInstancesKey = stringPreferencesKey("qobuzInstances")

val QobuzVerifiedInstancesKey = stringPreferencesKey("qobuzVerifiedInstances")

val ManualSourceLoginEnabledKey = booleanPreferencesKey("dev_manual_source_login")

val QobuzLastProbeTrackKey = stringPreferencesKey("qobuzLastProbeTrack")

val QobuzTokensKey = stringPreferencesKey("qobuzTokens")

val QobuzVerifiedTokensKey = stringPreferencesKey("qobuzVerifiedTokens")

enum class QobuzAudioQuality {
    FLAC,
    HI_RES,
    MAX,
}

val QobuzAudioQualityOptions =
    listOf(
        QobuzAudioQuality.FLAC,
        QobuzAudioQuality.HI_RES,
        QobuzAudioQuality.MAX,
    )

val QobuzAudioQualityKey = stringPreferencesKey("qobuzAudioQuality")

fun QobuzAudioQuality.toFormatId(): Int =
    when (this) {
        QobuzAudioQuality.FLAC -> 6
        QobuzAudioQuality.HI_RES -> 7
        QobuzAudioQuality.MAX -> 27
    }

val TelegramAccountNameKey = stringPreferencesKey("telegramAccountName")
val TelegramAccountPhoneKey = stringPreferencesKey("telegramAccountPhone")

val TelegramLosslessOnlyKey = booleanPreferencesKey("telegramLosslessOnly")

val TelegramBotsKey = stringPreferencesKey("telegramBots")

val TelegramBotForwardToChannelKey = booleanPreferencesKey("telegramBotForwardToChannel")

enum class AudioSourceType {
    TIDAL,
    QOBUZ,
    QOBUZ_BACKUP,
    DEEZER,
    APPLE,
    JIOSAAVN,
    SOUNDCLOUD,
    SPOTIFY,
    YOUTUBE,
}

val AudioSourceOrderKey = stringPreferencesKey("audioSourceOrder")

val SongSourceOverrideKey = stringPreferencesKey("songSourceOverride")

val SongSourceQobuzTrackIdKey = stringPreferencesKey("songSourceQobuzTrackId")
val SongSourceDeezerTrackIdKey = stringPreferencesKey("songSourceDeezerTrackId")

val SongSourceTidalTrackIdKey = stringPreferencesKey("songSourceTidalTrackId")

val SongSourceQobuzBackupVideoIdKey = stringPreferencesKey("songSourceQobuzBackupVideoId")

val SoundCloudEnabledKey = booleanPreferencesKey("soundcloudEnabled")

val SoundCloudAudioQualityKey = stringPreferencesKey("soundcloudAudioQuality")

enum class SoundCloudAudioQuality {
    MP3_128,
    AAC_96,
    AAC_160,
}

val SoundCloudAudioQualityOptions = listOf(
    SoundCloudAudioQuality.MP3_128,
    SoundCloudAudioQuality.AAC_96,
    SoundCloudAudioQuality.AAC_160,
)

val SoundCloudAuthTokenKey = stringPreferencesKey("soundcloudAuthToken")

val SpotifyDirectEnabledKey = booleanPreferencesKey("spotifyDirectEnabled")

val SpotifyDirectQualityKey = stringPreferencesKey("spotifyDirectQuality")

enum class SpotifyDirectQuality {
    AAC_128,
}

val SpotifyDirectQualityOptions = listOf(
    SpotifyDirectQuality.AAC_128,
)

val SpotifyResolveMethodKey = stringPreferencesKey("spotifyResolveMethod")

enum class SpotifyResolveMethod {
    STOPIFY,
    ECHO,
}

val SpotifyResolveMethodOptions = listOf(
    SpotifyResolveMethod.STOPIFY,
    SpotifyResolveMethod.ECHO,
)

val SpotifyDeviceIdKey = stringPreferencesKey("spotifyDeviceId")

val AudioSearchSourceKey = stringPreferencesKey("audioSearchSource")

val TidalAccountFirstKey = booleanPreferencesKey("tidalAccountFirst")

val PoolApiKeyKey = stringPreferencesKey("poolApiKey")

val DeezerEnabledKey = booleanPreferencesKey("deezerEnabled")

val DeezerArlKey = stringPreferencesKey("deezerArl")

val DeezerAccountNameKey = stringPreferencesKey("deezerAccountName")

val DeezerAccountPremiumKey = booleanPreferencesKey("deezerAccountPremium")

val DeezerInstancesKey = stringPreferencesKey("deezerInstances")

val EnableDeezerLyricsKey = booleanPreferencesKey("enableDeezerLyrics")

val JioSaavnEnabledKey = booleanPreferencesKey("enableSaavnStreaming")

val SaavnAudioQualityKey = stringPreferencesKey("saavnAudioQuality")

enum class SaavnAudioQuality {
    QUALITY_320,
    QUALITY_160,
    QUALITY_96,
    ;

    fun toApiValue(): String =
        when (this) {
            QUALITY_320 -> "320kbps"
            QUALITY_160 -> "160kbps"
            QUALITY_96 -> "96kbps"
        }

    fun toLabel(): String =
        when (this) {
            QUALITY_320 -> "320 kbps"
            QUALITY_160 -> "160 kbps"
            QUALITY_96 -> "96 kbps"
        }

    companion object {
        fun fromStoredName(name: String?): SaavnAudioQuality =
            runCatching { valueOf(name?.uppercase() ?: "") }.getOrDefault(QUALITY_320)
    }
}

val WebClientPoTokenEnabledKey = booleanPreferencesKey("webClientPoTokenEnabled")
val PoTokenGvsKey = stringPreferencesKey("poTokenGvs")
val PoTokenPlayerKey = stringPreferencesKey("poTokenPlayer")
val UseVisitorDataKey = booleanPreferencesKey("useVisitorData")
val PoTokenSourceUrlKey = stringPreferencesKey("poTokenSourceUrl")

val LanguageCodeToName =
    mapOf(
        "en" to "English (US)",
        "en-GB" to "English (UK)",
        "en-NG" to "English (Nigeria)",
        "ja" to "日本語",
        "ko" to "한국어",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁���中文",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "uk" to "Українська",
        "cs" to "Čeština",
        "el" to "Ελληνικά",
        "he" to "עברית",
        "hu" to "Magyar",
        "ro" to "Română",
        "fi" to "Suomi",
        "da" to "Dansk",
        "no" to "Norsk",
        "sv" to "Svenska",
        "sk" to "Slovenčina",
        "bg" to "Български",
        "hr" to "Hrvatski",
        "sr" to "Срpsки",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "et" to "Eesti",
    )

val CountryCodeToName =
    mapOf(
        "JP" to "Japan",
        "KR" to "South Korea",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CN" to "China",
        "TW" to "Taiwan",
        "HK" to "Hong Kong",
        "FR" to "France",
        "DE" to "Germany",
        "ES" to "Spain",
        "MX" to "Mexico",
        "BR" to "Brazil",
        "RU" to "Russia",
        "IT" to "Italy",
        "NL" to "Netherlands",
        "PL" to "Poland",
        "TR" to "Turkey",
        "AU" to "Australia",
        "CA" to "Canada",
        "IN" to "India",
        "ID" to "Indonesia",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "NG" to "Nigeria",
        "PH" to "Philippines",
        "MY" to "Malaysia",
        "SG" to "Singapore",
        "AR" to "Argentina",
        "CL" to "Chile",
        "CO" to "Colombia",
        "PE" to "Peru",
        "ZA" to "South Africa",
        "EG" to "Egypt",
        "SA" to "Saudi Arabia",
        "AE" to "United Arab Emirates",
    )

val LaunchCountKey = intPreferencesKey("launch_count")
val OnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

val OnboardingCurrentPageKey = intPreferencesKey("onboarding_current_page")
val HasPressedStarKey = booleanPreferencesKey("has_pressed_star")
val RemindAfterKey = intPreferencesKey("remind_after")

val EnableUpdateNotificationKey = booleanPreferencesKey("enableUpdateNotification")
val UpdateChannelKey = stringPreferencesKey("updateChannel")
val LastUpdateCheckKey = longPreferencesKey("lastUpdateCheck")
val YtDlpManualUpdateHistoryKey = stringSetPreferencesKey("ytDlpManualUpdateHistory")
val LastNotifiedVersionKey = stringPreferencesKey("lastNotifiedVersion")

val SeenNewReleaseIdsKey = stringPreferencesKey("seenNewReleaseIds")

val ReadNewReleaseIdsKey = stringPreferencesKey("readNewReleaseIds")

val PresaveReleaseRadarKey = booleanPreferencesKey("presaveReleaseRadar")

val GitHubContributorsEtagKey = stringPreferencesKey("github_contributors_etag")
val GitHubContributorsJsonKey = stringPreferencesKey("github_contributors_json")
val GitHubContributorsLastCheckedAtKey = longPreferencesKey("github_contributors_last_checked_at")
val GitHubTranslationContributorsJsonKey = stringPreferencesKey("github_translation_contributors_json")
val GitHubTranslationContributorsLastCheckedAtKey = longPreferencesKey("github_translation_contributors_last_checked_at")

val GitHubReleasesEtagKey = stringPreferencesKey("github_releases_etag")
val GitHubReleasesJsonKey = stringPreferencesKey("github_releases_json")
val GitHubReleasesLastCheckedAtKey = longPreferencesKey("github_releases_last_checked_at")
val GitHubReleasesFingerprintKey = stringPreferencesKey("github_releases_fingerprint")

val CanaryReleasesEtagKey = stringPreferencesKey("daily_nightly_releases_etag")
val CanaryReleasesJsonKey = stringPreferencesKey("daily_nightly_releases_json")
val CanaryReleasesLastCheckedAtKey = longPreferencesKey("daily_nightly_releases_last_checked_at")
val CanaryReleasesFingerprintKey = stringPreferencesKey("daily_nightly_releases_fingerprint")

enum class UpdateChannel {
    STABLE,
    CANARY,
    ;

    companion object {
        fun fromStoredName(
            value: String?,
            defaultValue: UpdateChannel,
        ): UpdateChannel =
            when (value) {
                "NIGHTLY", "DAILY_NIGHTLY" -> CANARY
                else -> entries.firstOrNull { it.name == value } ?: defaultValue
            }
    }
}

val VideoAmbientModeKey = booleanPreferencesKey("videoAmbientMode")

val VideoPlaybackSpeedKey = floatPreferencesKey("videoPlaybackSpeed")

val VideoQualityPreferredHeightKey = intPreferencesKey("videoQualityPreferredHeight")

enum class VideoAspectRatio {
    FIT,
    CROP,
    STRETCH,
    FILL,
    ;

    fun toExoResizeMode(): Int = when (this) {
        FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        CROP -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    }
}

val VideoAspectRatioKey = stringPreferencesKey("videoAspectRatio")

val RecommendationsEnabledKey = booleanPreferencesKey("recommendationsEnabled")

val RecommendationRecencyWeightKey = floatPreferencesKey("recommendationRecencyWeight")

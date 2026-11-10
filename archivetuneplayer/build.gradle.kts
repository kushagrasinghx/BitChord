/*
 * ArchiveTune's whole player stack, ported one-to-one.
 *
 * Every file under src/main/kotlin/moe/rukamori/archivetune is a byte-for-byte
 * copy of ArchiveTune's (github.com/4nx3b/ArchiveTune, dev @ d08d8da94):
 * Player.kt and its eleven design styles, every queue sheet, lyrics screen,
 * lottie icon and transition they draw. The package name is kept so the
 * sources compile against each other unmodified.
 *
 * What is NOT copied is the app-service layer those files talk to — the
 * MediaService, the Room database, the preference DataStore, the lyrics
 * pipeline, the download manager. Each is reimplemented here as a thin bridge
 * (see the files with a "bridge" banner) that forwards to this app's real
 * implementations, so the player runs on BitChord's playback while looking
 * and behaving exactly like ArchiveTune's.
 *
 * Library versions follow the app's own pins (Compose BOM 2024.12.01 with
 * foundation 1.10.0, material3 1.3.1, haze 1.3.1, coil 3.0.4, media3 1.11.0)
 * so adding this module never moves the versions the rest of the app
 * compiled against. Where ArchiveTune's stack needs a library this app
 * doesn't use (lottie, reorderable, accompanist-lyrics, squigglyslider,
 * backdrop, datastore, timber, materialkolor), it is declared here at the
 * newest version that still compiles against those pins.
 */
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "moe.rukamori.archivetune"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        // The two buildConfig fields the copied sources read (the account
        // pool the tidal/qobuz providers authenticate against). Same defaults
        // as ArchiveTune's app; overridable from local.properties/env.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use(::load)
        }
        val sourceProviderUrl = (
            localProps.getProperty("SOURCE_PROVIDER_URL")?.takeIf { it.isNotBlank() }
                ?: System.getenv("SOURCE_PROVIDER_URL")?.takeIf { it.isNotBlank() }
                ?: "https://archivepool.vercel.app"
            ).trim().trimEnd('/')
        buildConfigField("String", "SOURCE_PROVIDER_URL", "\"$sourceProviderUrl\"")
        val sourceProviderKey = (
            localProps.getProperty("SOURCE_PROVIDER_KEY")
                ?: System.getenv("SOURCE_PROVIDER_KEY")
                ?: ""
            ).trim()
        buildConfigField("String", "SOURCE_PROVIDER_KEY", "\"$sourceProviderKey\"")
        val poolClientKey = (
            localProps.getProperty("POOL_CLIENT_KEY")
                ?: System.getenv("POOL_CLIENT_KEY")
                ?: ""
            ).trim()
        buildConfigField("String", "POOL_CLIENT_KEY", "\"$poolClientKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // The copied sources opt in file-locally where they need to; the
        // module-level opt-ins below cover the handful that don't.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
        )
    }
}

dependencies {
    // ArchiveTune's own modules, vendored under archivetune/.
    api(project(":archivetune:core"))
    implementation(project(":archivetune:canvas"))
    implementation(project(":archivetune:betterlyrics"))
    implementation(project(":archivetune:jiosaavn"))
    implementation(project(":archivetune:lastfm"))
    implementation(project(":archivetune:spotifycore"))

    // ---- Compose, on the app's pins ----
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-util")
    // ArchiveTune's own pin: the ten styles draw material3's expressive
    // widgets (LoadingIndicator, the wavy progress indicators, ToggleButton
    // and ButtonGroup, MaterialShapes, MotionScheme), which are public only
    // from the 1.5 line on — and 1.5.0-alpha23's foundation requirement
    // stays within what this app already pins.
    implementation("androidx.compose.material3:material3:1.5.0-alpha23")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.compose.runtime:runtime")
    // The morphing shapes behind the expressive controls.
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    // The share dialog's window breakpoints (AT's pin).
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0-rc01")

    // ---- AndroidX the player stack leans on ----
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    // The copied entities carry Room annotations; only the annotation
    // artifact is needed — no database is built in this module.
    implementation("androidx.room:room-common:2.6.1")
    // SimpleSQLiteQuery, which the copied string extensions build.
    implementation("androidx.sqlite:sqlite:2.5.0")
    // The Apache helpers the entities generate ids with.
    implementation("org.apache.commons:commons-lang3:3.20.0")
    // Japanese lyric romanisation's tokenizer core (the language pack the
    // manager downloads carries the dictionary).
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    // ---- Media3 (the bridge hands a MediaController to the copied player,
    //      which consumes it as a plain media3 Player) ----
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.media3:media3-datasource:1.11.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-ui-compose:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    // ---- Images ----
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // ---- Libraries ArchiveTune's stack needs that the app doesn't use ----
    // Karaoke lyrics rendering (LyricsV2 / the flamingo and tiktok styles).
    implementation("com.mocharealm.accompanist:lyrics-ui:1.0.19")
    implementation("com.mocharealm.accompanist:lyrics-core:0.4.6")
    // The wavy seek bar.
    implementation("me.saket.squigglyslider:squigglyslider:1.0.0")
    // Liquid-glass effects behind the ported components.
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("com.valentinilk.shimmer:compose-shimmer:1.5.0")
    // Reorderable queues and lists.
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    // Lottie icons (the animated like button, download states).
    implementation("com.airbnb.android:lottie-compose:6.6.6")
    // Seed-grown schemes for the dynamic backgrounds. 2.1.0 is the last line
    // built against Compose 1.7, which is what this module compiles against.
    implementation("com.materialkolor:material-kolor:2.1.0")
    // Logging — the copied stack logs through Timber throughout.
    implementation("com.jakewharton.timber:timber:5.0.1")
    // Direct okhttp/ktor use inside the copied providers.
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("io.ktor:ktor-client-core:3.5.2")
    // The ktor OkHttp engine the AI text service and the lyric providers'
    // HTTP layer build their clients on.
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // The frost behind the ported components' glass surfaces — the app's
    // own pin; the player closure uses only its stable core API.
    implementation("dev.chrisbanes.haze:haze:1.3.1")
}

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.music.bitchord"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.music.bitchord"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "2.0.0-offline"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    flavorDimensions += "env"
    productFlavors {
        create("dev") { dimension = "env"; applicationId = "com.dev.bitchord"; resValue("string", "app_name", "BitChord Offline Dev") }
        create("prod") { dimension = "env"; resValue("string", "app_name", "BitChord Offline") }
    }
    signingConfigs {
        val store = signing.getProperty("storeFile")?.let { rootProject.file(it) }
        if (store != null && store.exists()) {
            create("release") {
                storeFile = store
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    sourceSets["main"].java {
        exclude("**/MainActivity.kt")
        exclude("**/ui/MainViewModel.kt")
        exclude("**/ui/screens/**")
        exclude("**/ui/replay/**")
        exclude("**/ui/components/**")
        exclude("**/ui/player/**")
        exclude("**/ui/icons/**")
        exclude("**/ui/haptics/**")
        exclude("**/ui/performance/**")
        exclude("**/ui/ForegroundState.kt")
        exclude("**/ui/LiquidGlassSurface.kt")
        exclude("**/ui/OfflineSettingsScreen.kt")
        exclude("**/ui/utils/**")
        exclude("**/auth/**")
        exclude("**/data/canvas/**")
        exclude("**/data/innertube/**")
        exclude("**/data/sources/**")
        exclude("**/data/scrobbling/**")
        exclude("**/data/lyrics/**")
        exclude("**/data/jiosaavn/**")
        exclude("**/data/stats/**")
        exclude("**/data/discord/**")
        exclude("**/data/AppUpdateChecker.kt")
        exclude("**/data/Http.kt")
        exclude("**/data/LikeState.kt")
        exclude("**/data/NerdStats.kt")
        exclude("**/data/TrackLog.kt")
        exclude("**/data/YtMusicRepository.kt")
        exclude("**/data/LocalMediaRepository.kt")
        exclude("**/data/settings/**")
        exclude("**/download/**")
        exclude("**/playback/AudioCache.kt")
        exclude("**/playback/AudioOutputPolicy.kt")
        exclude("**/playback/AudioOutputStatus.kt")
        exclude("**/playback/Autoplay.kt")
        exclude("**/playback/ChunkedDataSource.kt")
        exclude("**/playback/CrossfadeController.kt")
        exclude("**/playback/DynamicLruCacheEvictor.kt")
        exclude("**/playback/MusicLink.kt")
        exclude("**/playback/OriginalVersion.kt")
        exclude("**/playback/PlayerConnection.kt")
        exclude("**/playback/PlayerDeepLink.kt")
        exclude("**/playback/QualityUpgrade.kt")
        exclude("**/playback/QueueBuilder.kt")
        exclude("**/playback/QueueHistory.kt")
        exclude("**/playback/QueueShuffle.kt")
        exclude("**/playback/SleepTimer.kt")
        exclude("**/playback/SpatialAudioProcessor.kt")
        exclude("**/playback/StreamChoice.kt")
        exclude("**/playback/StreamContainer.kt")
        exclude("**/playback/TransitionFilterProcessor.kt")
        exclude("**/playback/smart/**")
        exclude("**/widget/**")
        exclude("**/com/my/kizzy/**")
        exclude("**/org/schabi/newpipe/**")
        exclude("**/offline/OfflineLocalLibrary.kt")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    testImplementation("junit:junit:4.13.2")
}

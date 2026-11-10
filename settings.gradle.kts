pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor is published via JitPack.
        maven("https://jitpack.io")
    }
}

rootProject.name = "BitChord"
include(":shared")
include(":sharedUi")

// The Android module only when there is an SDK to build it against. A machine
// set up for the desktop alone has none, and including it there fails at
// configuration time — before the desktop module it has nothing to do with can
// build at all.
// Read as a Properties file, not line by line: Android Studio writes the path
// escaped (`C\:\Users\...` on Windows), and only Properties undoes that.
val androidSdk: String? = file("local.properties")
    .takeIf { it.isFile }
    ?.let { f -> java.util.Properties().apply { f.inputStream().use(::load) } }
    ?.getProperty("sdk.dir")
    ?.trim()
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")

if (androidSdk != null && file(androidSdk).isDirectory) {
    include(":app")
    include(":tv")
    include(":wearos")
    // The vendored ArchiveTune player stack: the Android library plus the
    // pure-JVM modules its sources import (InnerTube core, canvas providers,
    // lyric parsers, JioSaavn). Desktop-only checkouts skip them along with
    // the app that consumes them.
    include(":archivetuneplayer")
    include(":archivetune:core")
    include(":archivetune:canvas")
    include(":archivetune:betterlyrics")
    include(":archivetune:jiosaavn")
} else {
    logger.lifecycle("No Android SDK found — building the desktop targets only.")
}

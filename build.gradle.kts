// Top-level build file where you can add configuration options common to all sub-projects/modules.
/*
 * The version of the phone app and of the watch app that ships inside it, in
 * one place. The watch has to move with the phone: it is installed and updated
 * from the phone's own APK (see app/build.gradle.kts), and Android refuses an
 * update whose versionCode is lower than the one installed.
 *
 * No "v" in the name — the in-app updater reads it as a number; the "v" is the
 * release tag's alone.
 */
extra["bitchordVersionCode"] = 42
extra["bitchordVersionName"] = "1.10"

/*
 * Bump this by hand before cutting each sideloaded test build ("beta2",
 * "beta3", ...) and blank it out before cutting the real release. Marks the
 * versionName above as a pre-release: AppUpdateChecker.isNewer() treats any
 * "-suffix" as older than a clean release of the same number, so testers
 * still get the update prompt once the matching tag is actually published.
 *
 * Applied to release builds as well as debug ones, and that is the whole
 * point of it. A sideloaded beta is a *release* build — signed with the real
 * key, installed over the real package — so leaving the marker off it is
 * exactly the case that strands a tester: their build calls itself 1.6.1,
 * the published 1.6.1 then matches it, isNewer() says no, and no prompt ever
 * comes. Blanking this line is the one step that turns a beta into a release,
 * so it is the one place to get right.
 *
 * The watch app carries the same marker. It needs no prompt logic of its own:
 * the phone compares the watch's installed APK with the one it carries, so a
 * beta watch app shows "Update available" as soon as the phone has the release.
 */
extra["bitchordBetaSuffix"] = ""

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("org.jetbrains.compose") version "1.10.3" apply false
}

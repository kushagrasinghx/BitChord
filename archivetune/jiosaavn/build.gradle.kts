/*
 * The JioSaavn client from ArchiveTune's :jiosaavn, vendored verbatim at
 * the pinned submodule commit. Upstream builds it as an Android library —
 * its sources use android.util.Base64 (the URL-safe, no-wrap flags JioSaavn
 * decryption needs) and android.util.Log — and so it stays one here, on
 * this app's JDK 17 instead of upstream's 21.
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "moe.rukamori.archivetune.jiosaavn"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-cio:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
}

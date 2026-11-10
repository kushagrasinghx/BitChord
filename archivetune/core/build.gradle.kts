/*
 * ArchiveTune's InnerTube + stream-resolution core, vendored verbatim from
 * 4nx3b/core at the commit ArchiveTune's `dev` pins (gitlink d08d8da94).
 *
 * A pure-JVM module: ktor + the two NewPipe-fork extractors + quickjs-kt for
 * the local cipher solver. Kept as its own module so the player library can
 * depend on `moe.rukamori.archivetune.innertube.*` exactly as ArchiveTune's
 * app does — no source changes were made beyond the toolchain pin below.
 *
 * Toolchain: 17, not ArchiveTune's 21 — this repo's CI runs on JDK 17 and no
 * toolchain resolver is configured to fetch another.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("io.ktor:ktor-client-encoding:3.5.2")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.3.2")
    // Tier 1/2 extraction: PipePipe under its own namespace, BravePipe under
    // the NewPipe namespace (see NewPipe.kt for the two API adaptations).
    implementation("com.github.maxrave-dev:PipePipeExtractor:f8982ca9e7")
    implementation("com.github.maxrave-dev:BravePipeExtractor:0fcaf19ecc")
    implementation("io.github.dokar3:quickjs-kt:1.0.14")
}

// Copied verbatim from ArchiveTune's :core build: PipePipe brings
// protobuf-java (full) while Brave brings protobuf-javalite; both occupy
// com.google.protobuf.* and trip DEX duplicate-class checks. Javalite wins.
configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

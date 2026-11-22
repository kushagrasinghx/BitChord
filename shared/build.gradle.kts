import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The web player: commonMain compiled to an ES module it imports (web/shared-kotlin).
    // nodejs(), not browser(): the module only needs compiling, and its tests run in Node.
    js {
        nodejs()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    // Android and desktop are both JVM, but the default hierarchy gives them no source set in
    // common.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Compile-only: the two applications supply the runtime artifact themselves.
                compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")
                // The network stack the lyrics providers and the YouTube Music
                // client run on, at the phone's versions — both applications
                // now call the same code, so they resolve the same libraries.
                api("io.ktor:ktor-client-core:3.5.2")
                api("io.ktor:ktor-client-okhttp:3.5.2")
                api("io.ktor:ktor-client-websockets:3.5.2")
                api("io.ktor:ktor-client-content-negotiation:3.5.2")
                api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                api("org.jsoup:jsoup:1.22.2")
                // YouTube stream resolution, as the phone does it: InnerTubeX's
                // client catalog and cipher tiers, NewPipe as the fallback. NewPipe
                // is compile-only because the phone ships a trimmed jar of it.
                api("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")
                compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            // Models in this module intentionally have no Android or Compose dependency.
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("com.squareup.okhttp3:mockwebserver:5.3.2")
        }
    }
}

android {
    namespace = "com.music.bitchord.shared"
    compileSdk = 36
    // Without a minSdk the library's manifest has no <uses-sdk>, which the
    // manifest merger reads as targetSdk < 4 and answers by granting every app
    // that depends on it READ_PHONE_STATE — a permission nothing here uses.
    defaultConfig {
        minSdk = 26
    }
}

/*
 * The web player's copy of commonMain, as JavaScript: `./gradlew :shared:exportWeb` writes it to
 * web/shared-kotlin/dist, which is committed so the site builds without a JDK (Cloudflare Pages
 * has Node only). CI regenerates it whenever shared/ changes (.github/workflows/web.yml).
 */
tasks.register<Sync>("exportWeb") {
    group = "build"
    description = "Compiles commonMain to JavaScript for the web player (web/shared-kotlin/dist)."
    val compile = tasks.named("compileProductionLibraryKotlinJs")
    dependsOn(compile, "jsProductionLibraryCompileSync")
    from(layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin")) {
        include("*.mjs", "*.d.mts")
        // The .map files aren't shipped, so nothing may point at them.
        filter { line -> if (line.startsWith("//# sourceMappingURL=")) "" else line }
        // Kotlin/JS lists a class's interfaces in no fixed order from one run to the next. The list
        // only answers `is` checks, so sorting it changes nothing and keeps the output identical
        // between runs, which CI's "is it up to date" comparison relies on.
        filter { line ->
            if (!line.startsWith("initMetadataFor")) line
            else line.replace(Regex("""\[([A-Za-z_$][\w$]*(?:, [A-Za-z_$][\w$]*)+)]""")) { m ->
                "[" + m.groupValues[1].split(", ").sorted().joinToString(", ") + "]"
            }
        }
    }
    into(rootProject.layout.projectDirectory.dir("web/shared-kotlin/dist"))
    // The polyfills at the top of the stdlib come out in a different order between machines
    // (startsWith and endsWith swap). Each stands alone, so they are sorted too.
    doLast {
        destinationDir.listFiles { f -> f.name.endsWith(".mjs") }?.forEach { file ->
            val text = file.readText()
            // Line endings are the machine's (Sync's filters write them), so either may appear.
            val region = Regex("//region block: polyfills\\r?\\n").find(text) ?: return@forEach
            val bodyStart = region.range.last + 1
            val end = text.indexOf("//endregion", bodyStart)
            val blocks = text.substring(bodyStart, end).split(Regex("(?m)(?=^if \\(typeof )")).filter { it.isNotEmpty() }
            file.writeText(text.substring(0, bodyStart) + blocks.sorted().joinToString("") + text.substring(end))
        }
    }
}

// The JS target uses the machine's Node.js (the web build needs one anyway) rather than
// downloading its own: settings.gradle.kts rejects the download repository Kotlin would add
// (FAIL_ON_PROJECT_REPOS). Android builds never run JS tasks, so they don't need Node.
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
}

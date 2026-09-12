// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
    }
    dependencies {
        /*
         * Overrides the D8/R8 that AGP 8.10.1 bundles (8.10.9). Gradle's conflict
         * resolution picks the higher version, so this is the whole mechanism.
         *
         * 8.10.x miscompiles `NowPlayingScreen`, which is large enough — 36
         * parameters, ~5,500 dex instructions, registers past v287 — to hit a
         * register-allocation bug in debug-mode dexing. Resolving phis at one of
         * the join points emits `const/16 v29, #int 48` over the register already
         * holding the `MeshPalette` from `rememberArtworkColors`, and the
         * `MeshGradientBackground` call downstream then reads it back as a
         * reference:
         *
         *   [0x116d] copy-reference v16<-v29 type=PositiveByteConstant
         *
         * ART's verifier rejects the whole class for that, so the dev build died
         * with a VerifyError the moment the player composed. The JVM bytecode
         * kotlinc produces is valid — the fault is entirely in the dex lowering,
         * and it is fixed as of 8.11.32. 8.13.x is the newest 8.x line, and also
         * the first to read Kotlin 2.3 @Metadata: 8.10.9 caps out at 2.2.0 and
         * warns "malformed kotlin.Metadata" on every class this project compiles.
         *
         * Only debug dexing is affected — release-mode allocation keeps the
         * palette in place, which is why prod builds were fine and only dev
         * crashed. Removable once AGP itself bundles something past 8.11.32.
         */
        classpath("com.android.tools:r8:8.13.23")
    }
}
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("org.jetbrains.compose") version "1.12.0" apply false
}

buildscript {
    repositories {
        google()
    }
    dependencies {
        // Overrides the D8/R8 that AGP bundles to fix the debug dexing bug
        classpath("com.android.tools:r8:8.13.23")
    }
}
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}

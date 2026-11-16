# Keep rules for the packaged release build.
#
# Shrinking only — see the build script. Everything here is something the shrinker cannot see being
# used, because it is reached by reflection, by a service loader, or from native code.

# The entry point.
-keep class com.music.bitchord.desktop.MainKt { *; }

# Kotlin metadata and coroutines' internals.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# kotlinx.serialization generates serializers and looks them up by name.
-keep,includedescriptorclasses class com.music.bitchord.**$$serializer { *; }
-keepclassmembers class com.music.bitchord.** {
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class com.music.bitchord.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Ktor and its engine, both found through ServiceLoader.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# JavaCPP resolves every binding class and native library by name.
-keep class org.bytedeco.** { *; }
-dontwarn org.bytedeco.**

# ONNX Runtime's JNI layer calls back into these.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Skiko and Compose's own native bridge.
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-dontwarn org.jetbrains.skiko.**

# dbus-java builds proxies from interfaces, and SQLite's driver is a service.
-keep class org.freedesktop.** { *; }
-keep class com.github.hypfvieh.** { *; }
-keep class org.sqlite.** { *; }
-dontwarn org.freedesktop.**
-dontwarn com.github.hypfvieh.**

# Both script engines are discovered rather than referenced.
-keep class org.mozilla.javascript.** { *; }
-keep class com.oracle.truffle.** { *; }
-keep class org.graalvm.** { *; }
-dontwarn org.graalvm.**
-dontwarn com.oracle.truffle.**

# NewPipe's extractor reflects over its service list.
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**

# Our own JNI entry points: the analyser's symbol names encode this package.
-keep class com.music.bitchord.playback.smart.** { *; }

# Anything with a native method, and whatever it is called from.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Referenced but not on the path, and never reached at runtime either.
#
# kotlin.concurrent.atomics is the experimental API kotlin-stdlib 2.3 ships references to without
# the classes; com.google.re2j is an optional regex backend Truffle will use if it finds one, and
# it is not on this classpath.
-dontwarn kotlin.concurrent.atomics.**
-dontwarn com.google.re2j.**
-dontwarn org.newsclub.net.unix.**
-dontwarn com.google.protobuf.**

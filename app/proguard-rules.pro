# R8 is on for release for speed, not size — see the note on isMinifyEnabled in
# build.gradle.kts. These rules keep it away from everything that finds classes
# by name at runtime, where a missing class shows up as a crash on a device
# rather than as a build error.

# Nothing is renamed. Stack traces stay readable, and every lookup by name —
# enum names in preferences, Media3 custom commands, library reflection — keeps
# working without a rule of its own.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# ---- Script engines and extractors ------------------------------------------
# Rhino compiles and runs YouTube's player JavaScript and binds Java classes into
# it reflectively; NewPipe and InnerTubeX drive it and parse with nanojson/jsoup.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class org.schabi.newpipe.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.metrolist.** { *; }
-keep class com.dokar.quickjs.** { *; }

# ---- Native and crypto --------------------------------------------------------
# ONNX Runtime and QuickJS are called from JNI, which R8 cannot see. SMBJ picks
# its BouncyCastle providers and event bus handlers reflectively.
-keep class ai.onnxruntime.** { *; }
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class org.bouncycastle.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# ---- Serialization --------------------------------------------------------------
# protobuf-lite reads its generated messages' fields reflectively.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# Ktor resolves serializers and engine pieces by type at runtime.
-keep class io.ktor.** { *; }
# Our own @Serializable models, whole: kotlinx.serialization's bundled rules
# cover the generated serializers, and keeping the classes too costs little.
-keep @kotlinx.serialization.Serializable class com.music.bitchord.** { *; }
-keep @kotlinx.serialization.Serializable class com.my.kizzy.** { *; }

# ---- WebView bridges ------------------------------------------------------------
# PoToken minting and the Spotify token page call back into these from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- JVM-only APIs ------------------------------------------------------------
# Referenced by desktop-only paths in Rhino (JSR-223, java.beans, dynalink),
# mbassy's EL filters, SMBJ's Kerberos and Ktor's IntelliJ debugger probe. None
# of them exist on Android and none of those paths run here — the debug build,
# which R8 never touches, has always lived without them.
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn javax.el.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.ietf.jgss.**

package com.music.bitchord

// String.prototype.normalize, as java.text.Normalizer is on the JVM.
actual fun String.nfd(): String = asDynamic().normalize("NFD") as String

actual fun String.nfkc(): String = asDynamic().normalize("NFKC") as String

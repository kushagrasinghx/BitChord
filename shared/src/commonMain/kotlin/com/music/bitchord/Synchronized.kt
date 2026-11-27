package com.music.bitchord

/**
 * `@Synchronized` for common code. On the JVM targets (Android, desktop) it is
 * kotlin.jvm.Synchronized itself (jvmSharedMain); the web player's JavaScript is
 * single-threaded, so there it has nothing to do and isn't declared.
 */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
expect annotation class Synchronized()

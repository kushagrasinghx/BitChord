/*
 * BRIDGE — javax.inject for the copied sources. This module runs no
 * dependency-injection framework; the annotations are inert markers so the
 * verbatim @Inject constructors compile. Construction goes through the
 * bridge's own factories instead.
 */
package javax.inject

@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject

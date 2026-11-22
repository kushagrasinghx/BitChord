package com.music.bitchord.data.stats

import kotlinx.serialization.Serializable

/** One preference, with the type it has to be restored as. */
@Serializable
data class PrefValue(
    val type: String,
    val value: String? = null,
    val values: List<String> = emptyList(),
) {
    fun decoded(): Any? = when (type) {
        BOOLEAN -> value?.toBooleanStrictOrNull()
        INT -> value?.toIntOrNull()
        LONG -> value?.toLongOrNull()
        FLOAT -> value?.toFloatOrNull()
        STRING -> value
        STRING_SET -> values.toSet()
        else -> null
    }

    companion object {
        /**
         * Numbers go out as strings on purpose. JSON has one number type and
         * every parser picks its own Kotlin type back off it, which is
         * exactly the ambiguity this whole class exists to remove.
         */
        fun of(value: Any?): PrefValue? = when (value) {
            is Boolean -> PrefValue(BOOLEAN, value.toString())
            is Int -> PrefValue(INT, value.toString())
            is Long -> PrefValue(LONG, value.toString())
            is Float -> PrefValue(FLOAT, value.toString())
            is String -> PrefValue(STRING, value)
            is Set<*> -> PrefValue(STRING_SET, values = value.filterIsInstance<String>())
            else -> null
        }

        private const val BOOLEAN = "bool"
        private const val INT = "int"
        private const val LONG = "long"
        private const val FLOAT = "float"
        private const val STRING = "string"
        private const val STRING_SET = "stringSet"
    }
}

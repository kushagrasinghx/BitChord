package com.music.bitchord.sync

import kotlinx.serialization.Serializable

/** A typed SharedPreferences value encoded without JSON number coercion. */
@Serializable
data class SyncValue(
    val type: String,
    val value: String? = null,
    val values: List<String> = emptyList(),
) {
    init {
        require(type in TYPES) { "Unsupported sync preference type: $type" }
        if (type == STRING_SET) {
            require(value == null) { "String-set sync value cannot have a scalar value" }
        } else {
            require(value != null && values.isEmpty()) { "Scalar sync value must have one value" }
            when (type) {
                BOOLEAN -> require(value == "true" || value == "false")
                INT -> require(value.toIntOrNull() != null)
                LONG -> require(value.toLongOrNull() != null)
                FLOAT -> require(value.toFloatOrNull()?.isFinite() == true)
                STRING -> require(value.length <= MAX_STRING_LENGTH)
            }
        }
        require(values.size <= MAX_STRING_SET_SIZE)
        require(values.all { it.length <= MAX_STRING_LENGTH })
    }

    companion object {
        private const val BOOLEAN = "bool"
        private const val INT = "int"
        private const val LONG = "long"
        private const val FLOAT = "float"
        private const val STRING = "string"
        private const val STRING_SET = "string_set"
        private const val MAX_STRING_LENGTH = 65_536
        private const val MAX_STRING_SET_SIZE = 2_048
        private val TYPES = setOf(BOOLEAN, INT, LONG, FLOAT, STRING, STRING_SET)

        fun fromPreference(value: Any?): SyncValue? = runCatching {
            when (value) {
                is Boolean -> SyncValue(BOOLEAN, value.toString())
                is Int -> SyncValue(INT, value.toString())
                is Long -> SyncValue(LONG, value.toString())
                is Float -> SyncValue(FLOAT, value.toString())
                is String -> SyncValue(STRING, value)
                is Set<*> -> {
                    if (value.any { it !is String }) return null
                    SyncValue(STRING_SET, values = value.filterIsInstance<String>().sorted())
                }
                else -> return null
            }
        }.getOrNull()
    }

    fun toPreference(): Any = when (type) {
        BOOLEAN -> requireNotNull(value).toBooleanStrict()
        INT -> requireNotNull(value).toInt()
        LONG -> requireNotNull(value).toLong()
        FLOAT -> requireNotNull(value).toFloat()
        STRING -> requireNotNull(value)
        STRING_SET -> values.toSet()
        else -> error("Unsupported sync preference type: $type")
    }
}

/** Lamport clock with a deterministic device-ID tie-breaker. */
@Serializable
data class FieldClock(val counter: Long, val deviceId: String) {
    init {
        require(counter >= 0) { "Sync clock cannot be negative" }
        require(deviceId.isNotBlank() && deviceId.length <= 128) { "Invalid sync device ID" }
    }

    fun compareTo(other: FieldClock): Int {
        val byCounter = counter.compareTo(other.counter)
        return if (byCounter != 0) byCounter else deviceId.compareTo(other.deviceId)
    }
}

/** A null [value] is a tombstone: this device explicitly removed the key. */
@Serializable
data class SyncField(val clock: FieldClock, val value: SyncValue?)

/** Versioned, per-key profile state. Unknown fields are retained for forward compatibility. */
@Serializable
data class SyncSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val fields: Map<String, SyncField> = emptyMap(),
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported sync schema: $schemaVersion" }
        require(fields.size <= MAX_FIELDS) { "Too many fields in sync snapshot" }
        require(fields.keys.all { it.isNotBlank() && it.length <= MAX_FIELD_NAME_LENGTH }) {
            "Invalid sync field name"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val MAX_FIELDS = 4_096
        private const val MAX_FIELD_NAME_LENGTH = 256
    }
}

/** Merge two snapshots deterministically; equal clocks with different values signal state corruption. */
fun mergeSnapshots(first: SyncSnapshot, second: SyncSnapshot): SyncSnapshot {
    require(first.schemaVersion == second.schemaVersion) { "Cannot merge different sync schemas" }
    val merged = (first.fields.keys + second.fields.keys).toSortedSet().associateWith { key ->
        val left = first.fields[key]
        val right = second.fields[key]
        when {
            left == null -> requireNotNull(right)
            right == null -> left
            else -> when (left.clock.compareTo(right.clock)) {
                1 -> left
                -1 -> right
                else -> {
                    require(left.value == right.value) { "Conflicting sync values share the same field clock" }
                    left
                }
            }
        }
    }
    return SyncSnapshot(first.schemaVersion, merged)
}

/**
 * Mark only changes made since [baseline] as a new local write. Call with the
 * last successfully synced values as the baseline, not the downloaded snapshot.
 */
fun recordLocalChanges(
    snapshot: SyncSnapshot,
    baseline: Map<String, SyncValue>,
    current: Map<String, SyncValue>,
    deviceId: String,
): SyncSnapshot {
    val changedKeys = (baseline.keys + current.keys).toSortedSet().filter { baseline[it] != current[it] }
    if (changedKeys.isEmpty()) return snapshot

    val lastCounter = snapshot.fields.values.maxOfOrNull { it.clock.counter } ?: 0L
    require(lastCounter < Long.MAX_VALUE) { "Sync clock exhausted" }
    val clock = FieldClock(lastCounter + 1, deviceId)
    val fields = snapshot.fields.toMutableMap()
    changedKeys.forEach { key -> fields[key] = SyncField(clock, current[key]) }
    return SyncSnapshot(snapshot.schemaVersion, fields)
}

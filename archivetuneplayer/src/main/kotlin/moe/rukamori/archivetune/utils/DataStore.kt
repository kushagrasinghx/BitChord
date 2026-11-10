/*
 * BRIDGE (replaces ArchiveTune's utils/DataStore.kt).
 *
 * Same API the copied sources call — context.dataStore, PreferenceStore,
 * rememberPreference / rememberEnumPreference — over this module's own
 * PreferenceDataStore, plus a hook table the hosting app can install
 * adapters into for the settings it already owns (the player design style
 * itself, the slider and button styles, ...). A bridged key reads and
 * writes through the host's store; everything else persists locally with
 * ArchiveTune's defaults.
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.extensions.toEnum
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "archivetune_player_settings",
)

/**
 * The host's window into the preference layer: register an adapter for a
 * key and that key reads, writes and observes through the host instead of
 * the local store. Strings are the wire format for enum-valued keys so the
 * adapter can map the two apps' enum names.
 */
object BridgedPreferences {
    @PublishedApi
    internal class Adapter<T>(
        val read: () -> T,
        val write: (T) -> Unit,
        val changes: Flow<T>,
    )

    @PublishedApi
    internal val adapters = ConcurrentHashMap<Preferences.Key<*>, Adapter<*>>()

    @PublishedApi
    internal val enumAdapters = ConcurrentHashMap<Preferences.Key<String>, Adapter<*>>()

    fun <T> bridge(
        key: Preferences.Key<T>,
        read: () -> T,
        write: (T) -> Unit,
        changes: Flow<T>,
    ) {
        adapters[key] = Adapter(read, write, changes)
    }

    /** The enum-valued variant: the key is a String in the store, the value the host hands back is the enum itself. */
    fun <T : Enum<T>> bridgeEnum(
        key: Preferences.Key<String>,
        read: () -> T,
        write: (T) -> Unit,
        changes: Flow<T>,
    ) {
        enumAdapters[key] = Adapter(read, write, changes)
    }

    @PublishedApi
    internal fun <T> adapterFor(key: Preferences.Key<T>): Adapter<T>? {
        @Suppress("UNCHECKED_CAST")
        return adapters[key] as? Adapter<T>
    }

    @PublishedApi
    internal fun <T : Enum<T>> enumAdapterFor(key: Preferences.Key<String>): Adapter<T>? {
        @Suppress("UNCHECKED_CAST")
        return enumAdapters[key] as? Adapter<T>
    }
}

object PreferenceStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var _prefs: Preferences? = null
    private val initialSnapshot = kotlinx.coroutines.CompletableDeferred<Preferences>()

    @Volatile
    private var started = false

    val isStarted: Boolean
        get() = started

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch {
                try {
                    context.applicationContext.dataStore.data.collect { preferences ->
                        _prefs = preferences
                        initialSnapshot.complete(preferences)
                    }
                } catch (error: Throwable) {
                    initialSnapshot.completeExceptionally(error)
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    reportException(error)
                }
            }
        }
    }

    val snapshot: Preferences?
        get() = _prefs

    suspend fun awaitSnapshot(): Preferences = snapshot ?: initialSnapshot.await()

    fun <T> get(key: Preferences.Key<T>): T? = snapshot?.get(key)

    fun launchEdit(
        dataStore: DataStore<Preferences>,
        block: MutablePreferences.() -> Unit,
    ) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs.block()
            }
        }
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? {
    val snapshot = PreferenceStore.snapshot
    if (snapshot != null) return snapshot[key]

    val loaded =
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(1500) {
                if (PreferenceStore.isStarted) {
                    PreferenceStore.awaitSnapshot()
                } else {
                    data.first()
                }
            }
        }
    return loaded?.get(key)
}

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = get(key) ?: defaultValue

suspend fun <T> DataStore<Preferences>.getAsync(key: Preferences.Key<T>): T? = data.first()[key]

suspend fun <T> DataStore<Preferences>.getAsync(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = data.first()[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val adapter = BridgedPreferences.adapterFor(key)
    if (adapter != null) {
        val state =
            adapter.changes
                .distinctUntilChanged()
                .collectAsStateWithLifecycle(initialValue = adapter.read())
        return remember {
            object : MutableState<T> {
                override var value: T
                    get() = state.value
                    set(value) {
                        adapter.write(value)
                    }

                override fun component1() = value

                override fun component2(): (T) -> Unit = { value = it }
            }
        }
    }

    val initial = remember { PreferenceStore.get(key) ?: defaultValue }

    val state =
        remember {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = initial)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val adapter = BridgedPreferences.enumAdapterFor<T>(key)
    if (adapter != null) {
        val state =
            adapter.changes
                .distinctUntilChanged()
                .collectAsStateWithLifecycle(initialValue = adapter.read())
        return remember {
            object : MutableState<T> {
                override var value: T
                    get() = state.value
                    set(value) {
                        adapter.write(value)
                    }

                override fun component1() = value

                override fun component2(): (T) -> Unit = { value = it }
            }
        }
    }

    val initial = remember { PreferenceStore.get(key).toEnum(defaultValue = defaultValue) }

    val state =
        remember {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = initial)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value.name
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

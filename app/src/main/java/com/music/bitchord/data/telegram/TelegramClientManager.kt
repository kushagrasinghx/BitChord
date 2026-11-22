package com.music.bitchord.data.telegram

import android.content.Context
import android.os.Build
import android.util.Base64
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** One in-process TDLib client shared by settings, indexing, search and playback. */
object TelegramClientManager {
    private const val TAG = "TelegramMusic"
    private const val PAGE_SIZE = 100
    private const val MAX_CHATS = 2000
    private const val INITIALIZATION_TIMEOUT_MS = 30_000L
    private const val CLOSE_TIMEOUT_MS = 5_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private val secretsMutex = Mutex()
    private val chatsById = ConcurrentHashMap<Long, TelegramChat>()
    private val fileUpdates = ConcurrentHashMap<Int, MutableStateFlow<TdApi.File?>>()
    private val fileReadLocks = ConcurrentHashMap<Int, Mutex>()
    private data class AuthorizationUpdate(
        val generation: Long,
        val state: TdApi.AuthorizationState,
        val credentials: TelegramSecrets,
        val clientCreated: CompletableDeferred<Unit>,
    )
    private val authorizationUpdates = Channel<AuthorizationUpdate>(Channel.UNLIMITED)
    private val _auth = MutableStateFlow<TelegramAuthStatus>(TelegramAuthStatus.CredentialsRequired)
    private val _chats = MutableStateFlow<List<TelegramChat>>(emptyList())
    private val _indexingStatus = MutableStateFlow<String?>(null)
    private val _updates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = 128)
    val auth: StateFlow<TelegramAuthStatus> = _auth.asStateFlow()
    val chats: StateFlow<List<TelegramChat>> = _chats.asStateFlow()
    val indexingStatus: StateFlow<String?> = _indexingStatus.asStateFlow()
    val updates: SharedFlow<TdApi.Update> = _updates.asSharedFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var client: Client? = null
    @Volatile private var secrets: TelegramSecrets? = null
    private lateinit var secureStore: TelegramSecureStore
    private lateinit var index: TelegramIndexDatabase
    private lateinit var sessionDirectory: File
    private var parametersSent = false
    @Volatile private var clientGeneration = 0L
    @Volatile private var clientCreationGate: CompletableDeferred<Unit>? = null
    private var initializationTimeout: Job? = null

    init {
        // TDLib may publish the first auth update before Client.create returns.
        // Process updates in order, after the client reference is installed.
        scope.launch {
            for (update in authorizationUpdates) {
                update.clientCreated.await()
                if (update.generation == clientGeneration) {
                    handleAuthorization(update.state, update.credentials)
                }
            }
        }
    }

    val isReady: Boolean get() = _auth.value is TelegramAuthStatus.Ready
    val hasSavedCredentials: Boolean get() = secrets?.apiId != null && !secrets?.apiHash.isNullOrBlank()
    val selectedChatIds: Set<Long> get() = secrets?.selectedChatIds.orEmpty()
    val indexDatabase: TelegramIndexDatabase get() = index

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val app = context.applicationContext
        secureStore = TelegramSecureStore(app)
        val root = File(app.noBackupFilesDir, "telegram").apply { mkdirs() }
        sessionDirectory = File(root, "tdlib")
        index = TelegramIndexDatabase(app)
        try {
            secrets = secureStore.read()
            val saved = secrets
            if (saved?.apiId != null && saved.apiId > 0 && saved.apiHash.isNotBlank()) {
                _auth.value = TelegramAuthStatus.Starting
                scope.launch { startClient(saved) }
            }
        } catch (error: Throwable) {
            // Do not silently discard a session if Keystore decryption fails.
            _auth.value = TelegramAuthStatus.Error(
                "Telegram's encrypted local credentials could not be opened. Clear the local Telegram session to reconnect.",
            )
            TrackLog.w(TAG, "encrypted Telegram settings could not be read (${error.javaClass.simpleName})")
        }
    }

    suspend fun connect(apiIdText: String, apiHashText: String) {
        val apiId = apiIdText.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Enter the numeric Telegram API ID.")
        val apiHash = apiHashText.trim()
        require(apiHash.matches(Regex("[A-Fa-f0-9]{32}"))) {
            "Enter the 32-character Telegram API hash from my.telegram.org."
        }
        closeClientForRetry()
        secretsMutex.withLock {
            val current = secrets
            val key = current?.databaseKey?.takeIf { it.isNotBlank() } ?: ByteArray(32).also(SecureRandom()::nextBytes)
                .let { Base64.encodeToString(it, Base64.NO_WRAP) }
            val next = TelegramSecrets(apiId, apiHash, key, current?.selectedChatIds.orEmpty())
            secureStore.write(next)
            secrets = next
            _auth.value = TelegramAuthStatus.Starting
        }
        startClient(requireNotNull(secrets))
    }

    suspend fun retryInitialization() {
        val saved = secrets?.takeIf { it.apiId > 0 && it.apiHash.isNotBlank() }
            ?: throw IllegalStateException("Enter your Telegram API credentials to retry.")
        closeClientForRetry()
        _auth.value = TelegramAuthStatus.Starting
        startClient(saved)
    }

    /** Close a failed client without deleting its TDLib database or saved credentials. */
    private suspend fun closeClientForRetry() = startMutex.withLock {
        val active = client ?: return@withLock
        initializationTimeout?.cancel()
        try {
            withTimeout(CLOSE_TIMEOUT_MS) {
                val answer = CompletableDeferred<Unit>()
                active.send(TdApi.Close()) { result ->
                    if (result is TdApi.Error) answer.completeExceptionally(IOException("TDLib close failed."))
                    else answer.complete(Unit)
                }
                answer.await()
            }
        } catch (error: Throwable) {
            throw IOException("TDLib did not stop cleanly. Restart BitChord before retrying.", error)
        }
        client = null
        clientGeneration++ // Discard callbacks queued by the failed instance.
        clientCreationGate = null
        parametersSent = false
    }

    suspend fun submitPhoneNumber(phone: String) {
        val normalized = phone.trim().replace(" ", "")
        require(normalized.matches(Regex("\\+[1-9][0-9]{6,14}"))) { "Enter a phone number with its country code, such as +14155550123." }
        send(TdApi.SetAuthenticationPhoneNumber(normalized, null))
    }

    suspend fun submitCode(code: String) {
        val normalized = code.trim().replace(" ", "")
        require(normalized.matches(Regex("[0-9]{3,10}"))) { "Enter the verification code Telegram sent you." }
        send(TdApi.CheckAuthenticationCode(normalized))
    }

    suspend fun submitEmailAddress(email: String) {
        val normalized = email.trim()
        require(normalized.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Enter the email address linked to your Telegram account." }
        send(TdApi.SetAuthenticationEmailAddress(normalized))
    }

    suspend fun submitEmailCode(code: String) {
        val normalized = code.trim().replace(" ", "")
        require(normalized.matches(Regex("[0-9]{3,10}"))) { "Enter the code Telegram sent to your email." }
        send(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(normalized)))
    }

    suspend fun submitPassword(password: String) {
        require(password.isNotEmpty()) { "Enter your Telegram two-step verification password." }
        send(TdApi.CheckAuthenticationPassword(password))
    }

    suspend fun refreshChats(): List<TelegramChat> = withContext(Dispatchers.IO) {
        ensureReady()
        val lists = listOf<TdApi.ChatList>(TdApi.ChatListMain(), TdApi.ChatListArchive())
        val discovered = mutableSetOf<Long>()
        var complete = true
        for (chatList in lists) {
            var previousSize = -1
            var rounds = 0
            while (rounds++ < MAX_CHATS / PAGE_SIZE) {
                runCatching { send(TdApi.LoadChats(chatList, PAGE_SIZE)) }
                val ids = try {
                    send(TdApi.GetChats(chatList, MAX_CHATS)).chatIds
                } catch (_: Throwable) {
                    complete = false
                    break
                }
                ids.forEach { id ->
                    discovered += id
                    val chat = runCatching { send(TdApi.GetChat(id)) }.getOrNull()
                    if (chat == null) {
                        complete = false
                    } else {
                        cacheChat(chat, publish = false)
                    }
                }
                if (ids.size >= MAX_CHATS) {
                    complete = false
                    break
                }
                if (ids.size <= previousSize) break
                previousSize = ids.size
            }
        }
        if (complete) {
            chatsById.keys.retainAll(discovered)
            secretsMutex.withLock {
                val current = secrets
                if (current != null) {
                    val selected = current.selectedChatIds intersect chatsById.keys
                    if (selected != current.selectedChatIds) {
                        val next = current.copy(selectedChatIds = selected)
                        secureStore.write(next)
                        secrets = next
                    }
                }
            }
        }
        publishChats()
        _chats.value
    }

    suspend fun setChatSelected(chatId: Long, selected: Boolean) = withContext(Dispatchers.IO) {
        secretsMutex.withLock {
            val current = requireNotNull(secrets) { "Connect Telegram first." }
            val next = current.copy(selectedChatIds = updateTelegramSelection(current.selectedChatIds, chatId, selected))
            secureStore.write(next)
            secrets = next
        }
        if (!selected) index.removeChats(setOf(chatId))
        if (selected) TelegramIndexWorker.enqueue(requireNotNull(appContext))
        publishChats()
    }

    suspend fun reindexSelected() {
        val selected = selectedChatIds
        index.resetIndex(selected)
        _indexingStatus.value = if (selected.isEmpty()) null else "Indexing queued for ${selected.size} source(s)…"
        appContext?.let(TelegramIndexWorker::enqueue)
    }

    internal fun updateIndexingStatus(message: String?) {
        _indexingStatus.value = message
    }

    suspend fun logOut(clearCredentials: Boolean = true) {
        val active = client
        if (active != null && _auth.value !is TelegramAuthStatus.CredentialsRequired) {
            runCatching { send(TdApi.LogOut()) }
            runCatching { send(TdApi.Close()) }
            // TDLib closes asynchronously after its result is delivered.
            kotlinx.coroutines.delay(500)
        }
        client = null
        parametersSent = false
        chatsById.clear()
        _chats.value = emptyList()
        if (clearCredentials) {
            secretsMutex.withLock {
                secureStore.clear()
                secrets = null
            }
            if (sessionDirectory.exists()) sessionDirectory.deleteRecursively()
            index.clear()
            _auth.value = TelegramAuthStatus.CredentialsRequired
        } else {
            _auth.value = TelegramAuthStatus.PhoneNumberRequired
        }
    }

    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T = withContext(Dispatchers.IO) {
        val active = client ?: throw IOException("Telegram is not connected.")
        val answer = CompletableDeferred<TdApi.Object>()
        active.send(function) { result ->
            when (result) {
                is TdApi.Error -> answer.completeExceptionally(IOException("Telegram error ${result.code}: ${result.message}"))
                else -> answer.complete(result)
            }
        }
        @Suppress("UNCHECKED_CAST")
        (answer.await() as T)
    }

    suspend fun getFile(fileId: Int): TdApi.File = send(TdApi.GetFile(fileId)).also(::observeFile)

    suspend fun downloadWindow(fileId: Int, offset: Long, limit: Long): TdApi.File =
        send(TdApi.DownloadFile(fileId, 32, offset, limit, false)).also(::observeFile)

    suspend fun downloadCompleteFile(fileId: Int): TdApi.File =
        send(TdApi.DownloadFile(fileId, 32, 0, 0, false)).also(::observeFile)

    private fun observeFile(file: TdApi.File) {
        fileUpdates.computeIfAbsent(file.id) { MutableStateFlow(null) }.value = file
    }

    suspend fun awaitFile(fileId: Int, condition: (TdApi.File) -> Boolean): TdApi.File = withTimeout(60_000) {
        val flow = fileUpdates.computeIfAbsent(fileId) { MutableStateFlow(null) }
        flow.first { it != null && condition(it) }!!
    }

    suspend fun <T> withFileReadLock(fileId: Int, block: suspend () -> T): T =
        fileReadLocks.computeIfAbsent(fileId) { Mutex() }.withLock { block() }

    suspend fun indexPage(chatId: Long): TelegramIndexDatabase.PageResult {
        ensureReady()
        val cursor = index.cursor(chatId)
        if (cursor.built) {
            val latest = send(TdApi.GetChatHistory(chatId, 0, 0, PAGE_SIZE, false)).messages.orEmpty()
        index.storeMessages(chatId, latest)
            return TelegramIndexDatabase.PageResult(latest.size, true)
        }
        val from = cursor.nextMessageId
        val result = send(TdApi.GetChatHistory(chatId, from, 0, PAGE_SIZE, false))
        index.storeMessages(chatId, result.messages.orEmpty())
        val messages = result.messages.orEmpty()
        val oldest = messages.minOfOrNull { it.id } ?: 0L
        val newest = messages.maxOfOrNull { it.id } ?: 0L
        val next = when {
            messages.isEmpty() -> 0L
            oldest <= 0L || oldest == from -> 0L
            else -> oldest
        }
        val done = next == 0L
        index.updateCursor(chatId, next, done, maxOf(cursor.newestMessageId, newest))
        return TelegramIndexDatabase.PageResult(messages.size, done)
    }

    private suspend fun ensureReady() {
        if (!isReady) throw IOException("Connect to Telegram before browsing chats.")
    }

    private suspend fun startClient(credentials: TelegramSecrets) = startMutex.withLock {
        if (client != null) return@withLock
        try {
            System.loadLibrary("tdjni")
            runCatching { Client.execute(TdApi.SetLogVerbosityLevel(0)) }
            if (!sessionDirectory.exists() && !sessionDirectory.mkdirs()) {
                throw IOException("Could not create Telegram's private session directory.")
            }
            val filesDirectory = File(sessionDirectory, "files")
            if (!filesDirectory.exists() && !filesDirectory.mkdirs()) {
                throw IOException("Could not create Telegram's private files directory.")
            }
            parametersSent = false
            val generation = ++clientGeneration
            val created = CompletableDeferred<Unit>()
            clientCreationGate = created
            val createdClient = Client.create(
                { update -> onUpdate(update, credentials, generation, created) },
                { error -> TrackLog.w(TAG, "TDLib update handler failed (${error.javaClass.simpleName})") },
                { error -> TrackLog.w(TAG, "TDLib request callback failed (${error.javaClass.simpleName})") },
            )
            client = createdClient
            _auth.value = TelegramAuthStatus.Starting
            created.complete(Unit)
            initializationTimeout?.cancel()
            initializationTimeout = scope.launch {
                delay(INITIALIZATION_TIMEOUT_MS)
                if (generation == clientGeneration && _auth.value == TelegramAuthStatus.Starting) {
                    _auth.value = TelegramAuthStatus.Error(
                        "TDLib did not finish initializing. Check your connection and Telegram API credentials, then retry.",
                    )
                    runCatching { createdClient.send(TdApi.Close()) {} }
                    client = null
                    parametersSent = false
                }
            }
        } catch (error: Throwable) {
            client = null
            clientCreationGate?.complete(Unit)
            parametersSent = false
            _auth.value = TelegramAuthStatus.Error("TDLib could not start: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun onUpdate(
        update: TdApi.Object,
        credentials: TelegramSecrets,
        generation: Long,
        created: CompletableDeferred<Unit>,
    ) {
        if (update is TdApi.UpdateAuthorizationState) {
            authorizationUpdates.trySend(AuthorizationUpdate(generation, update.authorizationState, credentials, created))
        }
        if (update is TdApi.UpdateFile) observeFile(update.file)
        if (update is TdApi.UpdateNewChat) cacheChat(update.chat)
        if (update is TdApi.UpdateNewMessage) {
            if (update.message.chatId in selectedChatIds) scope.launch {
                index.storeMessage(update.message.chatId, update.message)
                appContext?.let(TelegramIndexWorker::enqueue)
            }
        }
        if (update is TdApi.UpdateDeleteMessages) {
            if (update.isPermanent) index.deleteMessages(update.chatId, update.messageIds)
        }
        scope.launch { _updates.emit(update as? TdApi.Update ?: return@launch) }
    }

    private suspend fun handleAuthorization(state: TdApi.AuthorizationState, credentials: TelegramSecrets) {
        try {
            when (state) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    if (parametersSent) return
                    parametersSent = true
                    val dbKey = Base64.decode(credentials.databaseKey, Base64.NO_WRAP)
                    send(
                        TdApi.SetTdlibParameters(
                            false,
                            sessionDirectory.absolutePath,
                            File(sessionDirectory, "files").absolutePath,
                            dbKey,
                            true,
                            true,
                            true,
                            false,
                            credentials.apiId,
                            credentials.apiHash,
                            Locale.getDefault().toLanguageTag().ifBlank { "en" },
                            Build.MODEL.ifBlank { "Android" },
                            Build.VERSION.RELEASE.orEmpty(),
                            BuildConfig.VERSION_NAME,
                        ),
                    )
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> setAuthStatus(TelegramAuthStatus.PhoneNumberRequired)
                is TdApi.AuthorizationStateWaitCode -> setAuthStatus(TelegramAuthStatus.CodeRequired)
                is TdApi.AuthorizationStateWaitEmailAddress -> setAuthStatus(TelegramAuthStatus.EmailAddressRequired)
                is TdApi.AuthorizationStateWaitEmailCode -> setAuthStatus(TelegramAuthStatus.EmailCodeRequired)
                is TdApi.AuthorizationStateWaitPassword -> setAuthStatus(TelegramAuthStatus.PasswordRequired)
                is TdApi.AuthorizationStateReady -> {
                    val user = runCatching { send(TdApi.GetMe()) }.getOrNull()
                    val name = listOfNotNull(user?.firstName, user?.lastName).joinToString(" ").ifBlank { "Telegram account" }
                    setAuthStatus(TelegramAuthStatus.Ready(name, user?.phoneNumber.orEmpty()))
                    runCatching { refreshChats() }
                }
                is TdApi.AuthorizationStateClosed -> {
                    if (_auth.value !is TelegramAuthStatus.CredentialsRequired) setAuthStatus(TelegramAuthStatus.PhoneNumberRequired)
                }
                is TdApi.AuthorizationStateWaitRegistration -> setAuthStatus(TelegramAuthStatus.Error("This Telegram account needs registration in the official Telegram app first."))
                else -> Unit // Preserve the initialization state for TDLib's non-interactive states.
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Telegram codes/passwords and API credentials are never logged.
            setAuthStatus(TelegramAuthStatus.Error(error.message ?: "Telegram authorization failed."))
        }
    }

    private fun setAuthStatus(status: TelegramAuthStatus) {
        _auth.value = status
        if (status != TelegramAuthStatus.Starting) initializationTimeout?.cancel()
    }

    private fun cacheChat(chat: TdApi.Chat, publish: Boolean = true) {
        if (chat.toTelegramChat(chatsById[chat.id]) == null) return
        chatsById.compute(chat.id) { _, previous -> chat.toTelegramChat(previous) }
        if (publish) publishChats()
    }

    private fun publishChats() {
        val selected = selectedChatIds
        _chats.value = chatsById.values
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .map { it.copy(selected = it.id in selected) }
    }
}

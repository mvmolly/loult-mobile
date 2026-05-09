package family.loult.app.domain.repo

import androidx.compose.runtime.mutableStateListOf
import family.loult.app.data.net.IncomingMessage
import family.loult.app.data.net.LoultEvent
import family.loult.app.data.net.LoultWebSocketClient
import family.loult.app.data.net.OutgoingMessage
import family.loult.app.data.net.WireProfile
import family.loult.app.data.net.WireUser
import family.loult.app.data.settings.LoultSettings
import family.loult.app.domain.model.ChatMessage
import family.loult.app.domain.model.ConnectionState
import family.loult.app.domain.model.LoultUser
import family.loult.app.domain.model.Profile
import family.loult.app.domain.model.RoomState
import family.loult.app.shared.audio.TtsPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How far back to look when stamping an audio_id onto its paired text
 *  message. The audio event always arrives shortly after the text it
 *  voices, so a small window is enough and keeps the scan O(1) regardless
 *  of how long the chat has been running. */
private const val AUDIO_STAMP_LOOKBACK = 30

/**
 * Owns the WebSocket lifecycle. Connection / user state lives in a tiny
 * RoomState StateFlow; the chat log lives in its own SnapshotStateList so
 * appends are O(1) amortized and Compose readers (LazyColumn) get
 * granular invalidation instead of paying an O(n) StateFlow equality check
 * on every incoming message.
 *
 * Reconnect strategy: capped exponential backoff (1, 2, 4, 8, max 30s).
 */
class ChatRepository(
    private val client: LoultWebSocketClient,
    private val settings: LoultSettings,
    private val scope: CoroutineScope,
    private val tts: TtsPlayer,
) {

    init {
        // Restore persisted mute on construction.
        tts.setMuted(settings.muted)
    }

    private val _state = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = _state.asStateFlow()

    /** Compose-aware list. Reads inside a composable register a snapshot
     *  read; mutations only invalidate the readers that observed the
     *  affected slot. Exposed as a plain List<T> so callers can't mutate it. */
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    private var connectJob: Job? = null

    /** User ids whose TTS should be silenced. Loaded from settings on first use
     *  and kept in sync via [setMutedUserIds]. */
    private var mutedUserIds: Set<String> = settings.mutedUserIds

    fun connect(channel: String = "") {
        connectJob?.cancel()
        connectJob = scope.launch {
            var backoff = 1_000L
            while (true) {
                _state.update { it.copy(connection = ConnectionState.Connecting) }
                runCatching { collectFor(channel) }
                    .onFailure { e ->
                        _state.update { it.copy(connection = ConnectionState.Error(e.message)) }
                    }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        _state.update { it.copy(connection = ConnectionState.Disconnected) }
    }

    /**
     * Wipe local state (users + messages) and reconnect with whatever cookie
     * is currently in LoultSettings. Used after the user manually changes
     * the cookie so a new Pokémon identity takes effect immediately.
     */
    fun reconnect(channel: String = "") {
        _state.value = RoomState(connection = ConnectionState.Connecting)
        _messages.clear()
        connect(channel)
    }

    /** Apply a mute toggle: persists to settings + propagates to the player. */
    fun setMuted(muted: Boolean) {
        settings.muted = muted
        tts.setMuted(muted)
    }

    fun setMutedUserIds(ids: Set<String>) {
        mutedUserIds = ids
    }

    suspend fun send(message: OutgoingMessage) = client.sendCommand(message)

    private suspend fun collectFor(channel: String) {
        client.connect(channel = channel).collect { event ->
            when (event) {
                // Reset the flood flag on every successful reconnect so the
                // composer re-enables; the server will re-impose antiflood
                // if the user genuinely flooded.
                LoultEvent.Connected -> _state.update {
                    it.copy(connection = ConnectionState.Connected, flooded = false)
                }
                is LoultEvent.Incoming -> handle(event.message)
                is LoultEvent.Binary -> Unit
                is LoultEvent.UnknownText -> Unit
                is LoultEvent.Closed -> _state.update { it.copy(connection = ConnectionState.Disconnected) }
                is LoultEvent.Error -> _state.update { it.copy(connection = ConnectionState.Error(event.cause.message)) }
            }
        }
    }

    private fun handle(msg: IncomingMessage) {
        when (msg) {
            is IncomingMessage.UserList -> {
                val users = msg.users.map { it.toDomain() }
                _state.update {
                    it.copy(users = users, self = users.firstOrNull { u -> u.isYou } ?: it.self)
                }
            }
            is IncomingMessage.Connect -> {
                val u = LoultUser(
                    userId = msg.userid,
                    name = msg.params.name,
                    adjective = msg.params.adjective,
                    color = msg.params.color,
                    img = msg.params.img,
                    profile = msg.profile?.toDomain(),
                    isYou = msg.params.you,
                )
                _state.update { state ->
                    state.copy(users = (state.users + u).distinctBy { it.userId })
                }
                _messages.add(
                    ChatMessage.System(
                        date = msg.date,
                        text = "Un ${u.name} ${u.adjective} apparaît !",
                        kind = ChatMessage.System.Kind.Connect,
                    )
                )
            }
            is IncomingMessage.Disconnect -> {
                val gone = _state.value.users.firstOrNull { it.userId == msg.userid }
                val text = if (gone != null) "Le ${gone.name} ${gone.adjective} s'enfuit !"
                else "Quelqu'un s'enfuit !"
                _state.update { state ->
                    state.copy(users = state.users.filterNot { it.userId == msg.userid })
                }
                _messages.add(ChatMessage.System(msg.date, text, ChatMessage.System.Kind.Disconnect))
            }
            is IncomingMessage.Msg -> {
                val from = _state.value.users.firstOrNull { it.userId == msg.userid } ?: return
                _messages.add(ChatMessage.Text(msg.date, from, decodeEntities(msg.msg)))
            }
            is IncomingMessage.Bot -> {
                val from = _state.value.users.firstOrNull { it.userId == msg.userid } ?: return
                _messages.add(ChatMessage.Bot(msg.date, from, decodeEntities(msg.msg)))
            }
            is IncomingMessage.Me -> {
                val from = _state.value.users.firstOrNull { it.userId == msg.userid } ?: return
                _messages.add(ChatMessage.Me(msg.date, from, decodeEntities(msg.msg)))
            }
            is IncomingMessage.PrivateMsg -> handlePrivateMsg(msg)
            is IncomingMessage.Audio -> {
                val sender = msg.effectiveSenderId
                if (sender != null) stampAudioId(sender, msg.audioId)
                // Speak this message's TTS unless globally muted, or the sender
                // is on the per-user mute set (server bots count too).
                if (!settings.muted && msg.effectiveSenderId !in mutedUserIds) {
                    tts.play(msg.audioId)
                }
            }
            is IncomingMessage.Attack -> {
                val text = formatAttack(msg, _state.value)
                if (text != null) {
                    _messages.add(ChatMessage.System(msg.date, text, ChatMessage.System.Kind.AttackEvent))
                }
            }
            is IncomingMessage.Antiflood -> {
                val (text, kind) = when (msg.event) {
                    "flood_warning" -> "Attention, la qualité de vos contributions semble en baisse." to ChatMessage.System.Kind.AntifloodWarning
                    "banned" -> "Banni temporairement pour flood." to ChatMessage.System.Kind.AntifloodBanned
                    else -> return
                }
                _state.update { it.copy(flooded = msg.event == "banned") }
                _messages.add(ChatMessage.System(msg.date, text, kind))
            }
            is IncomingMessage.Wait -> {
                _state.update { it.copy(waitUntil = msg.date) }
                _messages.add(
                    ChatMessage.System(
                        date = msg.date,
                        text = "La connection est en cours. Concentrez-vous quelques instants.",
                        kind = ChatMessage.System.Kind.Wait,
                    )
                )
            }
            is IncomingMessage.Notification -> {
                msg.msg?.let { body ->
                    _messages.add(ChatMessage.System(msg.date, decodeEntities(body), ChatMessage.System.Kind.Notification))
                }
            }
            is IncomingMessage.CookieChange -> {
                settings.cookie = msg.cookie
            }
            is IncomingMessage.Backlog -> handleBacklog(msg)
            is IncomingMessage.Inventory -> Unit
        }
    }

    /** Find the most recent un-stamped Text message from this sender within
     *  the lookback window and attach the audio id. Bounded so cost stays
     *  constant as the chat log grows. */
    private fun stampAudioId(senderId: String, audioId: String) {
        val list = _messages
        val end = list.size - 1
        val start = (end - AUDIO_STAMP_LOOKBACK + 1).coerceAtLeast(0)
        for (i in end downTo start) {
            val m = list[i]
            if (m is ChatMessage.Text && m.from.userId == senderId && m.audioId == null) {
                list[i] = m.copy(audioId = audioId)
                return
            }
        }
    }

    private fun handlePrivateMsg(msg: IncomingMessage.PrivateMsg) {
        val (text, kind) = when (msg.event) {
            "invalid_target" -> "Utilisateur récepteur inexistant" to ChatMessage.System.Kind.Error
            "success" -> {
                val body = msg.msg.orEmpty()
                "MP envoyé à ${msg.targetName ?: "?"} : ${decodeEntities(body)}" to ChatMessage.System.Kind.Info
            }
            null -> {
                val from = msg.userid?.let { id -> _state.value.users.firstOrNull { it.userId == id } }
                val sender = if (from != null) "${from.name} ${from.adjective}".trim() else "?"
                "MP de $sender : ${decodeEntities(msg.msg.orEmpty())}" to ChatMessage.System.Kind.Info
            }
            else -> return
        }
        _messages.add(ChatMessage.System(msg.date, text, kind))
    }

    private fun handleBacklog(backlog: IncomingMessage.Backlog) {
        if (backlog.msgs.isEmpty()) return
        val users = _state.value.users
        val newMessages = backlog.msgs.mapNotNull { entry ->
            val from = users.firstOrNull { it.userId == entry.userid }
                ?: entry.user?.let {
                    LoultUser(
                        userId = entry.userid,
                        name = it.name,
                        adjective = it.adjective,
                        color = it.color,
                        img = it.img,
                        profile = null,
                        isYou = it.you,
                    )
                }
                ?: return@mapNotNull null
            val body = decodeEntities(entry.msg)
            when (entry.msgType) {
                "me" -> ChatMessage.Me(entry.date, from, body)
                "bot" -> ChatMessage.Bot(entry.date, from, body)
                else -> ChatMessage.Text(entry.date, from, body)
            }
        }
        _messages.addAll(0, newMessages)
    }

    private fun formatAttack(msg: IncomingMessage.Attack, state: RoomState): String? {
        val name: (String?) -> String = { id -> state.users.firstOrNull { it.userId == id }?.name ?: "?" }
        return when (msg.event) {
            "attack" -> "${name(msg.attackerId)} attaque ${name(msg.defenderId)} !"
            "dice" -> "${name(msg.attackerId)} tire un ${msg.attackerDice} (+${msg.attackerBonus}), ${name(msg.defenderId)} tire un ${msg.defenderDice} (+${msg.defenderBonus}) !"
            "effect" -> "${name(msg.targetId)} est maintenant affecté par l'effet ${msg.effect} !"
            "nothing" -> "Il ne se passe rien..."
            else -> null
        }
    }
}

private fun WireUser.toDomain() = LoultUser(
    userId = userid,
    name = params.name,
    adjective = params.adjective,
    color = params.color,
    img = params.img,
    profile = profile?.toDomain(),
    isYou = params.you,
)

private fun WireProfile.toDomain() = Profile(
    age = age,
    sex = sex,
    orientation = orientation,
    job = job,
    city = city,
    departement = departement,
)

/** Server escapes user content as HTML entities (`&#x27;`, `&amp;`, `&lt;`, …).
 *  Decode the common ones; leave anything fancy alone. */
private fun decodeEntities(s: String): String =
    s.replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

package family.loult.app.domain.repo

import family.loult.app.data.net.IncomingMessage
import family.loult.app.data.net.LoultEvent
import family.loult.app.data.net.LoultWebSocketClient
import family.loult.app.data.net.OutgoingMessage
import family.loult.app.data.net.WireProfile
import family.loult.app.data.net.WireUser
import family.loult.app.data.net.WireUserParams
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

/**
 * Owns the WebSocket lifecycle and folds incoming events into a RoomState
 * StateFlow. Read-only for M1; the surface already supports send().
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

    private var connectJob: Job? = null

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
        connect(channel)
    }

    /** Apply a mute toggle: persists to settings + propagates to the player. */
    fun setMuted(muted: Boolean) {
        settings.muted = muted
        tts.setMuted(muted)
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
                    state.copy(
                        users = (state.users + u).distinctBy { it.userId },
                        messages = state.messages + ChatMessage.System(
                            date = msg.date,
                            text = "Un ${u.name} ${u.adjective} apparaît !",
                            kind = ChatMessage.System.Kind.Connect,
                        ),
                    )
                }
            }
            is IncomingMessage.Disconnect -> {
                _state.update { state ->
                    val gone = state.users.firstOrNull { it.userId == msg.userid }
                    val text = if (gone != null) "Le ${gone.name} ${gone.adjective} s'enfuit !"
                    else "Quelqu'un s'enfuit !"
                    state.copy(
                        users = state.users.filterNot { it.userId == msg.userid },
                        messages = state.messages + ChatMessage.System(msg.date, text, ChatMessage.System.Kind.Disconnect),
                    )
                }
            }
            is IncomingMessage.Msg -> {
                _state.update { state ->
                    val from = state.users.firstOrNull { it.userId == msg.userid } ?: return@update state
                    state.copy(messages = state.messages + ChatMessage.Text(msg.date, from, decodeEntities(msg.msg)))
                }
            }
            is IncomingMessage.Bot -> {
                _state.update { state ->
                    val from = state.users.firstOrNull { it.userId == msg.userid } ?: return@update state
                    state.copy(messages = state.messages + ChatMessage.Bot(msg.date, from, decodeEntities(msg.msg)))
                }
            }
            is IncomingMessage.Me -> {
                _state.update { state ->
                    val from = state.users.firstOrNull { it.userId == msg.userid } ?: return@update state
                    state.copy(messages = state.messages + ChatMessage.Me(msg.date, from, decodeEntities(msg.msg)))
                }
            }
            is IncomingMessage.PrivateMsg -> handlePrivateMsg(msg)
            is IncomingMessage.Audio -> {
                val sender = msg.effectiveSenderId
                if (sender != null) {
                    _state.update { state ->
                        val updated = state.messages.toMutableList()
                        val idx = updated.indexOfLast {
                            it is ChatMessage.Text && it.from.userId == sender && it.audioId == null
                        }
                        if (idx >= 0) {
                            val original = updated[idx] as ChatMessage.Text
                            updated[idx] = original.copy(audioId = msg.audioId)
                        }
                        state.copy(messages = updated)
                    }
                }
                // Speak this message's TTS unless muted (server bots count too).
                if (!settings.muted) tts.enqueue(msg.audioId)
            }
            is IncomingMessage.Attack -> {
                val text = formatAttack(msg, _state.value)
                if (text != null) {
                    _state.update { state ->
                        state.copy(messages = state.messages + ChatMessage.System(msg.date, text, ChatMessage.System.Kind.AttackEvent))
                    }
                }
            }
            is IncomingMessage.Antiflood -> {
                val (text, kind) = when (msg.event) {
                    "flood_warning" -> "Attention, la qualité de vos contributions semble en baisse." to ChatMessage.System.Kind.AntifloodWarning
                    "banned" -> "Banni temporairement pour flood." to ChatMessage.System.Kind.AntifloodBanned
                    else -> return
                }
                _state.update {
                    it.copy(
                        flooded = msg.event == "banned",
                        messages = it.messages + ChatMessage.System(msg.date, text, kind),
                    )
                }
            }
            is IncomingMessage.Wait -> {
                _state.update {
                    it.copy(
                        waitUntil = msg.date,
                        messages = it.messages + ChatMessage.System(
                            date = msg.date,
                            text = "La connection est en cours. Concentrez-vous quelques instants.",
                            kind = ChatMessage.System.Kind.Wait,
                        ),
                    )
                }
            }
            is IncomingMessage.Notification -> {
                msg.msg?.let { body ->
                    _state.update {
                        it.copy(messages = it.messages + ChatMessage.System(msg.date, decodeEntities(body), ChatMessage.System.Kind.Notification))
                    }
                }
            }
            is IncomingMessage.CookieChange -> {
                settings.cookie = msg.cookie
            }
            is IncomingMessage.Backlog -> handleBacklog(msg)
            is IncomingMessage.Inventory -> Unit
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
        _state.update { state ->
            state.copy(messages = state.messages + ChatMessage.System(msg.date, text, kind))
        }
    }

    private fun handleBacklog(backlog: IncomingMessage.Backlog) {
        if (backlog.msgs.isEmpty()) return
        _state.update { state ->
            val newMessages = backlog.msgs.mapNotNull { entry ->
                val from = state.users.firstOrNull { it.userId == entry.userid }
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
            state.copy(messages = newMessages + state.messages)
        }
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

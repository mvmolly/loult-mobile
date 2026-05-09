package family.loult.app.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Server -> client messages. Loult discriminates on the "type" field.
 *
 * Schema captured from a live wss://loult.family/socket/ session (May 2026).
 * Notable surprises vs. the older loult-ng docs:
 *   - `date` is a fractional millisecond timestamp (Double), not Long.
 *   - On userlist entries, `you` lives inside `params`; `profile` is top-level.
 *   - The avatar field is `img: String` (Pokédex id zero-padded to 3 chars).
 *
 * Unknown fields are ignored at decode time.
 */
@Serializable
sealed interface IncomingMessage {

    @Serializable
    @SerialName("userlist")
    data class UserList(
        val users: List<WireUser> = emptyList(),
    ) : IncomingMessage

    @Serializable
    @SerialName("backlog")
    data class Backlog(
        val msgs: List<WireBacklogEntry> = emptyList(),
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("msg")
    data class Msg(
        val userid: String,
        val msg: String,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("bot")
    data class Bot(
        val userid: String,
        val msg: String,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("me")
    data class Me(
        val userid: String,
        val msg: String,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("private_msg")
    data class PrivateMsg(
        // Inbound DM from another user (event == null): userid + msg are set.
        val userid: String? = null,
        val msg: String? = null,
        // Server confirmation of outgoing send: event = "success" / "invalid_target".
        // "success" payload also carries target_name + msg.
        val event: String? = null,
        @SerialName("target_name") val targetName: String? = null,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("audio")
    data class Audio(
        // The wire field is `sender_id`. May also be the literal "server" for
        // bot/system audio. Provide a fallback to userid for older protocol
        // variants we might encounter.
        @SerialName("sender_id") val senderId: String? = null,
        val userid: String? = null,
        @SerialName("audio_id") val audioId: String,
        val date: Double = 0.0,
    ) : IncomingMessage {
        val effectiveSenderId: String? get() = senderId ?: userid
    }

    @Serializable
    @SerialName("connect")
    data class Connect(
        val userid: String,
        val params: WireUserParams,
        val profile: WireProfile? = null,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("disconnect")
    data class Disconnect(
        val userid: String,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("attack")
    data class Attack(
        val event: String,
        @SerialName("attacker_id") val attackerId: String? = null,
        @SerialName("defender_id") val defenderId: String? = null,
        @SerialName("target_id") val targetId: String? = null,
        @SerialName("attacker_dice") val attackerDice: Int? = null,
        @SerialName("attacker_bonus") val attackerBonus: Int? = null,
        @SerialName("defender_dice") val defenderDice: Int? = null,
        @SerialName("defender_bonus") val defenderBonus: Int? = null,
        val effect: String? = null,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("antiflood")
    data class Antiflood(
        val event: String,
        @SerialName("flooder_id") val flooderId: String? = null,
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("wait")
    data class Wait(
        val date: Double = 0.0,
    ) : IncomingMessage

    @Serializable
    @SerialName("notification")
    data class Notification(
        val msg: String? = null,
        val date: Double = 0.0,
        val params: JsonElement? = null,
    ) : IncomingMessage

    @Serializable
    @SerialName("inventory")
    data class Inventory(
        val items: JsonElement? = null,
    ) : IncomingMessage

    @Serializable
    @SerialName("cookie-change")
    data class CookieChange(
        val cookie: String,
    ) : IncomingMessage
}

@Serializable
data class WireUser(
    val userid: String,
    val params: WireUserParams,
    val profile: WireProfile? = null,
)

@Serializable
data class WireUserParams(
    val name: String,
    val adjective: String = "",
    val color: String? = null,
    val img: String? = null,
    @SerialName("you") val you: Boolean = false,
)

/** A single entry inside a `backlog` event. `user` is inlined per-message
 *  (with no `you` / `profile` block) — different from the live userlist shape. */
@Serializable
data class WireBacklogEntry(
    val userid: String,
    val msg: String,
    val date: Double = 0.0,
    @SerialName("msg_type") val msgType: String = "msg",
    val user: WireUserParams? = null,
)

@Serializable
data class WireProfile(
    val age: Int? = null,
    val sex: String? = null,
    val orientation: String? = null,
    val job: String? = null,
    val city: String? = null,
    val departement: String? = null,
)

/** Client -> server commands. */
@Serializable
sealed interface OutgoingMessage {

    @Serializable
    @SerialName("msg")
    data class Msg(
        val msg: String,
        val lang: String? = null,
    ) : OutgoingMessage

    @Serializable
    @SerialName("private_msg")
    data class PrivateMsg(
        // Server expects `target` (the recipient's pokemon name), not `userid`.
        val target: String,
        val msg: String,
        val order: Int? = null,
    ) : OutgoingMessage

    @Serializable
    @SerialName("attack")
    data class Attack(
        val target: String,
        val order: Int? = null,
    ) : OutgoingMessage

    @Serializable
    @SerialName("me")
    data class Me(val msg: String) : OutgoingMessage

    @Serializable
    @SerialName("bot")
    data class Bot(val msg: String) : OutgoingMessage
}

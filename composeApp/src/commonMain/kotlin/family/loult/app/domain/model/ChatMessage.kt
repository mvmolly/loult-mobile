package family.loult.app.domain.model

sealed interface ChatMessage {
    val date: Double

    data class Text(
        override val date: Double,
        val from: LoultUser,
        val body: String,
        val audioId: String? = null,
    ) : ChatMessage

    data class Me(
        override val date: Double,
        val from: LoultUser,
        val body: String,
    ) : ChatMessage

    data class Bot(
        override val date: Double,
        val from: LoultUser,
        val body: String,
    ) : ChatMessage

    data class System(
        override val date: Double,
        val text: String,
        val kind: Kind,
    ) : ChatMessage {
        enum class Kind { Connect, Disconnect, AttackEvent, AntifloodWarning, AntifloodBanned, Wait, Notification, Info, Error }
    }
}

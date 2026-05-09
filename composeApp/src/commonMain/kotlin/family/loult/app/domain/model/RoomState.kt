package family.loult.app.domain.model

data class RoomState(
    val users: List<LoultUser> = emptyList(),
    val self: LoultUser? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val waitUntil: Double? = null,
    val flooded: Boolean = false,
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String?) : ConnectionState
}

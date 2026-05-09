package family.loult.app.data.net

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlin.coroutines.coroutineContext

/**
 * Connects to wss://<host>/socket/<channel> and exposes a Flow<LoultEvent>.
 *
 * The cookie is sent as `Cookie: id=<value>` if non-null; otherwise the server
 * generates one server-side and (per loult-ng) ships it back via Set-Cookie on
 * the upgrade response. We additionally listen for `cookie-change` JSON events
 * which the JS client uses for the same purpose.
 *
 * One client per session. The flow terminates when the WS closes or the
 * collector cancels.
 */
class LoultWebSocketClient(
    private val httpClient: HttpClient,
) {
    private val outgoing = Channel<OutgoingMessage>(Channel.BUFFERED)
    private var sender: Job? = null

    fun connect(
        host: String = "loult.family",
        channel: String = "",
    ): Flow<LoultEvent> = channelFlow {
        // Cookie is injected by Ktor's HttpCookies plugin from
        // LoultCookiesStorage (backed by LoultSettings). On the first ever
        // connect there's no cookie yet; the server replies with
        // Set-Cookie: id=<hex> on the upgrade response and the plugin saves
        // it for subsequent connects.
        val ws = httpClient.webSocketSession {
            url("wss://$host/socket/$channel")
        }
        send(LoultEvent.Connected)

        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        sender = scope.launch {
            for (msg in outgoing) {
                val text = LoultJson.encodeToString(OutgoingMessage.serializer(), msg)
                ws.send(text)
            }
        }

        try {
            for (frame in ws.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        val decoded = decodeOrNull(text)
                        if (decoded != null) send(LoultEvent.Incoming(decoded))
                        else send(LoultEvent.UnknownText(text))
                    }
                    is Frame.Binary -> send(LoultEvent.Binary(frame.data))
                    is Frame.Close -> send(LoultEvent.Closed(frame.readReason()?.message))
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            send(LoultEvent.Error(e))
        } finally {
            sender?.cancel()
            sender = null
            scope.cancel()
            runCatching { ws.close() }
        }
    }

    suspend fun sendCommand(message: OutgoingMessage) {
        outgoing.send(message)
    }

    private fun decodeOrNull(text: String): IncomingMessage? = try {
        LoultJson.decodeFromString(IncomingMessage.serializer(), text)
    } catch (_: Throwable) {
        null
    }
}

sealed interface LoultEvent {
    data object Connected : LoultEvent
    data class Incoming(val message: IncomingMessage) : LoultEvent
    data class Binary(val bytes: ByteArray) : LoultEvent
    data class UnknownText(val raw: String) : LoultEvent
    data class Closed(val reason: String?) : LoultEvent
    data class Error(val cause: Throwable) : LoultEvent
}

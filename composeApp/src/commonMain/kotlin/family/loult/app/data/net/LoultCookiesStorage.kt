package family.loult.app.data.net

import family.loult.app.data.settings.LoultSettings
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url

/**
 * Backs Ktor's HttpCookies plugin with our persistent LoultSettings.
 * The server sends `Set-Cookie: id=<hex>` on the WebSocket upgrade response;
 * Ktor parses it and calls addCookie() — we save it. On the next connect,
 * Ktor calls get() and we replay the persisted id, preserving Pokémon
 * identity across reconnects (and avoiding MAX_COOKIES_PER_IP bans).
 */
class LoultCookiesStorage(
    private val settings: LoultSettings,
) : CookiesStorage {

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val value = settings.cookie ?: return emptyList()
        return listOf(
            Cookie(
                name = "id",
                value = value,
                domain = "loult.family",
                path = "/",
            )
        )
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (cookie.name == "id" && cookie.value.isNotBlank()) {
            settings.cookie = cookie.value
        }
    }

    override fun close() = Unit
}

package family.loult.app.di

import com.russhwolf.settings.Settings
import family.loult.app.data.net.LoultCookiesStorage
import family.loult.app.data.net.LoultJson
import family.loult.app.data.net.LoultWebSocketClient
import family.loult.app.data.settings.LoultSettings
import family.loult.app.data.upload.BnlUploader
import family.loult.app.domain.repo.ChatRepository
import family.loult.app.ui.chat.ChatViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val networkModule = module {
    single { LoultCookiesStorage(get()) }
    single {
        val cookies = get<LoultCookiesStorage>()
        HttpClient {
            install(WebSockets)
            install(ContentNegotiation) { json(LoultJson) }
            install(HttpCookies) { storage = cookies }
            install(Logging) { level = LogLevel.INFO }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
        }
    }
    singleOf(::LoultWebSocketClient)
    singleOf(::BnlUploader)
}

val audioModule = module {
    // Placeholder — TtsPlayer / RadioPlayer come in M2/M3
}

val repositoryModule = module {
    single { LoultSettings(get<Settings>()) }
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { ChatRepository(client = get(), settings = get(), scope = get(), tts = get()) }
}

val viewModelModule = module {
    viewModel { ChatViewModel(repository = get(), settings = get(), uploader = get()) }
}

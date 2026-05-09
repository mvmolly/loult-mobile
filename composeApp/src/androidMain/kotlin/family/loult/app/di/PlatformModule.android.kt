package family.loult.app.di

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import family.loult.app.shared.audio.AndroidTtsPlayer
import family.loult.app.shared.audio.TtsPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<Settings> {
        val prefs = androidContext().getSharedPreferences("loult", android.content.Context.MODE_PRIVATE)
        SharedPreferencesSettings(prefs)
    }
    single<TtsPlayer> {
        // Constructed lazily on the main thread; ExoPlayer's lazy init
        // inside AndroidTtsPlayer enforces that.
        AndroidTtsPlayer(context = androidContext(), httpClient = get())
    }
}

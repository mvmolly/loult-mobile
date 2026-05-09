package family.loult.app.data.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * App-wide preferences. Backed by multiplatform-settings (DataStore Preferences
 * on Android via the no-arg variant).
 */
class LoultSettings(private val settings: Settings) {

    var cookie: String?
        get() = settings.getStringOrNull(KEY_COOKIE)
        set(value) {
            if (value == null) settings.remove(KEY_COOKIE)
            else settings[KEY_COOKIE] = value
        }

    /** Theme as the loult 3-part string ("theme color font"), default "bibw night sans". */
    var theme: String
        get() = settings.getString(KEY_THEME, DEFAULT_THEME)
        set(value) { settings[KEY_THEME] = value }

    var muted: Boolean
        get() = settings.getBoolean(KEY_MUTED, false)
        set(value) { settings[KEY_MUTED] = value }

    /** Render BNL image links (`https://bnl.loult.family/media/content/image/...`)
     *  inline below the message body. Default on. */
    var previewBnlImages: Boolean
        get() = settings.getBoolean(KEY_PREVIEW_BNL, true)
        set(value) { settings[KEY_PREVIEW_BNL] = value }

    private companion object {
        const val KEY_COOKIE = "loult.cookie"
        const val KEY_THEME = "loult.theme"
        const val KEY_MUTED = "loult.muted"
        const val KEY_PREVIEW_BNL = "loult.preview.bnl"
        const val DEFAULT_THEME = "bibw night sans"
    }
}

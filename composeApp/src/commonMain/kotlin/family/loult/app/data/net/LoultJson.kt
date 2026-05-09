package family.loult.app.data.net

import kotlinx.serialization.json.Json

/** Shared Json instance — tolerant to schema drift on a live, evolving server. */
val LoultJson: Json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
    coerceInputValues = true
}

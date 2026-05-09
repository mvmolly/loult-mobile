package family.loult.app.shared.audio

/**
 * Plays the server-rendered TTS clip for each chat message. Each `audio`
 * event from the WebSocket gives us an `audio_id`; the corresponding clip
 * lives at `https://loult.family/audio/<id>/`.
 *
 * Implementations play clips concurrently — voices that arrive while
 * another is still speaking mix on top, mirroring the loult.family web
 * client.
 */
interface TtsPlayer {
    /** Fetch and play this audio clip. No-op if currently muted. */
    fun play(audioId: String)

    /** Mute toggle. Stops in-flight playback when set true. */
    fun setMuted(muted: Boolean)

    /** Free the underlying resources. Called when the app process is shutting down. */
    fun release()
}

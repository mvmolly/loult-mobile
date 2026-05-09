package family.loult.app.shared.audio

/**
 * Plays the server-rendered TTS clip for each chat message. Each `audio`
 * event from the WebSocket gives us an `audio_id`; the corresponding clip
 * lives at `https://loult.family/audio/<id>/`.
 *
 * Implementations are expected to fetch and play sequentially, never
 * overlapping, mirroring the loult web client behaviour.
 */
interface TtsPlayer {
    /** Add an audio_id to the playback queue. No-op if currently muted. */
    fun enqueue(audioId: String)

    /** Mute toggle. Stops playback and clears the queue when set true. */
    fun setMuted(muted: Boolean)

    /** Free the underlying player. Called when the app process is shutting down. */
    fun release()
}

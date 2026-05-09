package family.loult.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import family.loult.app.data.net.OutgoingMessage
import family.loult.app.data.settings.LoultSettings
import family.loult.app.data.upload.BnlUploader
import family.loult.app.domain.model.RoomState
import family.loult.app.domain.repo.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val settings: LoultSettings,
    private val uploader: BnlUploader,
) : ViewModel() {

    val state: StateFlow<RoomState> = repository.state

    private val _cookie = MutableStateFlow(settings.cookie)
    val cookie: StateFlow<String?> = _cookie.asStateFlow()

    private val _muted = MutableStateFlow(settings.muted)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _mutedUserIds = MutableStateFlow(settings.mutedUserIds)
    val mutedUserIds: StateFlow<Set<String>> = _mutedUserIds.asStateFlow()

    private val _previewImages = MutableStateFlow(settings.previewBnlImages)
    val previewImages: StateFlow<Boolean> = _previewImages.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    init {
        repository.setMutedUserIds(settings.mutedUserIds)
        repository.connect(channel = "")
    }

    fun setComposerText(text: String) {
        _composerText.value = text
    }

    /** Prefill the composer with `/mp <name> : ` ready for the body to be typed. */
    fun startPrivateMessage(name: String) {
        _composerText.value = "/mp ${name.lowercase()} : "
    }

    /** Send an attack against the given pokemon. `order` distinguishes
     *  multiple users sharing the same name; null lets the server pick. */
    fun attack(name: String, order: Int? = null) {
        val target = name.lowercase().ifBlank { return }
        viewModelScope.launch {
            repository.send(OutgoingMessage.Attack(target = target, order = order))
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        val trimmed = text.trim()
        val outgoing = parseSlashCommand(trimmed) ?: OutgoingMessage.Msg(msg = trimmed)
        viewModelScope.launch { repository.send(outgoing) }
        _composerText.value = ""
    }

    private fun parseSlashCommand(trimmed: String): OutgoingMessage? {
        if (!trimmed.startsWith("/mp", ignoreCase = true)) return null
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val body = trimmed.substring(colon + 1).trim()
        if (body.isEmpty()) return null
        val meta = trimmed.substring(0, colon).split(' ').filter { it.isNotBlank() }
        if (meta.size < 2) return null
        val target = meta[1]
        val order = meta.getOrNull(2)?.toIntOrNull()
        return OutgoingMessage.PrivateMsg(target = target, msg = body, order = order)
    }

    /** Persist a new cookie and reconnect so the server reissues identity. */
    fun setCookie(newCookie: String) {
        val cleaned = newCookie.trim()
        if (cleaned.isEmpty() || cleaned == settings.cookie) return
        settings.cookie = cleaned
        _cookie.value = cleaned
        repository.reconnect()
    }

    fun setMuted(muted: Boolean) {
        if (_muted.value == muted) return
        _muted.value = muted
        repository.setMuted(muted)
    }

    fun toggleMute() = setMuted(!_muted.value)

    fun toggleUserMute(userId: String) {
        if (userId.isBlank()) return
        val current = _mutedUserIds.value
        val next = if (userId in current) current - userId else current + userId
        settings.mutedUserIds = next
        _mutedUserIds.value = next
        repository.setMutedUserIds(next)
    }

    /** Upload a file to BNL and post the resulting URL as a chat message. */
    fun uploadAndSendImage(bytes: ByteArray, filename: String) {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            try {
                val url = uploader.upload(bytes, filename, settings.cookie)
                if (url != null) {
                    repository.send(OutgoingMessage.Msg(msg = url))
                }
            } finally {
                _uploading.value = false
            }
        }
    }

    fun setPreviewImages(enabled: Boolean) {
        if (_previewImages.value == enabled) return
        settings.previewBnlImages = enabled
        _previewImages.value = enabled
    }

    override fun onCleared() {
        repository.disconnect()
    }
}

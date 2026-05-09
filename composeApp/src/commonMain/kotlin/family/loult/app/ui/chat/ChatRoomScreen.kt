package family.loult.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import family.loult.app.domain.model.ConnectionState
import family.loult.app.domain.model.RoomState
import family.loult.app.ui.settings.SettingsSheet
import family.loult.app.ui.users.UserListSheet
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatRoomScreen() {
    val viewModel: ChatViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cookie by viewModel.cookie.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val mutedUserIds by viewModel.mutedUserIds.collectAsStateWithLifecycle()
    val previewImages by viewModel.previewImages.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    val pickImage = rememberImagePicker { bytes, filename ->
        viewModel.uploadAndSendImage(bytes, filename)
    }

    var showSettings by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsSheet(
            cookie = cookie,
            muted = muted,
            previewImages = previewImages,
            onMutedChange = viewModel::setMuted,
            onPreviewImagesChange = viewModel::setPreviewImages,
            onSaveCookie = {
                viewModel.setCookie(it)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
    if (showUsers) {
        UserListSheet(
            users = state.users,
            mutedUserIds = mutedUserIds,
            onPickUser = { user ->
                viewModel.startPrivateMessage(user.name)
                showUsers = false
            },
            onToggleMute = { user -> viewModel.toggleUserMute(user.userId) },
            onDismiss = { showUsers = false },
        )
    }

    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    ChatRoomContent(
        state = state,
        previewImages = previewImages,
        mutedUserIds = mutedUserIds,
        composerText = composerText,
        uploading = uploading,
        onComposerChange = viewModel::setComposerText,
        onSend = viewModel::sendText,
        onPickImage = pickImage,
        onOpenSettings = { showSettings = true },
        onOpenUsers = { showUsers = true },
        onUserClick = { user -> viewModel.startPrivateMessage(user.name) },
        onToggleUserMute = { user -> viewModel.toggleUserMute(user.userId) },
        onCopyText = { body ->
            clipboard.setText(AnnotatedString(body))
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onAttack = { user ->
            if (!user.isYou) {
                viewModel.attack(user.name)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomContent(
    state: RoomState,
    previewImages: Boolean,
    mutedUserIds: Set<String>,
    composerText: String,
    uploading: Boolean,
    onComposerChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onPickImage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsers: () -> Unit,
    onUserClick: (family.loult.app.domain.model.LoultUser) -> Unit,
    onToggleUserMute: (family.loult.app.domain.model.LoultUser) -> Unit,
    onCopyText: (String) -> Unit,
    onAttack: (family.loult.app.domain.model.LoultUser) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ChatTopBar(
            state = state,
            onOpenSettings = onOpenSettings,
            onOpenUsers = onOpenUsers,
        )
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)

        val visibleMessages = remember(state.messages, mutedUserIds) {
            if (mutedUserIds.isEmpty()) state.messages
            else state.messages.filter { m ->
                when (m) {
                    is family.loult.app.domain.model.ChatMessage.Text -> m.from.userId !in mutedUserIds
                    is family.loult.app.domain.model.ChatMessage.Bot -> m.from.userId !in mutedUserIds
                    is family.loult.app.domain.model.ChatMessage.Me -> m.from.userId !in mutedUserIds
                    is family.loult.app.domain.model.ChatMessage.System -> true
                }
            }
        }

        LaunchedEffect(visibleMessages.size) {
            if (visibleMessages.isNotEmpty() &&
                listState.firstVisibleItemIndex <= 3 &&
                !listState.isScrollInProgress
            ) {
                listState.animateScrollToItem(0)
            }
        }
        LaunchedEffect(imeBottom) {
            if (visibleMessages.isNotEmpty() && listState.firstVisibleItemIndex <= 3) {
                listState.scrollToItem(0)
            }
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = visibleMessages.asReversed(),
                key = ::messageKey,
            ) { message ->
                MessageRow(
                    message,
                    previewImages = previewImages,
                    muted = false,
                    onUserClick = onUserClick,
                    onToggleMute = onToggleUserMute,
                    onCopyText = onCopyText,
                    onAttack = onAttack,
                )
            }
        }
        Composer(
            text = composerText,
            enabled = state.connection is ConnectionState.Connected && !state.flooded,
            uploading = uploading,
            placeholder = if (state.flooded) "Anti-flood…" else "Dis quelque chose…",
            onTextChange = onComposerChange,
            onSend = onSend,
            onPickImage = onPickImage,
        )
    }
}

private fun messageKey(message: family.loult.app.domain.model.ChatMessage): String = when (message) {
    is family.loult.app.domain.model.ChatMessage.Text -> "t-${message.date.toBits()}-${message.from.userId}"
    is family.loult.app.domain.model.ChatMessage.Bot -> "b-${message.date.toBits()}-${message.from.userId}"
    is family.loult.app.domain.model.ChatMessage.Me -> "m-${message.date.toBits()}-${message.from.userId}"
    is family.loult.app.domain.model.ChatMessage.System -> "s-${message.date.toBits()}-${message.text.hashCode()}"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    state: RoomState,
    onOpenSettings: () -> Unit,
    onOpenUsers: () -> Unit,
) {
    TopAppBar(
        title = {
            Column(modifier = Modifier.clickable(onClick = onOpenUsers)) {
                Text("loult", style = MaterialTheme.typography.titleLarge)
                ConnectionLabel(state.connection, userCount = state.users.size)
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Réglages",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun ConnectionLabel(connection: ConnectionState, userCount: Int) {
    val text = when (connection) {
        ConnectionState.Connected -> "$userCount connectés"
        ConnectionState.Connecting -> "connexion…"
        ConnectionState.Disconnected -> "déconnecté"
        is ConnectionState.Error -> "erreur: ${connection.message ?: "?"}"
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

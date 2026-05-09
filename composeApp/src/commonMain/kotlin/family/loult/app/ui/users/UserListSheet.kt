package family.loult.app.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import family.loult.app.domain.model.LoultUser
import family.loult.app.ui.components.PokemonAvatar
import family.loult.app.ui.components.SwipeRightToTrigger
import family.loult.app.ui.theme.LoultPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListSheet(
    users: List<LoultUser>,
    mutedUserIds: Set<String>,
    onPickUser: (LoultUser) -> Unit,
    onToggleMute: (LoultUser) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "${users.size} connectés",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn {
                items(items = users, key = { it.userId }) { user ->
                    val muted = user.userId in mutedUserIds
                    SwipeRightToTrigger(
                        onTrigger = { onToggleMute(user) },
                        background = { progress -> SwipeBackground(muted = muted, progress = progress) },
                    ) {
                        UserRow(user = user, muted = muted, onClick = { onPickUser(user) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeBackground(muted: Boolean, progress: Float) {
    val tint = if (muted) LoultPalette.info else LoultPalette.muted
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(tint.copy(alpha = 0.15f + 0.25f * progress))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (muted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (muted) "Réactiver" else "Muet",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun UserRow(user: LoultUser, muted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .alpha(if (muted) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PokemonAvatar(img = user.img, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name,
                    color = parseColorOr(user.color, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (user.adjective.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = user.adjective,
                        color = LoultPalette.muted,
                        style = MaterialTheme.typography.labelMedium,
                        fontStyle = FontStyle.Italic,
                    )
                }
                if (user.isYou) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(toi)",
                        color = LoultPalette.info,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            user.profile?.let { p ->
                val parts = listOfNotNull(
                    p.age?.let { "$it ans" },
                    p.job,
                    p.city,
                ).joinToString(" • ")
                if (parts.isNotEmpty()) {
                    Text(
                        text = parts,
                        color = LoultPalette.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (muted) {
            Icon(
                imageVector = Icons.Filled.VolumeOff,
                contentDescription = "Muet",
                tint = LoultPalette.muted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun parseColorOr(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = hex.removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

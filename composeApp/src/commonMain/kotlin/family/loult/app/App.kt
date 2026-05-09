package family.loult.app

import androidx.compose.runtime.Composable
import family.loult.app.ui.chat.ChatRoomScreen
import family.loult.app.ui.theme.LoultTheme

@Composable
fun App() {
    LoultTheme {
        ChatRoomScreen()
    }
}

# loult-mobile

An unofficial Android client for [loult.family](https://loult.family) — chat, pokémon, attacks, and overlapping TTS voices, in your pocket.

## Download

Grab the latest debug APK from the [releases page](https://github.com/mvmolly/loult-mobile/releases/latest).

Every push to `main` builds a fresh APK and publishes it as `build-<n>`.

## Features

- **Live chat** with the loult WebSocket protocol — text, `/me` actions, private messages (`/mp`), system events.
- **Concurrent TTS playback** — voices mix on top of each other just like the web client.
- **Pokémon avatars** with adjective, color, and tap-to-attack.
- **Tap on a pokémon** → opens a private message draft for that user.
- **Tap on the avatar** → fires an attack.
- **Long-press a message** → copies it to the clipboard with a haptic buzz.
- **Swipe right on a row** (chat or user list) → mute that user. Muted users are greyed out, their voices silenced, and their text hidden.
- **Inline previews** for BNL image and video URLs.
- **Image upload** through the `+` button in the composer.
- **Cookie-based identity** — paste your loult cookie in settings to keep the same pokémon across sessions.

## Stack

- Kotlin Multiplatform (Compose Multiplatform 1.7.3, Material3)
- Ktor 3 WebSockets + cookie-aware HTTP
- ExoPlayer / Media3 for video previews
- `MediaPlayer` per clip for the TTS mixer
- Koin for DI, multiplatform-settings for persistence

## Build

```sh
gradle :androidApp:assembleDebug
# APK at androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

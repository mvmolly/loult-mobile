# loult-mobile

Un client Android non officiel pour [loult.family](https://loult.family) — pour blablater, lancer des attaques et écouter les pokémons crier dans tes oreilles, depuis ton canapé.

## Télécharger

L'APK le plus frais se trouve sur la [page des releases](https://github.com/mvmolly/loult-mobile/releases/latest).

Chaque push sur `main` déclenche une build et publie un nouveau `build-<n>`. Pas de Play Store, pas de signature, juste un APK debug à installer à la main (active "Sources inconnues" dans les réglages Android).

## Ce que ça fait

- **Tchat en direct** via le WebSocket loult — messages, `/me`, messages privés (`/mp`), événements système.
- **Voix qui se chevauchent** comme sur le site, parce qu'écouter un pokémon à la fois c'est trop calme.
- **Avatars pokémon** avec couleur, adjectif, et tout le tralala.
- **Tape sur le nom d'un pokémon** → ça prépare un `/mp` pour lui.
- **Tape sur l'avatar** → tu l'attaques.
- **Appui long sur un message** → c'est copié, ton téléphone vibre.
- **Glisse à droite sur une ligne** (tchat ou liste des connectés) → tu mute. Le muet devient gris, sa voix se tait, ses messages disparaissent.
- **Aperçus en ligne** pour les images et vidéos BNL.
- **Upload d'images** via le `+` à côté du champ de saisie.
- **Cookie loult** dans les réglages pour garder le même pokémon entre deux sessions.

## Sous le capot

- Kotlin Multiplatform (Compose Multiplatform 1.7.3, Material3)
- Ktor 3 (WebSocket + HTTP avec gestion des cookies)
- Media3 / ExoPlayer pour les previews vidéo
- Un `MediaPlayer` par clip pour le mixeur de voix
- Koin pour l'injection, multiplatform-settings pour la persistance

## Build local

```sh
gradle :androidApp:assembleDebug
# APK : androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## C'est quoi loult déjà ?

Si tu lis ce README sans connaître loult, [va voir](https://loult.family). Si après ça t'as toujours pas compris, c'est probablement mieux comme ça.

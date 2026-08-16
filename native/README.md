# LocalStream — Application Android Native

Application **100 % native** (Kotlin + Jetpack Compose + Material 3) de LocalStream pour Android.

> `applicationId` : `com.localstream.app`

## Stack technique

| Élément        | Choix                                             |
| -------------- | ------------------------------------------------- |
| Langage        | Kotlin 2.0                                         |
| UI             | Jetpack Compose + Material 3, thème sombre unique  |
| Lecteur Vidéo  | ExoPlayer (androidx.media3)                        |
| Base de données| Room (androidx.room)                              |
| Persistance    | Jetpack DataStore & EncryptedSharedPreferences     |
| Réseau / API   | Retrofit, OkHttp, kotlinx.serialization            |
| Images         | Coil Compose                                       |
| Architecture   | MVVM — `ui/` (screens, composables), `domain/` et `data/` |
| DI             | Injection manuelle via `AppContainer`              |
| Navigation     | Navigation Compose                                 |
| minSdk         | 24                                                 |
| compileSdk / targetSdk | 36                                         |

## Build & Commandes Gradle

Depuis ce dossier (`native/`) :

```bash
# APK debug
./gradlew assembleDebug

# Tests unitaires JVM
./gradlew testDebugUnitTest

# Analyse statique (Detekt)
./gradlew detekt

# Tout exécuter (comme la CI)
./gradlew assembleDebug testDebugUnitTest detekt
```

L'APK debug est généré dans `app/build/outputs/apk/debug/`.

Prérequis : JDK 17 et Android SDK (configuré via `ANDROID_HOME` ou Android Studio).

## Navigation & Écrans

Le routage est géré par `NavHost` (`ui/navigation/`) avec une barre de navigation (`LocalStreamBottomBar`) donnant accès aux écrans principaux :

| Route            | Composable / Écran natif                       | Onglet / Description              |
| ---------------- | ---------------------------------------------- | --------------------------------- |
| `home`           | `com.localstream.app.ui.screens.HomeScreen`     | Accueil (Hero, reprises, récents) |
| `library`        | `com.localstream.app.ui.screens.LibraryScreen`  | Bibliothèque (Films / Séries)     |
| `playlists`      | `com.localstream.app.ui.screens.PlaylistsScreen`| Listes de lecture utilisateur     |
| `history`        | `com.localstream.app.ui.screens.HistoryScreen`  | Historique de visionnage          |
| `search`         | `com.localstream.app.ui.screens.SearchScreen`   | Recherche locale et filtres       |
| `settings`       | `com.localstream.app.ui.screens.SettingsScreen` | Paramètres & clés API             |
| `details/{id}`   | `com.localstream.app.ui.screens.DetailsScreen`  | Fiche détaillée film / série      |
| `player/{id}`    | `com.localstream.app.ui.player.PlayerScreen`    | Lecteur vidéo ExoPlayer plein écran |

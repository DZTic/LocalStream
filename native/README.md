# LocalStream — module natif Android

Réécriture **100 % native** (Kotlin + Jetpack Compose) de LocalStream, menée en
parallèle de l'app Capacitor existante (`../android/`). Ce dossier est un projet
Gradle **indépendant** : il ne partage ni configuration ni build avec l'app Capacitor,
qui est remplacée par le module natif en Phase 10.

> ✅ `applicationId` **final** : `com.localstream.app` (Phase 10 — reprise de l'identifiant afin d'hériter des installations existantes).

## Stack

| Élément        | Choix                                             |
| -------------- | ------------------------------------------------- |
| Langage        | Kotlin 2.0                                         |
| UI             | Jetpack Compose + Material 3, thème sombre unique  |
| Lecteur Vidéo  | ExoPlayer (androidx.media3)                        |
| Architecture   | MVVM simple — `ui/` (screens, composables), `domain/` et `data/` |
| DI             | Injection manuelle via `AppContainer`              |
| Navigation     | Navigation Compose                                 |
| minSdk         | 24                                                 |
| compileSdk / targetSdk | 36                                         |

## Builder

Depuis ce dossier (`native/`) :

```bash
# APK debug
./gradlew assembleDebug

# Tests unitaires JVM
./gradlew testDebugUnitTest

# Analyse statique
./gradlew detekt

# Tout (comme la CI)
./gradlew assembleDebug testDebugUnitTest detekt
```

L'APK est généré dans `app/build/outputs/apk/debug/`.

Prérequis : JDK 17 et un Android SDK (via Android Studio ou `ANDROID_HOME`).

## Navigation

`NavHost` (voir `ui/navigation/`) avec les routes : `home`, `search`, `library`,
`playlists`, `history`, `details/{id}`, `player/{id}`, `settings`.

La barre basse (`LocalStreamBottomBar`) reproduit `src/components/BottomNav.tsx`
avec 4 onglets de premier niveau (masquée pendant la lecture vidéo).

## Cartographie des écrans

| Écran natif (route)      | Source web (`src/`)                         | Onglet       | Statut       |
| ------------------------ | ------------------------------------------- | ------------ | ------------ |
| `home`                   | `components/screens/HomeScreen.tsx`         | Accueil      | Phase 7 ✅   |
| `library`                | `components/screens/LibraryScreen.tsx`      | Bibliothèque | Phase 7 ✅   |
| `playlists`              | `components/screens/PlaylistsScreen.tsx`    | Listes       | Phase 8 ✅   |
| `history`                | `components/screens/HistoryScreen.tsx`      | Historique   | Phase 8 ✅   |
| `search`                 | `components/screens/SearchScreen.tsx`       | —            | Phase 7 ✅   |
| `settings`               | `components/SettingsModal.tsx`              | —            | Phase 8 ✅   |
| `details/{id}`           | vue Héro / détails                          | —            | Phase 8 ✅   |
| `player/{id}`            | `components/WebPlayer.tsx` / `PlayerActivity`| —            | Phase 9 ✅   |


# LocalStream — module natif Android

Réécriture **100 % native** (Kotlin + Jetpack Compose) de LocalStream, menée en
parallèle de l'app Capacitor existante (`../android/`). Ce dossier est un projet
Gradle **indépendant** : il ne partage ni configuration ni build avec l'app Capacitor,
qui reste le produit livré jusqu'à la Phase 10.

> ⚠️ `applicationId` **provisoire** : `com.localstream.app.native`.
> Le vrai identifiant (`com.localstream.app`) sera repris en Phase 10 afin d'hériter
> des installations existantes.

## Stack

| Élément        | Choix                                             |
| -------------- | ------------------------------------------------- |
| Langage        | Kotlin 2.0                                         |
| UI             | Jetpack Compose + Material 3, thème sombre unique  |
| Architecture   | MVVM simple — `ui/` (screens, composables). `domain/` et `data/` viendront avec les écrans réels. |
| DI             | Injection manuelle au départ (Hilt réévalué quand `data/` grossira) |
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

## Conventions

- **Style Kotlin** : officiel (`kotlin.code.style=official`), vérifié par detekt.
- **Couleurs** : palette Tailwind transposée dans `ui/theme/Color.kt`
  (`bg-black` → `Black`, `zinc-900` → `Zinc900`, `red-600` → `Red600`).
  Ne pas coder de couleurs en dur dans les écrans ; passer par `MaterialTheme.colorScheme`.
- **Typographie** : titres en `FontWeight.Black` (équivalent `font-black` du web).
- **Versions** : centralisées dans `gradle/libs.versions.toml` (version catalog).
- **Écrans** : un composable racine par écran dans `ui/screens/`, sans logique métier
  (celle-ci ira dans `domain/`).

## Navigation

`NavHost` (voir `ui/navigation/`) avec les routes : `home`, `search`, `library`,
`playlists`, `history`, `details/{id}`, `settings`.

La barre basse (`LocalStreamBottomBar`) reproduit `src/components/BottomNav.tsx`
avec 4 onglets de premier niveau.

## Cartographie des écrans (Phase 0)

Correspondance entre l'UI web actuelle (`../src/`) et les écrans natifs cibles.

| Écran natif (route)      | Source web (`src/`)                         | Onglet       | Statut socle |
| ------------------------ | ------------------------------------------- | ------------ | ------------ |
| `home`                   | `components/screens/HomeScreen.tsx`         | Accueil      | Placeholder  |
| `library`                | `components/screens/LibraryScreen.tsx`      | Bibliothèque | Placeholder  |
| `playlists`              | `components/screens/PlaylistsScreen.tsx`    | Listes       | Placeholder  |
| `history`                | `components/screens/HistoryScreen.tsx`      | Historique   | Placeholder  |
| `search`                 | `components/screens/SearchScreen.tsx`       | —            | Placeholder  |
| `settings`               | `components/SettingsModal.tsx`              | —            | Placeholder  |
| `details/{id}`           | vue Héro / détails (`HomeScreen` + modales) | —            | Placeholder  |

Composants transverses à porter plus tard : `AppHeader`, `VideoCard`, `VideoRow`,
`FilterBar`, `SubtitlesModal`, `WebPlayer` (→ lecteur natif), `PermissionGate`, `Toasts`.

## Feuille de route

Ce module est le **socle** (Phase 1). Les écrans sont des placeholders ; les couches
`data/` (MediaStore, TMDB, OpenSubtitles, préférences) et `domain/` arriveront avec
les phases suivantes. L'app Capacitor (`../android/`) reste livrée jusqu'à la Phase 10.

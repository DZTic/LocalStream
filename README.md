# 🎬 LocalStream

<p align="center">
  <img src="public/logo.png" alt="LocalStream Logo" width="120" />
</p>

<p align="center">
  <strong>Votre médiathèque personnelle, style Netflix — application native Android (Kotlin & Jetpack Compose).</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-1.7-4285F4?logo=android" />
  <img src="https://img.shields.io/badge/Room-2.6-3DDC84?logo=sqlite" />
  <img src="https://img.shields.io/badge/Android-APK-3DDC84?logo=android" />
  <img src="https://img.shields.io/badge/Licence-MIT-green" />
</p>

---

**LocalStream** est une application Android native de streaming multimédia locale, conçue pour transformer vos dossiers de vidéos (films et séries) en une bibliothèque interactive inspirée des plus grandes plateformes de streaming. Elle fonctionne **sans serveur** : tout tourne directement et de manière autonome sur votre téléphone Android.

---

## ✨ Fonctionnalités

### 📱 Interface Natifs Jetpack Compose
- **Design Material 3 & Dark Theme** : interface réactive avec animations fluides, scroll adaptatif et navigation moderne.
- **Barre de navigation basse** (Accueil, Bibliothèque, Listes, Historique) et recherche en temps réel.
- **Section Hero rotative** avec visuels HD, résumés et bouton de lecture rapide.

### 📂 Scan MediaStore & Regroupements
- **Analyse automatique des médias** via MediaStore pour détecter les films, séries et épisodes locaux.
- **Détection des séries et épisodes** (`S01E01`, `1x01`, etc.) regroupés par saison.
- **Regroupement automatique des films en sagas / collections TMDB**.
- **Filtrage des vidéos système/personnelles** (caméra, enregistrements, réseaux sociaux).

### 🖼️ Métadonnées TMDB & Sous-titres OpenSubtitles
- **Enrichissement TMDB** : affiches officielles, arrière-plans, synopsis, genres et dates de sortie.
- **OpenSubtitles API** : recherche et téléchargement de sous-titres directement depuis l'application.

---

## 🛠️ Compilation & Tests

Le projet natif Android se situe dans le dossier `native/`.

```bash
cd native

# Exécuter les tests unitaires
./gradlew testDebugUnitTest

# Vérification du code avec Detekt
./gradlew detekt

# Compiler l'APK Debug
./gradlew assembleDebug
```

L'APK compilé se trouve dans `native/app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 Licence

Distribué sous la licence **MIT**. Voir [LICENSE](LICENSE) pour plus d'informations.

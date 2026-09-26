# Signature des APK release

Jusqu'à la 1.2.1, les APK release étaient signés avec `native/debug.keystore` (clé publique,
dans le dépôt). Ils le sont désormais avec une clé release privée, par **rotation de clé**
(APK Signature Scheme v3) : `release-rotation.lineage` contient la preuve, signée par la clé
debug, que la clé release lui succède. Android 9+ accepte donc la mise à jour par-dessus une
version signée debug, sans désinstallation ni perte de données. Après cette mise à jour,
l'appareil refuse un APK signé seulement par la clé debug.

| Clé     | Certificat SHA-256                                                 |
|---------|--------------------------------------------------------------------|
| debug   | `772c92f8347f7cc8438863af3cfdfa10460a2f4b799071e65c616ebd28804982` |
| release | `3ea1939c09619891e670069351afcd400f382166d4adc7aa0673e606f122ce9c` |

## Où est la clé release

- **Jamais dans le dépôt.** En local : `~/.android-keys/localstream-release.jks` et ses mots de
  passe dans `~/.android-keys/localstream-release.properties` (autre emplacement : variable
  `RELEASE_SIGNING_PROPERTIES`).
- **CI** : secrets GitHub `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- Perdre cette clé rend toute mise à jour impossible : en garder une sauvegarde hors de ce PC.

## Build

`./gradlew assembleRelease` signe avec la clé release si elle est trouvée, puis la tâche
`signReleaseApkWithRotation` re-signe l'APK avec la lignée. Sans clé release, l'APK est signé
avec la clé debug (builds de contributeurs) ; le job release de la CI échoue dans ce cas.

Vérifier un APK :

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

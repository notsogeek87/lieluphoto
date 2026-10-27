# Guide : mise à jour de l'app (releases GitHub)

## Pour qui / pourquoi

Pour l'utilisateur de LieluPhoto : recevoir les nouvelles versions sans passer par un store, comme LieluFind. Pour le mainteneur : savoir ce que le CI doit publier pour que la mise à jour fonctionne.

## Fonctionnement

1. Le CI (`.github/workflows/android-build.yml`) publie à chaque push sur `main` une release GitHub `v<versionName>.<run>` avec l'APK en pièce jointe. Le `versionCode` de l'APK vaut `1000 + run`.
2. `AppUpdateManager.checkForUpdate()` (flavor `default`) lit `https://api.github.com/repos/notsogeek87/lieluphoto/releases/latest`.
3. Si `1000 + run` du tag est supérieur au `BuildConfig.VERSION_CODE` installé, il prend le premier asset `.apk`, le télécharge puis propose l'installation.

```kotlin
// app/src/default/.../update/AppUpdateManager.kt
if (VERSION_CODE_BASE + run <= BuildConfig.VERSION_CODE) return null
```

## Points d'attention

- Le service `camera-api.hinnka.me` n'est plus utilisé pour les mises à jour (il reste utilisé pour le service IA intégré).
- Une release sans asset `.apk`, ou un tag illisible, est ignorée (rien n'est proposé).
- Les builds installés hors CI (versionCode 162) voient toute release du CI comme plus récente.
- Signature : le CI signe avec la clé de publication (secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) et vérifie l'empreinte SHA-256 de l'APK (`5A:0B:BE:F8:AA:9F:28:16:90:30:64:7B:5E:C8:10:2B:47:A9:AC:05:E8:EF:AC:C4:48:58:3A:49:3D:9A:2D:C6`) avant de publier. Sans ces secrets, le build de `main` échoue ; une branche retombe sur `app/debug.keystore` pour son APK de test. La mise à jour intégrée ne peut pas passer d'une signature à l'autre : depuis une version ≤ 1.28.4.292 (clé de debug), désinstaller une fois.
- Conflit probable lors d'un rebase sur upstream : `AppUpdateManager.kt`.

## Vérification au démarrage

Comme dans LieluFind, `MyCameraApplication` appelle `AppUpdateManager.checkOnLaunch(this)` à chaque lancement : l'app interroge la dernière release en arrière-plan, sans rien télécharger. Si elle est plus récente, `AppUpdateManager.state` passe à `Available` et `AppUpdateBanner` (affiché en haut de `MainActivity`) montre « Mise à jour disponible : <version> (build <versionCode>) » avec un bouton « Mettre à jour ».

- « Mettre à jour » télécharge l'APK, avec la progression dans le bandeau, puis lance l'installeur système.
- Si l'APK de cette release est déjà téléchargé, le bandeau propose directement « Installer ».
- Sans l'autorisation « Installer des applis inconnues », les réglages Android s'ouvrent ; au retour, « Installer » relance l'installation.
- En cas d'échec, le bandeau propose « Réessayer ». La croix masque le bandeau jusqu'au prochain lancement.

La vérification manuelle reste disponible dans les réglages et alimente le même bandeau.

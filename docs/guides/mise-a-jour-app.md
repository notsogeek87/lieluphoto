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
- Conflit probable lors d'un rebase sur upstream : `AppUpdateManager.kt`.

## Vérification au démarrage

`MyCameraApplication` appelle `AppUpdateManager.startSilentUpdate(this)` à chaque lancement : l'app interroge la dernière release en arrière-plan, télécharge l'APK s'il est plus récent, puis `AppUpdateInstallPrompt` (MainActivity) affiche « Installer maintenant ». Aucune vérification manuelle n'est nécessaire dans les réglages. Cet appel était commenté depuis le fork upstream ; il est réactivé.

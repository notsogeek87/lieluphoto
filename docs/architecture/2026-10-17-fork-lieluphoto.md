# ADR : fork LieluPhoto de PhotonCamera

- Date : 2026-10-17
- Statut : accepté

## Pour qui / pourquoi

Pour le mainteneur du fork (notsogeek87). Ce document explique pourquoi `main` diverge de `bjzhou/PhotonCamera` et ce que chaque écart change, afin de les retrouver et de les résoudre lors d'un rebase.

## Contexte

PhotonCamera (Apache-2.0) contacte le serveur de l'auteur (`camera-api.hinnka.me`) pour les mises à jour et embarque le SDK de crash reporting Tencent Bugly. Le fork veut une appli installable à côté de l'originale, sans ces appels, avec un accès direct au bokeh depuis le viseur.

## Décisions

| # | Décision | Fichiers | Raison |
|---|----------|----------|--------|
| 1 | Pastille bokeh cliquable dans le viseur (tous modes sauf vidéo) | `ui/camera/CameraScreen.kt`, `strings.xml` (en, fr) | Activer/désactiver sans passer par les réglages |
| 2 | `app_name` = « LieluPhoto », `applicationId` = `eu.lielu.photo` (le `namespace` reste `com.hinnka.mycamera`) | `app/build.gradle.kts`, tous les `values*/strings.xml` | Cohabiter avec l'appli d'origine ; garder le `namespace` évite de renommer les packages |
| 3 | `BuglyHelper` vide (même API), dépendances `libs.bugly.crashreport` retirées | `app/src/default/.../utils/BuglyHelper.kt`, `app/build.gradle.kts` | Aucun envoi de données à Tencent |
| 4 | `UPDATES_ENABLED = false` dans `AppUpdateManager` | `app/src/default/.../update/AppUpdateManager.kt` | Plus d'appel à `camera-api.hinnka.me` pour les mises à jour |

## Conservé volontairement

- `BUILT_IN_API_URL` (`https://camera-api.hinnka.me/v1`, `app/build.gradle.kts`), le service IA intégré, utilisé par `OpenAIApiClient.kt`.
- Le téléchargement du modèle de profondeur (Qualcomm AI Hub, S3), vérifié par taille et SHA-256 dans `DepthModelManager`.

## Limites connues

- Le flavor `google` a son propre `BuglyHelper` (`app/src/google/...`), non modifié.
- Les flavors `samsung`, `meitu` et `dev` n'ont pas été vérifiés.
- Seule la compilation du flavor `default` a été testée (`compileDefaultDebugKotlin`, `buildCMakeDebug`, `assembleDefaultDebug`).

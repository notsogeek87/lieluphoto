# LieluPhoto

Application Android appareil photo, qui retrouve la prise en main d'un appareil photo numérique sans miroir moderne. Fork de `bjzhou/photoncamera`.

* UI : Jetpack Compose
* API Camera2, pas de compatibilité descendante
* minSdk 30, code natif C++ (CMake)

## Avant tout push (obligatoire)

1. `./gradlew compileDefaultDebugKotlin` : doit réussir, sans exception.
2. Si le code natif (`app/src/main/cpp/`) est touché : `./gradlew buildCMakeDebug`.
3. Relire le diff (`git diff`) : accolades équilibrées, imports présents, aucun symbole supprimé encore utilisé.

Le build release (`assembleDefaultRelease`) est celui de la CI GitHub Actions : ne pas le lancer en local. Une tâche n'est terminée que lorsque la CI est verte sur le dernier commit poussé.

Les commandes Gradle doivent pouvoir s'exécuter hors du sandbox ; utiliser le HOME Gradle global, ne pas spécifier de `.gradle-home` projet.

## Méthode de travail

* Petits commits, un sujet par commit, chacun compilant. Un correctif de build ne change que ce que l'erreur demande.
* Dans les gros fichiers (`CameraScreen.kt`, `GalleryEditScreen.kt`), modifier par petites touches ciblées. Ne jamais réécrire ou déplacer un bloc entier sans en avoir fait la demande.
* Ne pas refactorer ni refondre l'interface en dehors de la demande. Un bug de mise en page ne justifie pas de réécrire l'écran.
* Si un push échoue en CI : lire le log, corriger la cause racine, recompiler en local, puis pousser. Maximum 2 tentatives sur la même cause, ensuite s'arrêter et demander.
* Ne jamais ajouter ni modifier de workflow (`.github/workflows/`), de tooling (`package.json`, scripts JS, dossiers d'agent) ni de dépendance sans demande explicite. Toute dépendance passe par `gradle/libs.versions.toml`.

## Règles de code

* Ne jamais compenser ni masquer une erreur par des mesures correctives : corriger la cause.
* Toute modification de code doit garantir une chaîne logique parfaitement fermée.
* Avant d'ajouter une fonctionnalité ou une nouvelle logique, penser d'abord à l'architecture des fichiers et du code, et suivre les patterns existants.
* Ne pas coder les textes en dur : utiliser les ressources (`strings.xml`) pour permettre l'internationalisation.
* Pas de « première version » ni de « version simplifiée » : viser une solution complète et mature.
* Pas de tests unitaires à ajouter, pas d'installation incrémentale.
* En cas de problème : isoler les variables d'abord, déterminer la direction ensuite, s'appuyer sur les journaux.
* Avant d'écrire du code GLES, lire @docs/gles-driver-compatibility.md. Ne le mettre à jour que pour de vrais problèmes de compatibilité.
* Scripts Python : les exécuter et les gérer hors du sandbox avec `uv`.

## Sécurité

* Aucun secret dans le code ni dans les commits (clés API, tokens, mots de passe, keystores). Les valeurs sensibles vont dans `local.properties` ou dans les secrets GitHub.
* Ne jamais modifier ni committer `local.properties`, `app/debug.keystore`, `build/`, `.gradle/`.
* Les releases sont signées par la clé de publication (secrets `KEYSTORE_*`, empreinte vérifiée par la CI, voir le README). `app/debug.keystore` ne sert qu'aux builds locaux et aux APK de test des branches.
* Le service IA intégré (`BUILT_IN_API_URL`, camera-api.hinnka.me) est volontairement conservé.

## Git

* Branche de travail : celle indiquée par la tâche. Sans indication, ne pas pousser sur `main` sans demande.
* Messages de commit : `type(scope): description` (`feat`, `fix`, `docs`, `chore`).
* Ne jamais réécrire l'historique de `main` (pas de force-push), sauf la synchronisation upstream ci-dessous, uniquement sur demande explicite.

## Fork : récupérer les nouveautés de bjzhou

Détails : [docs/guides/sync-upstream.md](docs/guides/sync-upstream.md) et [docs/README.md](docs/README.md).

Remotes : `origin` = notsogeek87/lieluphoto, `upstream` = bjzhou/photoncamera. **Ne jamais pousser sur `upstream`.**
`main` (et `staging`, `lielu`) portent nos modifications au-dessus de `upstream/main` : bouton bokeh, renommage LieluPhoto (`applicationId = eu.lielu.photo`), Bugly retiré, mises à jour via les releases GitHub (voir [docs/guides/mise-a-jour-app.md](docs/guides/mise-a-jour-app.md)).

```
git fetch upstream
git rebase upstream/main        # depuis main
git push --force-with-lease origin main
```

* Faire ensuite la même chose pour `staging` et `lielu` si besoin.
* Conflits probables : `CameraScreen.kt`, `app/build.gradle.kts`, `AppUpdateManager.kt`, `BuglyHelper.kt`, `strings.xml`.
* Après chaque rebase : `./gradlew compileDefaultDebugKotlin` puis `./gradlew buildCMakeDebug`.

# LieluPhoto

## L'application

Application Android appareil photo, qui retrouve la prise en main d'un appareil photo numérique sans miroir moderne.

### Cadre technique

* UI : Jetpack Compose
* API Camera2, pas de compatibilité descendante
* minSdk 30

### Compilation et débogage

Les commandes de compilation doivent pouvoir s'exécuter hors du sandbox ; utiliser le HOME Gradle global, ne pas spécifier de `.gradle-home` projet.

* Compilation complète (sauf nécessité, généralement inutile)

    ```
    ./gradlew assembleDefaultDebug
    ```

* Vérification de compilation (kotlin)

    ```
    ./gradlew compileDefaultDebugKotlin
    ```

* Vérification de compilation (native)

    ```
    ./gradlew buildCMakeDebug
    ```

### Règles

* Pour les scripts Python : les exécuter et les gérer hors du sandbox avec `uv`.
* Ne jamais compenser ni masquer une erreur par des mesures correctives : corriger la cause.
* Toute modification de code doit garantir une chaîne logique parfaitement fermée.
* Avant d'ajouter une fonctionnalité ou une nouvelle logique, penser d'abord à l'architecture des fichiers et du code.
* Ne pas coder les textes en dur : prévoir l'internationalisation.
* En cas de problème : isoler les variables d'abord, déterminer la direction ensuite, s'appuyer sur les journaux.
* Pas de « première version » ni de « version simplifiée » dans les solutions techniques : viser une solution complète et mature.
* Avant d'écrire du code GLES, lire la documentation de compatibilité des drivers GLES et ne la mettre à jour qu'en cas de besoin, avec uniquement du contenu relevant de vrais problèmes de compatibilité : @docs/gles-driver-compatibility.md
* Pas d'installation incrémentale.
* Pas de tests unitaires à ajouter.
* Les sous-tâches peu exigeantes en raisonnement peuvent être confiées à un sous-agent léger.
* Économiser les tokens : planifier en conséquence.

## Fork LieluPhoto : récupérer les nouveautés de bjzhou

Détails : [docs/guides/sync-upstream.md](docs/guides/sync-upstream.md) et [docs/README.md](docs/README.md).

Remotes : `origin` = notsogeek87/lieluphoto, `upstream` = bjzhou/photoncamera. Ne jamais pousser sur `upstream`.
`main` (et `staging`, `lielu`) portent nos modifications au-dessus de `upstream/main` : bouton bokeh, renommage LieluPhoto (`applicationId = eu.lielu.photo`), Bugly retiré, mises à jour via les releases GitHub (voir [docs/guides/mise-a-jour-app.md](docs/guides/mise-a-jour-app.md)).

```
git fetch upstream
git rebase upstream/main        # depuis main
git push --force-with-lease origin main
```

- Faire ensuite la même chose pour `staging` et `lielu` si besoin.
- Conflits probables : `CameraScreen.kt`, `app/build.gradle.kts`, `AppUpdateManager.kt`, `BuglyHelper.kt`, `strings.xml`.
- Après chaque rebase : `./gradlew compileDefaultDebugKotlin` puis `./gradlew buildCMakeDebug`.
- Le service IA intégré (`BUILT_IN_API_URL`, camera-api.hinnka.me) est volontairement conservé.

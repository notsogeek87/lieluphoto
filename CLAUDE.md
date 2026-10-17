AGENTS.md

优先说中文
## Fork LieluPhoto : récupérer les nouveautés de bjzhou

Détails : [docs/guides/sync-upstream.md](docs/guides/sync-upstream.md) et [docs/README.md](docs/README.md).

Remotes : `origin` = notsogeek87/lieluphoto, `upstream` = bjzhou/photoncamera. Ne jamais pousser sur `upstream`.
`main` (et `staging`, `lielu`) portent nos modifications au-dessus de `upstream/main` : bouton bokeh, renommage LieluPhoto (`applicationId = eu.lielu.photo`), Bugly retiré, mises à jour désactivées.

```
git fetch upstream
git rebase upstream/main        # depuis main
git push --force-with-lease origin main
```

- Faire ensuite la même chose pour `staging` et `lielu` si besoin.
- Conflits probables : `CameraScreen.kt`, `app/build.gradle.kts`, `AppUpdateManager.kt`, `BuglyHelper.kt`, `strings.xml`.
- Après chaque rebase : `./gradlew compileDefaultDebugKotlin` puis `./gradlew buildCMakeDebug`.
- Le service IA intégré (`BUILT_IN_API_URL`, camera-api.hinnka.me) est volontairement conservé.

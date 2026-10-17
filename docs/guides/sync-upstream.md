# Guide : récupérer les nouveautés de bjzhou

## Pour qui / pourquoi

Pour le mainteneur du fork. Le fork `notsogeek87/lieluphoto` garde ses modifications (voir [l'ADR](../architecture/2026-10-17-fork-lieluphoto.md)) au-dessus de `upstream/main`.

## Remotes

```
origin    https://github.com/notsogeek87/lieluphoto
upstream  https://github.com/bjzhou/photoncamera     # ne jamais pousser ici
```

## Procédure

```bash
git fetch upstream
git checkout main
git rebase upstream/main
git push --force-with-lease origin main
```

Répéter pour `staging` et `lielu` si elles sont gardées (elles pointent sur le même commit que `main` à la création).

## Conflits probables

`CameraScreen.kt`, `app/build.gradle.kts`, `AppUpdateManager.kt`, `BuglyHelper.kt`, `strings.xml`.

## Vérification après rebase

```bash
./gradlew compileDefaultDebugKotlin
./gradlew buildCMakeDebug
./gradlew :app:assembleDefaultDebug   # optionnel
```

Prérequis : Android SDK 36, NDK `29.0.14206865`, CMake `3.22.1`, `ANDROID_HOME` défini ou `local.properties` avec `sdk.dir=...`.

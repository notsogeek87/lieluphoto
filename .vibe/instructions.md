# LieluPhoto - Instructions pour Vibe Code

## Contexte du Projet

**LieluPhoto** est une application Android open-source de caméra, fork de Photon Camera, conçue pour simuler la prise en main et la qualité d'image des appareils photo numériques sans miroir modernes.

- **Application ID**: `eu.lielu.photo`
- **Fork source**: `bjzhou/photoncamera` (upstream)
- **Dépôt**: `notsogeek87/lieluphoto`
- **Langues**: Multi-langues (FR, EN, CN, JA)

## Stack Technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| **UI** | Jetpack Compose | - |
| **Camera API** | Camera2 API | Android 11+ |
| **Min SDK** | Android API | 30 |
| **Target SDK** | Android API | 36 |
| **Compile SDK** | Android API | 36 |
| **Kotlin** | JVM Target | 17 |
| **NDK** | Native Development Kit | 29.0.14206865 |
| **CMake** | Build System | 3.22.1 |
| **Gradle** | Build Tool | KTS (Kotlin DSL) |
| **C++** | Native Code | C++17/20 |
| **Python** | Scripts | 3.x |

## Architecture

```
.
├── app/                          # Code source Android
│   ├── src/main/java/            # Code Kotlin
│   ├── src/main/cpp/            # Code natif (C++)
│   │   ├── libultrahdr/          # HDR processing
│   │   ├── libjpeg-turbo/        # JPEG encoding
│   │   └── CMakeLists.txt        # Configuration CMake
│   └── build.gradle.kts          # Configuration Gradle
├── scripts/                      # Scripts Python
│   ├── *.py                     # Outils de calibration, conversion LUT
│   └── research/                 # Scripts de recherche
├── docs/                         # Documentation technique
│   ├── architecture/            # Architecture decisions
│   ├── guides/                   # Guides utilisateur
│   ├── research/                 # Recherche technique
│   └── README.md                 # Documentation principale
├── .github/workflows/            # CI/CD
│   └── android-build.yml         # Build et release
└── gradle/                       # Configuration Gradle
```

## Fonctionnalités Clés

1. **Support LUT avancé**
   - Formats: `.cube`, `.png` (Halfs/Fulls), `.xmp`
   - Prévisualisation temps réel avec shaders haute performance
   - Import personnalisé de bibliothèques LUT

2. **Recettes de couleur profondes**
   - Réglages de base: Exposition, Contraste, Lumières, Ombres, Saturation, Température, Teinte
   - Effets artistiques: Vignette, Grain, Fade, Bleach Bypass
   - Filtres pro: Bloom, Dispersion, Noise, Esthétique low-pixel

3. **Photos Motion**
   - Adaptation multi-fournisseur (Xiaomi, Samsung, Pixel)
   - Capture de courtes vidéos avec les images fixes

4. **Rafale haute vitesse**
   - Pas de limite sur le nombre de cadres
   - Application de filtres LUT en temps réel

5. **Synthèse multi-cadre & Super Résolution**
   - Empilement multi-frame pour améliorer la qualité
   - Réduction de bruit tout en préservant les détails naturels

6. **Bokeh à grande ouverture**
   - Modèle IA local: midas-v2 (optimisé pour Qualcomm)
   - Détection de profondeur précise

7. **Mode Phantom**
   - Capture directe via l'API système caméra
   - Contourne les problèmes de qualité d'image des APIs tierces

8. **Simulation de couleur IA**
   - Technologie: Google Nano Banana 2
   - Analyse des photos de référence
   - Génération de LUTs personnalisés

## Workflow de Développement

### Synchronisation avec Upstream

```bash
# Récupérer les changements depuis bjzhou/photoncamera
git fetch upstream
git rebase upstream/main

# Résoudre les conflits (fichiers fréquents)
# - CameraScreen.kt
# - app/build.gradle.kts
# - AppUpdateManager.kt
# - BuglyHelper.kt
# - strings.xml

# Vérifier la compilation
./gradlew compileDefaultDebugKotlin
./gradlew buildCMakeDebug

# Pousser les changements
git push --force-with-lease origin main
```

### Build et Test

```bash
# Build complet (debug)
./gradlew assembleDefaultDebug

# Vérification compilation Kotlin
./gradlew compileDefaultDebugKotlin

# Vérification compilation native
./gradlew buildCMakeDebug

# Build release
./gradlew :app:assembleDefaultRelease
```

### Gestion des Clés API

- **BUILT_IN_API_URL**: `https://camera-api.hinnka.me/v1`
- **BUILT_IN_API_KEY**: À définir dans `local.properties`
- **BUILT_IN_API_KEY_GOOGLE**: À définir dans `local.properties`

## Règles de Build et Validation pour Vibe Code

### Validation Obligatoire

**Aucune tâche n'est considérée comme terminée tant que le build CI n'a pas réussi.**

### Workflow de Développement

1. **Avant toute modification** :
   - Synchroniser avec upstream: `git fetch upstream && git rebase upstream/main`
   - Vérifier que le code existant compile: `./gradlew compileDefaultDebugKotlin`

2. **Pendant le développement** :
   - Vérifier régulièrement la compilation Kotlin: `./gradlew compileDefaultDebugKotlin`
   - Vérifier la compilation native: `./gradlew buildCMakeDebug`
   - **Ne pas pousser sans vérification locale**

3. **Avant le push** :
   - Exécuter le build release complet: `./gradlew :app:assembleDefaultRelease`
   - Si la compilation locale échoue, **ne pas pousser**
   - Regrouper toutes les modifications liées dans un seul commit

4. **Après le push** :
   - Vérifier le résultat de GitHub Actions: `gh run list --workflow android-build.yml --limit 5`
   - Si le build échoue:
     - Lire les logs: `gh run view <run-id> --log`
     - Identifier la cause racine
     - Corriger et pousser une correction **unique et complète**
     - Limite: 2 tentatives maximum par cause

5. **Validation finale** :
   - Le build doit réussir sur GitHub Actions avec `./gradlew :app:assembleDefaultRelease`
   - Environnement requis: JDK 21, NDK 29.0.14206865, CMake 3.22.1

### Capacités et Limitations

**Disponible depuis Vibe Code mobile:**
- ✅ Exécution de commandes shell
- ✅ Accès au wrapper Gradle
- ✅ Lecture des logs GitHub Actions via `gh` CLI
- ✅ Vérification de compilation Kotlin

**Non disponible depuis Vibe Code mobile:**
- ❌ SDK Android complet
- ❌ NDK (Native Development Kit)
- ❌ CMake
- ❌ Exécution de compilation native locale
- ❌ Exécution de build release complet localement

**Conséquence:** La validation finale DOIT passer par GitHub Actions.

### Règles Spécifiques

1. **Ne jamais déclarer une tâche terminée si le build CI échoue**
2. **Ne jamais masquer les erreurs de compilation**
3. **Ne jamais désactiver les contrôles de compilation**
4. **Toujours corriger la cause racine, pas le symptôme**
5. **Toujours vérifier GitHub Actions après un push**
6. **Limite: 2 tentatives de correction par cause**

### Commandes de Vérification

```bash
# Vérification locale (avant commit)
./gradlew compileDefaultDebugKotlin
./gradlew buildCMakeDebug

# Build release (si possible, sinon validation via CI)
./gradlew :app:assembleDefaultRelease

# Vérification CI (après push)
gh run list --workflow android-build.yml --limit 5
gh run view <run-id> --log
```

## Règles pour Vibe Code

### ✅ À FAIRE

1. **Respecter l'architecture existante**
   - Ne pas modifier la structure des dossiers sans validation
   - Maintenir la cohérence avec le code upstream

2. **Qualité de code**
   - Chaîne logique parfaitement fermée
   - Pas de code commenté (sauf documentation)
   - Internationalisation obligatoire (pas de textes en dur)

3. **Build et Performance**
   - Vérifier la compilation avant tout commit
   - Optimiser pour Android 11+ (API 30)
   - Respecter les contraintes mémoire (4G JVM max)

4. **Documentation**
   - Mettre à jour la documentation technique
   - Documenter les changements significatifs
   - Maintenir les README multilingues

5. **Sécurité**
   - Ne jamais committer de clés API ou secrets
   - Utiliser `local.properties` pour les configurations sensibles
   - Respecter les permissions Android

### ❌ À ÉVITER

1. **Ne pas** compenser ou masquer des erreurs
   - Corriger la cause racine, pas les symptômes

2. **Ne pas** créer de "première version" ou "version simplifiée"
   - Viser une solution complète et mature dès le départ

3. **Ne pas** ajouter de tests unitaires
   - Le projet n'utilise pas de tests unitaires (décision architecturale)

4. **Ne pas** installer de dépendances incrémentales
   - Toutes les dépendances doivent être validées

5. **Ne pas** modifier les scripts Python dans le sandbox
   - Les exécuter avec `uv` hors du sandbox

### Scripts Python

Les scripts dans `scripts/` sont gérés séparément :
- Calibration: `calibrate_dng_noise_model.py`, `calc_matrices.py`
- Conversion: `convert_luts.py`, `convert_phocus_hncs.py`
- Génération: `generate_camera_background.py`, `import_material_icons.py`
- Recherche: `scripts/research/`

**Ne pas modifier** sans comprendre le contexte complet.

## Configuration Spécifique

### Remotes Git
```
origin    = notsogeek87/lieluphoto (push autorisé)
upstream = bjzhou/photoncamera (lecture seule, NE JAMAIS PUSH)
```

### Branches
- `main`: Branche principale avec nos modifications
- `staging`: Branche de test
- `lielu`: Branche de développement

### Modifications Spécifiques LieluPhoto
1. Bouton bokeh personnalisé
2. Renommage: `applicationId = eu.lielu.photo`
3. Bugly retiré
4. Mises à jour via GitHub Releases
5. Service IA intégré conservé: `BUILT_IN_API_URL`

## Ressources Utiles

- [Documentation Architecture](docs/architecture/)
- [Guides Utilisateur](docs/guides/)
- [Recherche Technique](docs/research/)
- [Sync Upstream](docs/guides/sync-upstream.md)
- [Mise à jour App](docs/guides/mise-a-jour-app.md)

## Contacts et Support

- **Issues**: Ouvrir une issue GitHub
- **Discussions**: Voir les discussions GitHub
- **Contributions**: PR bienvenues avec respect des règles

---

*Dernière mise à jour: $(date)*
*Agent: Vibe Code (Mistral AI)*

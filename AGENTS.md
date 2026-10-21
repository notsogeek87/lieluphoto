# LieluPhoto - Guide pour tous les Agents IA

## ⚠️ RÈGLE FONDAMENTALE POUR TOUS LES AGENTS IA
**L'agent code SEUL, de A à Z.**
- Il **ne demande JAMAIS** à l'utilisateur de coder.
- Il **vérifie systématiquement** que :
  - Le code compile (`./gradlew assembleDebug`, etc.).
  - **TOUS les tests passent** avant tout push.
- Si un choix d'architecture bloque, il pose **une seule question courte** pour trancher.

---

## 📋 Présentation

Ce document définit les règles, conventions et bonnes pratiques pour **tous les agents IA** (Vibe Code, Claude, GitHub Copilot, etc.) travaillant sur le projet **LieluPhoto**.

> **⚠️ IMPORTANT**: Ce guide **remplace** tout guide existant. En cas de conflit, **ce document fait autorité**.

---

## 🏗️ Architecture du Projet

### Structure des Dossiers

```
.
├── app/                          # Code application Android
│   ├── src/main/java/            # Code source Kotlin
│   │   └── com/hinnka/mycamera/ # Package principal
│   ├── src/main/cpp/            # Code natif (C++/OpenGL)
│   │   ├── libultrahdr/          # Traitement HDR
│   │   ├── libjpeg-turbo/        # Encodage JPEG
│   │   └── ...                  # Autres bibliothèques
│   └── build.gradle.kts          # Configuration Gradle
│
├── scripts/                      # Scripts d'automatisation
│   ├── *.py                     # Scripts Python (calibration, conversion)
│   └── research/                 # Scripts de recherche
│
├── docs/                         # Documentation
│   ├── architecture/            # Décisions architecturales
│   ├── guides/                   # Guides utilisateur
│   ├── research/                 # Documentation technique
│   └── README.md                 # Documentation principale
│
├── .github/                      # Configuration GitHub
│   └── workflows/               # CI/CD
│
├── gradle/                       # Configuration Gradle
│   └── wrapper/                  # Wrapper Gradle
│
└── .vibe/                        # Configuration Vibe Code
    ├── instructions.md          # Instructions spécifiques
    └── config.json               # Configuration agent
```

### Stack Technique

| Catégorie | Technologie | Version/Details |
|-----------|-------------|-----------------|
| **Langage Principal** | Kotlin | 1.9.x (JVM 17) |
| **Langage Natif** | C++ | C++17/20 |
| **UI Framework** | Jetpack Compose | - |
| **Camera API** | Camera2 API | Android 11+ |
| **Build System** | Gradle | KTS (Kotlin DSL) |
| **Native Build** | CMake | 3.22.1 |
| **NDK** | Android NDK | 29.0.14206865 |
| **Min SDK** | Android | API 30 |
| **Target SDK** | Android | API 36 |
| **Dependencies** | Version Catalog | `gradle/libs.versions.toml` |

---

## 🎯 Règles Fondamentales

### ⚠️ RÈGLES NON-NÉGOCIABLES (Violation = Rejet Automatique)

1. **🚫 PAS DE COMMIT SUR `upstream`**
   - `upstream = bjzhou/photoncamera` est **LECTURE SEULE**
   - **NE JAMAIS** faire `git push upstream`
   - Seule la branche `origin/main` (notsogeek87/lieluphoto) peut être poussée

2. **🚫 PAS DE SECRETS DANS LE CODE**
   - **NE JAMAIS** committer de clés API, tokens, mots de passe
   - Utiliser `local.properties` pour toutes les configurations sensibles
   - Vérifier avec `git-secrets` avant tout commit

3. **🚫 PAS DE MODIFICATION DES SCRIPTS PYTHON DANS LE SANDBOX**
   - Les scripts Python (`scripts/*.py`) doivent être exécutés **hors du sandbox** avec `uv`
   - Ne pas modifier ces scripts sans validation préalable

4. **🚫 PAS DE TESTS UNITAIRES**
   - Le projet a décidé de **ne pas utiliser** de tests unitaires
   - Ne pas ajouter de fichiers `*Test.kt` ou `*Spec.kt`
   - Les tests existants ne doivent pas être modifiés

5. **🚫 PAS DE DÉPENDANCES INCréMENTALES**
   - Toute nouvelle dépendance doit être validée
   - Ajouter dans `gradle/libs.versions.toml` avec justification

### ✅ RÈGLES DE QUALITÉ OBLIGATOIRES

1. **🔍 CORRIGER LA CAUSE, PAS LE SYMPTÔME**
   ```kotlin
   // ❌ MAUVAIS: Masquer l'erreur
   try { riskyOperation() } catch (e: Exception) { /* ignore */ }
   
   // ✅ BON: Corriger la cause
   if (condition) { safeOperation() }
   ```

2. **🌐 INTERNATIONALISATION OBLIGATOIRE**
   ```kotlin
   // ❌ MAUVAIS: Texte en dur
   Text("Hello World")
   
   // ✅ BON: Utiliser les ressources
   Text(stringResource(R.string.hello_world))
   ```

3. **📦 CHAÎNE LOGIQUE PARFAITEMENT FERMÉE**
   - Toute fonction doit avoir un chemin de retour clair
   - Pas de `null` non géré
   - Pas de `throw` non capturé (sauf exceptions critiques)

4. **🎨 RESPECTER L'ARCHITECTURE EXISTANTE**
   - Comprendre le code avant de modifier
   - Suivre les patterns existants
   - Ne pas réinventer la roue

5. **✅ VÉRIFIER LA COMPILATION AVANT TOUT COMMIT**
   ```bash
   # Vérification minimale
   ./gradlew compileDefaultDebugKotlin
   ./gradlew buildCMakeDebug
   ```

---

## 📝 Conventions de Code

### Kotlin

```kotlin
// ✅ Nommage
val userName: String = "John"  // camelCase pour variables
fun calculateTotal(): Int     // camelCase pour fonctions
class UserProfile             // PascalCase pour classes
const val MAX_SIZE = 100      // UPPER_SNAKE_CASE pour constantes

// ✅ Format
fun example(
    param1: String,
    param2: Int,
): ReturnType {
    // Corps de fonction
}

// ✅ Null Safety
// Préférer les types non-nullables
fun process(data: String)  // ✅ BON
fun process(data: String?) // ⚠️ À éviter si possible

// ✅ Collections
// Préférer les collections immuables
val list = listOf(1, 2, 3)      // ✅ BON
val mutableList = mutableListOf() // ⚠️ À utiliser avec parcimonie
```

### C++

```cpp
// ✅ Nommage
int user_age = 0;           // snake_case pour variables
void CalculateTotal()      // PascalCase pour fonctions
class UserProfile          // PascalCase pour classes
const int MAX_SIZE = 100;  // UPPER_SNAKE_CASE pour constantes

// ✅ GLES/OpenGL
// Voir docs/gles-driver-compatibility.md avant toute modification
// Ne pas ajouter de code GLES sans validation

// ✅ Pointeurs
// Préférer les smart pointers
std::unique_ptr<User> user = std::make_unique<User>();  // ✅ BON
User* user = new User();                                    // ❌ À éviter
```

### JavaScript/TypeScript (Scripts Vibe)

```javascript
// ✅ ES Modules (toujours utiliser import/export)
import { function1 } from './module.js';
export const value = 42;

// ✅ Nommage
const userName = 'John';      // camelCase
function calculateTotal() {}  // camelCase
class UserProfile {}          // PascalCase
const MAX_SIZE = 100;         // UPPER_SNAKE_CASE

// ✅ Typage TypeScript
// Toujours typer les paramètres et retours
function process(data: string): number {  // ✅ BON
    return data.length;
}
```

### Python

```python
# ✅ Nommage
user_name = "John"           # snake_case pour variables

def calculate_total():      # snake_case pour fonctions
    pass

class UserProfile:          # PascalCase pour classes
    pass

MAX_SIZE = 100              # UPPER_SNAKE_CASE pour constantes

# ✅ Typage
from typing import Optional, List, Dict

def process(data: str) -> int:  # ✅ BON
    return len(data)
```

### Gradle (KTS)

```kotlin
// ✅ Configuration
android {
    defaultConfig {
        minSdk = 30
        targetSdk = 36
        versionCode = 162
        versionName = "1.28.4"
    }
}

// ✅ Dépendances
// Toujours utiliser le version catalog
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
```

---

## 🔧 Workflows de Développement

### 1. Synchronisation avec Upstream

```bash
# 1. Récupérer les dernières modifications
git fetch upstream

# 2. Rebase sur upstream/main
git rebase upstream/main

# 3. Résoudre les conflits (fichiers fréquents)
#    - CameraScreen.kt
#    - app/build.gradle.kts
#    - AppUpdateManager.kt
#    - BuglyHelper.kt
#    - strings.xml

# 4. Vérifier la compilation
./gradlew compileDefaultDebugKotlin
./gradlew buildCMakeDebug

# 5. Pousser sur origin
git push --force-with-lease origin main
```

### 2. Création d'une Nouvelle Fonctionnalité

```bash
# 1. Créer une branche
#    Format: feature/<description> ou fix/<description>
git checkout -b feature/nouvelle-fonctionnalite

# 2. Développer avec vérifications régulières
./gradlew compileDefaultDebugKotlin

# 3. Commit avec message clair
#    Format: type(scope): description
#    Exemples:
#    - feat(camera): add bokeh toggle button
#    - fix(raw): correct noise profile calculation
#    - docs: update architecture documentation
#    - chore: update dependencies
git commit -m "feat(camera): add bokeh toggle button"

# 4. Pousser la branche
git push -u origin feature/nouvelle-fonctionnalite

# 5. Créer une Pull Request
#    - Utiliser le template .github/PULL_REQUEST_TEMPLATE.md
#    - Attendre les reviews
```

### 3. Correction de Bug

```bash
# 1. Reproduire le bug
#    - Identifier les étapes de reproduction
#    - Capturer les logs si nécessaire

# 2. Isoler le problème
#    - Identifier le composant concerné
#    - Comprendre la cause racine

# 3. Implémenter la correction
#    - Corriger la cause, pas le symptôme
#    - Ajouter des logs de debug si nécessaire (à retirer après)

# 4. Vérifier la correction
#    - Tester manuellement
#    - Vérifier la compilation

# 5. Commit et PR
#    - Message clair: fix(scope): description du bug corrigé
```

---

## 📊 Vérifications Pré-Commit

### Checklist Obligatoire

- [ ] ✅ Code compile sans erreur (`./gradlew compileDefaultDebugKotlin`)
- [ ] ✅ Code natif compile (`./gradlew buildCMakeDebug`)
- [ ] ✅ Pas de secrets committés (vérifier avec `git-secrets`)
- [ ] ✅ Pas de textes en dur (internationalisation)
- [ ] ✅ Respect des conventions de nommage
- [ ] ✅ Pas de `TODO` ou `FIXME` non documentés
- [ ] ✅ Documentation mise à jour si nécessaire
- [ ] ✅ Changements testés manuellement

### Outils de Vérification

```bash
# Vérification de compilation Kotlin
./gradlew compileDefaultDebugKotlin

# Vérification de compilation native
./gradlew buildCMakeDebug

# Recherche de secrets (si git-secrets installé)
git secrets --scan

# Vérification des fichiers modifiés
git diff --cached

# Vérification des fichiers non suivis
git status
```

---

## 📁 Gestion des Fichiers

### Fichiers à NE JAMAIS Modifier

| Fichier/Dossier | Raison |
|----------------|--------|
| `app/debug.keystore` | Clé de signature debug |
| `local.properties` | Configuration locale (API keys) |
| `scripts/*.py` (sans validation) | Scripts critiques |
| `.gradle-home/` | Cache Gradle |
| `build/` | Fichiers générés |

### Fichiers à Mettre à Jour avec Précaution

| Fichier | Précautions |
|---------|-------------|
| `app/build.gradle.kts` | Vérifier la compilation après modification |
| `settings.gradle.kts` | Impact sur tout le projet |
| `gradle.properties` | Configurations globales |
| `gradle/libs.versions.toml` | Gestion des dépendances |
| `CMakeLists.txt` | Configuration native |
| `*.kt` dans `app/src/main/` | Code principal |

### Fichiers Générés

Ne **JAMAIS** committer les fichiers générés :
- `build/`
- `.gradle/`
- `.idea/`
- `*.iml`
- `.DS_Store`
- `__pycache__/`

Voir `.gitignore` pour la liste complète.

---

## 🚀 CI/CD

### Workflow Android Build

Le workflow `.github/workflows/android-build.yml` :

1. **Trigger**: Push sur toute branche (sauf docs/)
2. **Environnement**: Ubuntu latest, JDK 21
3. **Étapes**:
   - Checkout
   - Setup JDK 21
   - Setup Gradle
   - Installation NDK et CMake
   - Écriture de `local.properties` (avec secrets)
   - Détermination de la version
   - Compilation release
   - Renommage APK
   - Upload artifact
   - Publication release (main seulement)

### Publication de Release

- **Branche `main`**: Publication automatique d'une release GitHub
- **Autres branches**: Publication d'une pré-release (remplacée à chaque build)
- **Tag format**: `v<version>.<run_number>` pour main, `debug-<branch>` pour autres

---

## 📚 Documentation

### Documentation Existante

| Fichier | Description |
|---------|-------------|
| `README.md` | Documentation principale (FR) |
| `README_EN.md` | Documentation principale (EN) |
| `README_CN.md` | Documentation principale (CN) |
| `README_JA.md` | Documentation principale (JA) |
| `docs/README.md` | Index de la documentation |
| `docs/architecture/` | Décisions architecturales |
| `docs/guides/` | Guides utilisateur |
| `docs/research/` | Recherche technique |
| `.vibe/instructions.md` | Instructions spécifiques pour Vibe Code |

### Mise à Jour de la Documentation

1. **Quand mettre à jour** :
   - Nouvelle fonctionnalité ajoutée
   - Changement d'architecture
   - Modification de workflow
   - Correction de bug majeure

2. **Format** :
   - Utiliser le Markdown
   - Structurer avec des titres clairs
   - Inclure des exemples de code si nécessaire
   - Maintenir la cohérence entre les versions linguistiques

---

## 🔒 Sécurité

### Gestion des Clés API

```properties
# local.properties (NE JAMAIS COMMITTER)
BUILT_IN_API_KEY=<votre_clé>
BUILT_IN_API_KEY_GOOGLE=<votre_clé_google>
sdk.dir=/path/to/android/sdk
```

### Bonnes Pratiques de Sécurité

1. **Ne jamais** committer de fichiers contenant :
   - Clés API
   - Tokens d'authentification
   - Mots de passe
   - Secrets de signature
   - Informations personnelles

2. **Utiliser** :
   - GitHub Secrets pour les variables CI
   - `local.properties` pour les configurations locales
   - `.gitignore` pour exclure les fichiers sensibles

3. **Vérifier** :
   - `git status` avant commit
   - `git diff --cached` avant push
   - Outils de détection de secrets

---

## 🎯 Objectifs du Projet

### Priorités Actuelles

1. **Stabilité** : Maintenir la compatibilité avec upstream
2. **Performance** : Optimiser pour Android 11+
3. **Fonctionnalités** : Ajouter des capacités uniques (Bokeh, HDR, etc.)
4. **Qualité** : Améliorer la qualité d'image
5. **Expérience Utilisateur** : Interface intuitive et professionnelle

### Fonctionnalités à Maintenir

- Support multi-langues
- Compatibilité multi-fournisseurs (Xiaomi, Samsung, Pixel)
- Performance élevée (rafale, prévisualisation)
- Qualité d'image professionnelle
- Intégration IA (Midas-v2, Nano Banana 2)

---

## 📞 Support et Contacts

### Comment Obtenir de l'Aide

1. **Documentation** : Lire les fichiers README et docs/
2. **Issues GitHub** : Ouvrir une issue pour les bugs ou questions
3. **Discussions GitHub** : Pour les discussions générales
4. **Pull Requests** : Contribuer avec des PR bien documentées

### Règles pour les Contributions

1. **Respecter** ce guide AGENTS.md
2. **Suivre** les conventions de code
3. **Documenter** les changements
4. **Tester** avant de committer
5. **Être patient** : Les reviews peuvent prendre du temps

---

## 📝 Changelog des Règles

| Date | Modification | Auteur |
|------|--------------|--------|
| 2024-XX-XX | Création initiale | Vibe Code |
| 2024-XX-XX | Ajout des règles de sécurité | Vibe Code |

---

## 🏁 Conclusion

En suivant ce guide, vous contribuerez à maintenir **LieluPhoto** comme un projet de haute qualité, stable et performant.

> **Rappel**: En cas de doute, **demander** plutôt que de deviner. La communication est essentielle pour la qualité du code.

---

*Document généré par Vibe Code (Mistral AI)*
*Dernière mise à jour: $(date)*

# LieluPhoto - Instructions pour Vibe Code (Règles de Sécurité Strictes)

## ⚠️ RÈGLE FONDAMENTALE - À LIRE EN PREMIER

**TOUT COMMIT DOIT ÊTRE VÉRIFIÉ AVANT PUSH.**

- Si l'environnement le permet (accès shell + Gradle), **exécuter les commandes de vérification**
- Si l'environnement ne le permet pas, **ne pas pousser** et demander une vérification manuelle
- **NE JAMAIS** déclarer un build validé sans preuve de compilation réussie

**En cas d'échec CI après push :**
1. Lire les logs GitHub Actions
2. Identifier la cause racine
3. Corriger UNE SEULE FOIS
4. Vérifier localement avant de repusher
5. **MAXIMUM 2 tentatives** pour une même cause, puis s'arrêter et produire un diagnostic

---

## Contexte du Projet

**LieluPhoto** est une application Android open-source de caméra, fork de Photon Camera.

- **Application ID**: `eu.lielu.photo`
- **Fork source**: `bjzhou/photoncamera` (upstream - LECTURE SEULE)
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

## 🔒 RÈGLES DE SÉCURITÉ NON-NÉGOCIABLES

### ❌ INTERDITIONS ABSOLUES (Violation = Arrêt immédiat)

1. **PAS DE PUSH SUR UPSTREAM**
   ```bash
   # INTERDIT :
   git push upstream
   git push bjzhou/photoncamera
   
   # AUTORISÉ :
   git push origin main
   git push --force-with-lease origin main
   ```

2. **PAS DE SECRETS DANS LE CODE**
   - **NE JAMAIS** committer : API keys, tokens, mots de passe, clés de signature
   - Utiliser `local.properties` pour les configurations sensibles
   - Vérifier avec `git secrets --scan` ou `grep -rE '(password|secret|token|api_key)'`

3. **PAS DE MODIFICATION DES SCRIPTS PYTHON DANS LE SANDBOX**
   - Les scripts `scripts/*.py` doivent être exécutés **hors du sandbox** avec `uv`
   - Ne pas modifier sans validation préalable

4. **PAS DE DÉSACTIVATION DE LA CI**
   - Ne jamais modifier `.github/workflows/` pour contourner les erreurs
   - Ne jamais désactiver les workflows GitHub Actions

5. **PAS DE FORCE PUSH SAUF AVEC --force-with-lease**
   ```bash
   # INTERDIT :
   git push --force
   
   # AUTORISÉ :
   git push --force-with-lease origin main
   ```

### ✅ VÉRIFICATIONS OBLIGATOIRES AVANT TOUT COMMIT

#### 1. Vérification de compilation (si environnement le permet)
```bash
# Vérification minimale - TOUJOURS exécuter si possible
./gradlew compileDefaultDebugKotlin
./gradlew buildCMakeDebug

# Vérification complète (si temps disponible)
./gradlew compileDefaultReleaseKotlin
./gradlew buildCMakeRelease
```

**Si une commande échoue :**
- **NE PAS PUSHER**
- Corriger l'erreur
- Relancer la vérification
- **MAXIMUM 2 tentatives** pour une même erreur

#### 2. Vérification des imports
```bash
# Après modification de fichiers Kotlin, vérifier les imports
# Exemple pour GalleryEditScreen.kt
grep -n "import" app/src/main/java/com/hinnka/mycamera/ui/gallery/GalleryEditScreen.kt
```

#### 3. Vérification des fichiers modifiés
```bash
# Vérifier TOUS les fichiers modifiés
 git diff --cached
 git status

# Vérifier les nouveaux fichiers non suivis
git status | grep "Untracked files"
```

#### 4. Vérification des secrets
```bash
# Rechercher des secrets avant commit
git secrets --scan 2>/dev/null || grep -rE '(password|secret|token|api_key|BUILT_IN_API)' --include='*.kt' --include='*.java' --include='*.cpp' --include='*.h' .
```

### 🎯 COMMANDES DE COMPILATION À UTILISER

**Toujours utiliser les variantes Release pour la vérification finale :**
```bash
# Compilation Kotlin Release (obligatoire avant push)
./gradlew compileDefaultReleaseKotlin

# Compilation native Release
./gradlew buildCMakeRelease

# Build complet Release (si possible)
./gradlew :app:assembleDefaultRelease
```

**Ne pas se contenter de Debug :**
- Debug peut réussir alors que Release échoue (ProGuard, optimisations, etc.)
- **TOUJOURS tester Release si l'environnement le permet**

---

## Workflow de Développement Sécurisé

### 1. Avant toute modification
```bash
# Synchroniser avec upstream
 git fetch upstream
 git rebase upstream/main

# Vérifier la compilation actuelle
./gradlew compileDefaultDebugKotlin
./gradlew buildCMakeDebug
```

### 2. Pendant le développement
```bash
# Après chaque modification significative
./gradlew compileDefaultDebugKotlin

# Avant de committer
./gradlew compileDefaultReleaseKotlin
./gradlew buildCMakeRelease
```

### 3. Avant de pousser
```bash
# Vérification finale
./gradlew compileDefaultReleaseKotlin
./gradlew buildCMakeRelease

# Vérifier les imports
grep -n "import" app/src/main/java/com/hinnka/mycamera/ui/gallery/*.kt

# Vérifier git status
git status
git diff --cached

# Pousser avec lease (empêche les écrasements)
git push --force-with-lease origin main
```

### 4. Après avoir poussé
```bash
# Vérifier le statut de GitHub Actions (si capacité disponible)
# Si échec :
# 1. Lire les logs
# 2. Identifier la cause
# 3. Corriger UNE SEULE FOIS
# 4. Vérifier localement
# 5. Repousser
# 6. MAXIMUM 2 tentatives, puis STOP et diagnostic
```

---

## 🚨 PROCÉDURE EN CAS D'ÉCHEC DE BUILD

### Étape 1 : Détection
- Recevoir notification d'échec (via GitHub ou manuellement)
- **NE PAS** enchaîner les corrections sans diagnostic

### Étape 2 : Diagnostic
```bash
# Récupérer les logs du dernier build échoué
# (via GitHub Actions UI ou API)

# Identifier :
# - Le commit en échec
# - Le fichier source de l'erreur
# - Le type d'erreur (compilation, import, etc.)
```

### Étape 3 : Correction (MAXIMUM 2 tentatives)
```bash
# Tentative 1 :
# - Corriger la cause racine
# - Vérifier localement
# - Pousser

# Si échec à nouveau :
# - Analyser pourquoi la correction n'a pas fonctionné
# - Corriger différemment
# - Vérifier localement
# - Pousser (2ème tentative)

# Si échec une 3ème fois :
# - STOP
# - Produire un diagnostic structuré
# - NE PAS continuer à corriger
```

### Étape 4 : Diagnostic structuré (si 2 échecs)
```markdown
## Diagnostic de build échoué

- **Commit**: [sha]
- **Fichier**: [chemin]
- **Erreur**: [message d'erreur exact]
- **Cause racine**: [analyse]
- **Tentatives**: 2/2
- **Solution proposée**: [description]
- **Blocage**: [raison si applicable]
```

---

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
   - **TOUJOURS vérifier la compilation avant commit**
   - Optimiser pour Android 11+ (API 30)
   - Respecter les contraintes mémoire (4G JVM max)

4. **Sécurité**
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

6. **Ne pas** déclarer un build validé sans preuve
   - **TOUJOURS** avoir la preuve de compilation réussie

---

## Configuration Spécifique

### Remotes Git
```
origin    = notsogeek87/lieluphoto (push autorisé)
upstream = bjzhou/photoncamera (LECTURE SEULE, NE JAMAIS PUSH)
```

### Branches
- `main`: Branche principale avec nos modifications
- `staging`: Branche de test
- `lielu`: Branche de développement

### Commandes de build prioritaires
```bash
# Vérification minimale (obligatoire)
./gradlew compileDefaultReleaseKotlin
./gradlew buildCMakeRelease

# Build complet
./gradlew :app:assembleDefaultRelease
```

---

## Checklist Anti-Erreurs Fréquentes

### ❌ ERREURS À ÉVITER (Déjà faites)

- [ ] **Imports manquants** : Vérifier TOUS les imports après modification de fichiers
  - Ex: `EditScreenLayout` utilisé dans `GalleryEditScreen.kt` -> import manquant = build KO
- [ ] **Fichiers nouveaux non ajoutés** : `git add` pour TOUS les nouveaux fichiers
  - Ex: `EditScreenLayout.kt` créée mais pas `git add` = fichier non poussé
- [ ] **Variables non initialisées** : Vérifier que toutes les `val`/`var` ont une valeur
- [ ] **Null Safety** : Pas de `!!` non nécessaire, préférer les types non-nullables
- [ ] **Changements de signature** : Vérifier que les appels de fonctions correspondent aux définitions
- [ ] **Double déclaration** : Vérifier pas de variables déclarées 2 fois

### ✅ VÉRIFIER AVANT TOUT PUSH

```bash
# 1. Vérifier la compilation Kotlin Release
./gradlew compileDefaultReleaseKotlin

# 2. Vérifier la compilation native Release
./gradlew buildCMakeRelease

# 3. Vérifier les imports dans TOUS les fichiers modifiés
find app/src/main/java -name "*.kt" -newer /tmp/marker -exec grep -n "import" {} \;

# 4. Vérifier les nouveaux fichiers
git status | grep "Untracked files"

# 5. Vérifier les appels de fonctions existantes
# (exemple pour GalleryEditScreen)
grep -n "setEdit\|EditPlayer\|Zoomable" app/src/main/java/com/hinnka/mycamera/ui/gallery/GalleryEditScreen.kt
```

---

## Ressources Utiles

- [Documentation Architecture](docs/architecture/)
- [Guides Utilisateur](docs/guides/)
- [Recherche Technique](docs/research/)
- [AGENTS.md](AGENTS.md) - Guide complet pour tous les agents

---

## Contacts et Support

- **Issues**: Ouvrir une issue GitHub
- **Discussions**: Voir les discussions GitHub
- **Contributions**: PR bienvenues avec respect des règles

---

*Dernière mise à jour: $(date)
*Agent: Vibe Code (Mistral AI)
*Version: 2.0.0 (Sécurité renforcée)*

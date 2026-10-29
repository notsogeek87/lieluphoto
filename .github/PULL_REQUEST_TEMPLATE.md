# Pull Request - LieluPhoto

## 📝 Description

**Brève description** des changements proposés. Expliquer le **pourquoi** autant que le **quoi**.

---

## ✅ Type de Changement

- [ ] ✨ **Nouvelle fonctionnalité** (ajout de fonctionnalité)
- [ ] 🐛 **Correction de bug** (fix d'un problème existant)
- [ ] 📚 **Documentation** (mise à jour de docs, README, etc.)
- [ ] 🔧 **Refactor** (restructuration de code sans changement fonctionnel)
- [ ] 🎨 **UI/UX** (amélioration de l'interface ou expérience utilisateur)
- [ ] ⚡ **Performance** (optimisation de performance)
- [ ] 🔒 **Sécurité** (correction de vulnérabilité)
- [ ] 📦 **Dépendances** (mise à jour de bibliothèques)
- [ ] 🌐 **Internationalisation** (traductions, support multi-langues)
- [ ] 🧪 **Expérimental** (nouvelle fonctionnalité en test)
- [ ] ⬆️ **Sync Upstream** (synchronisation avec bjzhou/photoncamera)
- [ ] 🗑️ **Nettoyage** (suppression de code mort, optimisation)

---

## 🔗 Issues Relatives

- **Clôt** : #
- **Relé à** : #
- **Bloc** : #

*Supprimer les sections non pertinentes.*

---

## 📊 Modifications

### Changements Principaux

- **Fichiers modifiés** : Liste des fichiers principaux modifiés
- **Lignes de code** : ~+X/-Y (estimation)
- **Tests** : [ ] Ajoutés / [ ] Modifiés / [ ] Supprimés / [ ] Non applicable

### Détails Techniques

```
# Exemple de structure de changements

## Backend (Kotlin/C++)
- Modification de `CameraScreen.kt` pour ajouter le bouton bokeh
- Mise à jour de `RawProcessor.cpp` pour le traitement HDR

## Frontend (Compose)
- Ajout de `BokehToggle.kt` pour le composant UI
- Modification de `CameraUI.kt` pour intégrer le toggle

## Configuration
- Mise à jour de `app/build.gradle.kts` pour la version 1.29.0
```

---

## 📸 Captures d'Écran / Vidéos

*Si applicable, ajouter des captures d'écran ou liens vers des vidéos démontrant les changements.*

| Avant | Après |
|-------|-------|
| ![avant](url) | ![après](url) |

---

## ✅ Checklist

### Obligatoire (Doit être coché avant review)

- [ ] ✅ **Code compile** (`./gradlew compileDefaultDebugKotlin`)
- [ ] ✅ **Code natif compile** (`./gradlew buildCMakeDebug`)
- [ ] ✅ **Pas de secrets** committés (vérifié avec git-secrets)
- [ ] ✅ **Internationalisation** : Tous les textes sont dans les ressources
- [ ] ✅ **Conventions de code** respectées
- [ ] ✅ **Documentation mise à jour** (si nécessaire)
- [ ] ✅ **Changements testés** manuellement

### Recommandé

- [ ] 📝 **Message de commit** suit le format `type(scope): description`
- [ ] 🧹 **Code propre** : Pas de `TODO`, `FIXME`, ou code commenté
- [ ] 🔍 **Tests** : Nouveaux tests ajoutés si applicable (sauf si explicitement non requis)
- [ ] 📊 **Performance** : Pas de régression de performance
- [ ] 🎯 **Compatibilité** : Fonctionne sur Android 11+ (API 30)
- [ ] 🌍 **Multi-langues** : Vérifié pour toutes les langues supportées

### Synchronisation Upstream

- [ ] 🔄 **Rebase sur upstream/main** avant de merger
- [ ] 🔍 **Conflits résolus** avec la branche main
- [ ] ✅ **Vérification post-rebase** : Compilation OK après rebase

---

## 🔬 Testing

### Environnement de Test

- **Appareil** : [ex: Pixel 7, Samsung S23, etc.]
- **Version Android** : [ex: Android 14]
- **Configuration** : [ex: Debug/Release]

### Scénarios Testés

- [ ] 📷 **Capture photo** : Fonctionne correctement
- [ ] 🎥 **Mode vidéo** : Enregistrement OK
- [ ] 🌈 **LUT** : Application des filtres OK
- [ ] 🎭 **Bokeh** : Effet de profondeur OK
- [ ] ⚡ **Rafale** : Capture rapide OK
- [ ] 🌃 **Mode nuit** : Fonctionne correctement
- [ ] 🔍 **Zoom** : Fonctionne correctement
- [ ] ⚙️ **Paramètres** : Tous les réglages accessibles

### Résultats

```
✅ Tous les tests passent
⚠️ Certains tests échouent (détails ci-dessous)
❌ Tests non exécutés
```

---

## 📈 Impact

### Performance

- **Avant** : [ex: 60 FPS]
- **Après** : [ex: 60 FPS]
- **Impact** : [ex: Aucun / +5% / -2%]

### Taille de l'APK

- **Avant** : [ex: 50.2 MB]
- **Après** : [ex: 50.5 MB]
- **Delta** : [ex: +0.3 MB]

### Compatibilité

- **Min SDK** : [ex: 30 (inchangé)]
- **Target SDK** : [ex: 36 (inchangé)]
- **Nouveaux permissions** : [ex: Aucun / CAMERA, etc.]

---

## 💬 Notes pour les Reviewers

*Ajouter toute information supplémentaire utile pour la review :*

- Problèmes connus
- Décisions architecturales
- Questions ouvertes
- Points nécessitant une attention particulière

---

## 🚀 Déploiement

### Pré-requis

- [ ] ✅ **CI passe** (GitHub Actions)
- [ ] ✅ **Review approuvée** par au moins 1 maintainer
- [ ] ✅ **Pas de conflits** avec la branche cible

### Étapes Post-Merge

1. **Vérifier le build CI** sur GitHub Actions
2. **Tester la release** si applicable
3. **Mettre à jour la documentation** si nécessaire
4. **Annonce** (si fonctionnalité majeure)

---

## 📝 Journal des Changements (Pour Maintainers)

*À remplir par les maintainers pendant la review :*

| Date | Reviewer | Commentaire | Statut |
|------|----------|-------------|--------|
| YYYY-MM-DD | @reviewer | Commentaire 1 | ✅ Approuvé |
| YYYY-MM-DD | @reviewer | Commentaire 2 | ⚠️ Changements requis |

---

## 🏷️ Labels

*Labels à ajouter après la création de la PR :*

- `enhancement` / `bug` / `documentation` / `refactor` / etc.
- `needs-review`
- `wip` (si Work In Progress)

---

> **⚠️ IMPORTANT** : Les PR qui ne respectent pas ce template ou les règles définies dans [AGENTS.md](../AGENTS.md) **seront fermées sans review**.


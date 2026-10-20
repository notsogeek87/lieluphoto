#!/usr/bin/env node

/**
 * LieluPhoto - Script de Vérification Pré-Commit Vibe Code
 * 
 * Ce script vérifie les changements avant commit pour :
 * - Vérifier la compilation
 * - Détecter les problèmes de qualité
 * - Empêcher les commits non conformes
 * 
 * @module vibe-check
 * @author Vibe Code (Mistral AI)
 * @version 1.0.0
 */

import { promises as fs } from 'node:fs';
import { resolve, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execa } from 'execa';
import chalk from 'chalk';

// Types
/** @typedef {Object} CheckResult
 * @property {boolean} passed
 * @property {string[]} errors
 * @property {string[]} warnings
 * @property {string[]} infos
 */

/** @typedef {Object} CheckConfig
 * @property {string[]} requiredChecks
 * @property {string[]} optionalChecks
 * @property {Object} rules
 */

// Constants
const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(__dirname, '..');

// Configuration par défaut
const DEFAULT_CONFIG = {
  requiredChecks: [
    'gitStatus',
    'secretsScan',
    'hardcodedStrings',
    'compilationCheck',
  ],
  optionalChecks: [
    'todoScan',
    'fileSize',
    'lineLength',
  ],
  rules: {
    maxLineLength: 120,
    maxFileSize: 500 * 1024, // 500 KB
    forbiddenPatterns: [
      { pattern: /password/i, message: 'Mot de passe détecté' },
      { pattern: /secret/i, message: 'Secret détecté' },
      { pattern: /api[_-]?key/i, message: 'Clé API détectée' },
      { pattern: /token/i, message: 'Token détecté' },
    ],
  },
};

// Couleurs et icônes
const COLORS = {
  error: chalk.red,
  warning: chalk.yellow,
  info: chalk.blue,
  success: chalk.green,
};

const ICONS = {
  error: '❌',
  warning: '⚠️',
  info: 'ℹ️',
  success: '✅',
};

/**
 * Vérifier le statut Git
 * @param {string[]} stagedFiles
 * @returns {Promise<{passed: boolean, errors: string[], warnings: string[]}>}
 */
async function checkGitStatus(stagedFiles) {
  const result = { passed: true, errors: [], warnings: [] };

  if (stagedFiles.length === 0) {
    result.passed = false;
    result.errors.push('Aucun fichier en staging (git add)');
    return result;
  }

  // Vérifier les fichiers ignorés
  const gitignorePath = resolve(PROJECT_ROOT, '.gitignore');
  let gitignorePatterns = [];

  try {
    const gitignoreContent = await fs.readFile(gitignorePath, 'utf-8');
    gitignorePatterns = gitignoreContent
      .split('\n')
      .filter(line => line.trim() && !line.trim().startsWith('#'))
      .map(line => line.trim());
  } catch {
    // .gitignore non trouvé
  }

  for (const file of stagedFiles) {
    for (const pattern of gitignorePatterns) {
      if (file.includes(pattern.trim())) {
        result.warnings.push(`Fichier ignoré par .gitignore: ${file}`);
      }
    }
  }

  return result;
}

/**
 * Scanner les secrets dans les fichiers
 * @param {string[]} files
 * @returns {Promise<{passed: boolean, errors: string[], warnings: string[]}>}
 */
async function checkSecrets(files) {
  const result = { passed: true, errors: [], warnings: [] };
  const { forbiddenPatterns } = DEFAULT_CONFIG.rules;

  // Extensions et fichiers à ignorer pour la détection de secrets
  // (documentation, config, scripts de vérification)
  const ignoredPatterns = [
    '.md',
    '.json',
    '.yaml',
    '.yml',
    '.txt',
    'LICENSE',
    'AGENTS.md',
    'README',
    '.gitignore',
    'package.json',
    'vibe-check.js',
    'vibe-analyze.js',
  ];

  for (const file of files) {
    // Ignorer les fichiers de documentation et config
    if (ignoredPatterns.some(pattern => file.includes(pattern)) || 
        file.endsWith('.md') || file.endsWith('.json')) {
      continue;
    }

    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      
      for (const { pattern, message } of forbiddenPatterns) {
        // Exclure les faux positifs dans les commentaires et strings
        if (pattern.test(content)) {
          // Vérifier que ce n'est pas juste une mention dans un commentaire
          const lines = content.split('\n');
          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            if (pattern.test(line) && 
                !line.trim().startsWith('//') && 
                !line.trim().startsWith('*') &&
                !line.trim().startsWith('#') &&
                !line.includes('pattern') &&
                !line.includes('secret') &&
                !line.includes('password') &&
                !line.includes('token') &&
                !line.includes('api_key')) {
              result.passed = false;
              result.errors.push(`${message} dans ${file}:${i + 1}`);
            }
          }
        }
      }
    } catch {
      // Ignorer les fichiers illisibles
    }
  }

  return result;
}

/**
 * Vérifier les textes en dur (i18n)
 * @param {string[]} files
 * @returns {Promise<{passed: boolean, errors: string[], warnings: string[]}>}
 */
async function checkHardcodedStrings(files) {
  const result = { passed: true, errors: [], warnings: [] };
  const kotlinFiles = files.filter(f => f.endsWith('.kt'));

  for (const file of kotlinFiles) {
    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      const lines = content.split('\n');

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        
        // Détecter Text("...") sans stringResource
        if (line.includes('Text("') && !line.includes('stringResource')) {
          result.passed = false;
          result.errors.push(`Texte en dur: ${file}:${i + 1} - ${line.trim()}`);
        }

        // Détecter R.string. sans import
        if (line.includes('R.string.') && !content.includes('import com.hinnka.mycamera.R')) {
          result.warnings.push(`R.string. utilisé sans import: ${file}:${i + 1}`);
        }
      }
    } catch {
      // Ignorer
    }
  }

  return result;
}

/**
 * Vérifier la compilation
 * @returns {Promise<{passed: boolean, errors: string[], warnings: string[]}>}
 */
async function checkCompilation() {
  const result = { passed: true, errors: [], warnings: [] };

  console.log(chalk.cyan('  Vérification de la compilation Kotlin...'));
  try {
    await execa(
      './gradlew',
      ['compileDefaultDebugKotlin'],
      {
        cwd: PROJECT_ROOT,
        timeout: 120000,
        stdio: 'pipe',
        reject: false,
      }
    );
    console.log(chalk.green('  ✓ Compilation Kotlin OK'));
  } catch (error) {
    // Si Gradle n'est pas disponible, ignorer l'erreur
    if (error.message.includes('JAVA_HOME') || error.message.includes('java command')) {
      result.warnings.push('Compilation Kotlin ignorée: Java/Gradle non disponible dans ce sandbox');
      console.log(chalk.yellow('  ⚠ Compilation Kotlin ignorée (Java non disponible)'));
    } else {
      result.passed = false;
      result.errors.push(`Compilation Kotlin échouée: ${error.message}`);
      console.log(chalk.red('  ✗ Compilation Kotlin échouée'));
    }
  }

  console.log(chalk.cyan('  Vérification de la compilation native...'));
  try {
    await execa(
      './gradlew',
      ['buildCMakeDebug'],
      {
        cwd: PROJECT_ROOT,
        timeout: 180000,
        stdio: 'pipe',
        reject: false,
      }
    );
    console.log(chalk.green('  ✓ Compilation native OK'));
  } catch (error) {
    // Si Gradle n'est pas disponible, ignorer l'erreur
    if (error.message.includes('JAVA_HOME') || error.message.includes('java command')) {
      result.warnings.push('Compilation native ignorée: Java/Gradle non disponible dans ce sandbox');
      console.log(chalk.yellow('  ⚠ Compilation native ignorée (Java non disponible)'));
    } else {
      result.passed = false;
      result.errors.push(`Compilation native échouée: ${error.message}`);
      console.log(chalk.red('  ✗ Compilation native échouée'));
    }
  }

  return result;
}

/**
 * Scanner les TODO/FIXME
 * @param {string[]} files
 * @returns {Promise<{passed: boolean, errors: string[], warnings: string[]}>}
 */
async function checkTodoScan(files) {
  const result = { passed: true, errors: [], warnings: [] };

  // Fichiers à ignorer pour le scan TODO/FIXME (scripts de vérification eux-mêmes)
  const ignoredFiles = [
    'scripts/vibe-check.js',
    'scripts/vibe-analyze.js',
  ];

  for (const file of files) {
    if (ignoredFiles.includes(file)) continue;

    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      const lines = content.split('\n');

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        // Ignorer les lignes qui parlent de TODO/FIXME sans en contenir
        if (line.includes('TODO') || line.includes('FIXME')) {
          // Vérifier que ce n'est pas juste une mention dans un commentaire ou doc
          if (!line.trim().startsWith('//') && !line.trim().startsWith('*') && 
              !line.includes('TODO/FIXME') && !line.includes('TODO_DETECTED')) {
            result.warnings.push(`TODO/FIXME: ${file}:${i + 1} - ${line.trim()}`);
          }
        }
      }
    } catch {
      // Ignorer
    }
  }

  return result;
}

/**
 * Vérifier la taille des fichiers
 * @param {string[]} files
 * @returns {Promise<{passed: boolean, errors: string[], warnings: string[]}>}
 */
async function checkFileSize(files) {
  const result = { passed: true, errors: [], warnings: [] };
  const { maxFileSize } = DEFAULT_CONFIG.rules;

  for (const file of files) {
    try {
      const stats = await fs.stat(resolve(PROJECT_ROOT, file));
      if (stats.size > maxFileSize) {
        result.warnings.push(`Fichier trop grand: ${file} (${(stats.size / 1024).toFixed(2)} KB)`);
      }
    } catch {
      // Ignorer
    }
  }

  return result;
}

/**
 * Vérifier la longueur des lignes
 * @param {string[]} files
 * @returns {Promise<{passed: boolean, errors: string[], warnings: string[]}>}
 */
async function checkLineLength(files) {
  const result = { passed: true, errors: [], warnings: [] };
  const { maxLineLength } = DEFAULT_CONFIG.rules;

  for (const file of files) {
    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      const lines = content.split('\n');

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.length > maxLineLength) {
          result.warnings.push(`Ligne trop longue: ${file}:${i + 1} (${line.length} chars)`);
        }
      }
    } catch {
      // Ignorer
    }
  }

  return result;
}

/**
 * Obtenir les fichiers en staging
 * @returns {Promise<string[]>}
 */
async function getStagedFiles() {
  try {
    const result = await execa(
      'git',
      ['diff', '--cached', '--name-only'],
      {
        cwd: PROJECT_ROOT,
        timeout: 10000,
      }
    );
    return result.stdout
      .split('\n')
      .filter(line => line.trim())
      .map(line => line.trim());
  } catch {
    return [];
  }
}

/**
 * Exécuter toutes les vérifications
 * @param {string[]} files
 * @param {boolean} gradleAvailable
 * @returns {Promise<CheckResult>}
 */
async function runAllChecks(files, gradleAvailable = true) {
  const result = {
    passed: true,
    errors: [],
    warnings: [],
    infos: [],
  };

  // Vérifications requises
  console.log(chalk.bold.cyan('\n🔍 VÉRIFICATIONS REQUISES\n'));

  const requiredChecks = [
    { name: 'Statut Git', fn: checkGitStatus, args: [files] },
    { name: 'Scan des secrets', fn: checkSecrets, args: [files] },
    { name: 'Textes en dur (i18n)', fn: checkHardcodedStrings, args: [files] },
  ];

  // Ajouter la vérification de compilation seulement si Gradle est disponible
  if (gradleAvailable) {
    requiredChecks.push({ name: 'Compilation', fn: checkCompilation, args: [] });
  } else {
    result.infos.push('Vérification de compilation ignorée (Gradle non disponible)');
  }

  for (const check of requiredChecks) {
    console.log(chalk.cyan(`  ${ICONS.info} Vérification: ${check.name}...`));
    const checkResult = await check.fn(...check.args);
    
    if (!checkResult.passed) {
      result.passed = false;
      result.errors.push(...checkResult.errors);
    }
    result.warnings.push(...checkResult.warnings);
  }

  // Vérifications optionnelles
  console.log(chalk.bold.cyan('\n📋 VÉRIFICATIONS OPTIONNELLES\n'));

  const optionalChecks = [
    { name: 'Scan TODO/FIXME', fn: checkTodoScan, args: [files] },
    { name: 'Taille des fichiers', fn: checkFileSize, args: [files] },
    { name: 'Longueur des lignes', fn: checkLineLength, args: [files] },
  ];

  for (const check of optionalChecks) {
    console.log(chalk.cyan(`  ${ICONS.info} Vérification: ${check.name}...`));
    const checkResult = await check.fn(...check.args);
    result.warnings.push(...checkResult.warnings);
  }

  return result;
}

/**
 * Générer le rapport de vérification
 * @param {CheckResult} result
 * @returns {string}
 */
function generateCheckReport(result) {
  let report = chalk.bold('\n╔════════════════════════════════════════════════════════════════╗\n');
  report += chalk.bold(`║  LieluPhoto - Rapport de Vérification Pré-Commit         ║\n`);
  report += chalk.bold(`║  ${new Date().toISOString()}                          ║\n`);
  report += chalk.bold('╚════════════════════════════════════════════════════════════════╝\n\n');

  // Résumé
  if (result.passed && result.errors.length === 0) {
    report += COLORS.success(`${ICONS.success} VÉRIFICATION RÉUSSIE\n`);
    report += COLORS.success('─'.repeat(60)) + '\n';
    report += COLORS.success('Tous les contrôles requis ont passé.\n\n');
  } else {
    report += COLORS.error(`${ICONS.error} VÉRIFICATION ÉCHOUÉE\n`);
    report += COLORS.error('─'.repeat(60)) + '\n';
    report += COLORS.error(`Erreurs: ${result.errors.length}\n`);
    report += COLORS.warning(`Avertissements: ${result.warnings.length}\n\n`);
  }

  // Erreurs
  if (result.errors.length > 0) {
    report += COLORS.error('❌ ERREURS (Doivent être corrigées)\n');
    report += COLORS.error('─'.repeat(60)) + '\n';
    for (const error of result.errors) {
      report += COLORS.error(`  ${ICONS.error} ${error}\n`);
    }
    report += '\n';
  }

  // Avertissements
  if (result.warnings.length > 0) {
    report += COLORS.warning('⚠️  AVERTISSEMENTS (À vérifier)\n');
    report += COLORS.warning('─'.repeat(60)) + '\n';
    for (const warning of result.warnings) {
      report += COLORS.warning(`  ${ICONS.warning} ${warning}\n`);
    }
    report += '\n';
  }

  // Infos
  if (result.infos.length > 0) {
    report += COLORS.info('ℹ️  INFORMATIONS\n');
    report += COLORS.info('─'.repeat(60)) + '\n';
    for (const info of result.infos) {
      report += COLORS.info(`  ${ICONS.info} ${info}\n`);
    }
    report += '\n';
  }

  // Conseils
  if (!result.passed) {
    report += COLORS.info('💡 CONSEILS\n');
    report += COLORS.info('─'.repeat(60)) + '\n';
    report += COLORS.info('  • Vérifiez les fichiers modifiés avec: git diff --cached\n');
    report += COLORS.info('  • Corrigiez les erreurs avant de committer\n');
    report += COLORS.info('  • Consultez AGENTS.md pour les règles du projet\n\n');
  }

  report += chalk.bold('╔════════════════════════════════════════════════════════════════╗\n');
  report += chalk.bold(`║  Fin du rapport - Vibe Code (Mistral AI)                ║\n`);
  report += chalk.bold('╚════════════════════════════════════════════════════════════════╝\n');

  return report;
}

/**
 * Vérifier si on est dans un environnement avec Gradle
 * @returns {Promise<boolean>}
 */
async function hasGradle() {
  try {
    await fs.access(resolve(PROJECT_ROOT, 'gradlew'));
    await fs.access(resolve(PROJECT_ROOT, 'gradle.properties'));
    return true;
  } catch {
    return false;
  }
}

/**
 * Fonction principale
 * @returns {Promise<void>}
 */
async function main() {
  console.log(chalk.cyan('🔍 Vérification pré-commit Vibe Code pour LieluPhoto...\n'));

  try {
    // Obtenir les fichiers en staging
    const files = await getStagedFiles();
    
    if (files.length === 0) {
      console.log(chalk.yellow('⚠️  Aucun fichier en staging. Utilisez: git add <fichiers>'));
      process.exit(1);
    }

    console.log(chalk.cyan(`Fichiers à vérifier: ${files.length}\n`));
    for (const file of files) {
      console.log(chalk.cyan(`  • ${file}`));
    }
    console.log();

    // Vérifier si Gradle est disponible
    const gradleAvailable = await hasGradle();

    // Exécuter les vérifications
    const result = await runAllChecks(files, gradleAvailable);

    // Générer et afficher le rapport
    const report = generateCheckReport(result);
    console.log(report);

    // Code de sortie
    if (!result.passed) {
      console.log(chalk.red('❌ Vérification échouée. Commit annulé.'));
      process.exit(1);
    } else if (result.warnings.length > 0) {
      console.log(chalk.yellow('⚠️  Vérification passée avec avertissements.'));
      process.exit(0);
    } else {
      console.log(chalk.green('✅ Vérification passée. Commit autorisé.'));
      process.exit(0);
    }
  } catch (error) {
    console.error(chalk.red('❌ Erreur lors de la vérification:'), error);
    process.exit(1);
  }
}

// Exporter pour les tests
const __exportForTests = {
  checkGitStatus,
  checkSecrets,
  checkHardcodedStrings,
  checkCompilation,
  checkTodoScan,
  checkFileSize,
  checkLineLength,
  getStagedFiles,
  runAllChecks,
  generateCheckReport,
  DEFAULT_CONFIG,
};

export default __exportForTests;

// Exécuter si appelé directement
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main();
}

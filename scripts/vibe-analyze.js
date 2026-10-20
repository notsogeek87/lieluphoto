#!/usr/bin/env node

/**
 * LieluPhoto - Script d'Analyse Vibe Code
 * 
 * Ce script analyse le projet pour :
 * - Vérifier la structure du projet
 * - Analyser les dépendances
 * - Détecter les problèmes potentiels
 * - Générer un rapport d'analyse
 * 
 * @module vibe-analyze
 * @author Vibe Code (Mistral AI)
 * @version 1.0.0
 */

import { promises as fs } from 'node:fs';
import { resolve, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execa } from 'execa';
import chalk from 'chalk';

// Types
/** @typedef {Object} AnalyzeResult
 * @property {string} timestamp
 * @property {string} projectName
 * @property {ProjectStructure} structure
 * @property {DependencyAnalysis} dependencies
 * @property {CodeQualityAnalysis} codeQuality
 * @property {Issue[]} issues
 * @property {string} summary
 */

/** @typedef {Object} ProjectStructure
 * @property {string} root
 * @property {string[]} keyDirectories
 * @property {string[]} sourceFiles
 * @property {string[]} configFiles
 * @property {string[]} docFiles
 */

/** @typedef {Object} DependencyAnalysis
 * @property {Object} gradle
 * @property {Object} npm
 * @property {Object} python
 */

/** @typedef {Object} CodeQualityAnalysis
 * @property {number} totalFiles
 * @property {number} totalLines
 * @property {number} kotlinFiles
 * @property {number} cppFiles
 * @property {number} pythonFiles
 * @property {number} testFiles
 */

/** @typedef {Object} Issue
 * @property {'error' | 'warning' | 'info'} severity
 * @property {string} code
 * @property {string} message
 * @property {string} file
 * @property {number} line
 */

// Constants
const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(__dirname, '..');
const CONFIG = JSON.parse(
  await fs.readFile(resolve(PROJECT_ROOT, '.vibe', 'config.json'), 'utf-8')
);

const SEVERITY_COLORS = {
  error: chalk.red,
  warning: chalk.yellow,
  info: chalk.blue,
};

const SEVERITY_ICONS = {
  error: '❌',
  warning: '⚠️',
  info: 'ℹ️',
};

/**
 * Analyser la structure du projet
 * @returns {Promise<ProjectStructure>}
 */
async function analyzeStructure() {
  const root = PROJECT_ROOT;
  
  // Lister les répertoires clés
  const keyDirectories = [
    'app',
    'scripts',
    'docs',
    'gradle',
    '.github',
    '.vibe',
    'research',
    'tools',
  ].filter(d => {
    try {
      return fs.stat(resolve(root, d)).then(() => true).catch(() => false);
    } catch {
      return false;
    }
  });

  // Compter les fichiers par type
  const sourceFiles = [];
  const configFiles = [];
  const docFiles = [];

  // Extensions à analyser
  const SOURCE_EXTENSIONS = ['.kt', '.java', '.cpp', '.h', '.hpp', '.c'];
  const CONFIG_EXTENSIONS = ['.gradle.kts', '.gradle', '.properties', '.toml', '.json', '.xml'];
  const DOC_EXTENSIONS = ['.md', '.txt', '.rst'];

  // Parcourir le projet
  const walk = async (dir, relativePath = '') => {
    const entries = await fs.readdir(resolve(dir, relativePath), { withFileTypes: true });
    
    for (const entry of entries) {
      const fullPath = resolve(dir, relativePath, entry.name);
      const relPath = relative(PROJECT_ROOT, fullPath);
      
      // Ignorer les dossiers cachés (sauf .github, .vibe)
      if (entry.isDirectory()) {
        if (!entry.name.startsWith('.') || ['.github', '.vibe'].includes(entry.name)) {
          await walk(dir, resolve(relativePath, entry.name));
        }
      } else if (entry.isFile()) {
        // Ignorer les fichiers dans .git
        if (relPath.includes('.git')) continue;
        
        // Catégoriser les fichiers
        if (SOURCE_EXTENSIONS.some(ext => entry.name.endsWith(ext))) {
          sourceFiles.push(relPath);
        } else if (CONFIG_EXTENSIONS.some(ext => entry.name.endsWith(ext))) {
          configFiles.push(relPath);
        } else if (DOC_EXTENSIONS.some(ext => entry.name.endsWith(ext))) {
          docFiles.push(relPath);
        }
      }
    }
  };

  await walk(PROJECT_ROOT);

  return {
    root,
    keyDirectories,
    sourceFiles,
    configFiles,
    docFiles,
  };
}

/**
 * Analyser les dépendances Gradle
 * @returns {Promise<Object>}
 */
async function analyzeGradleDependencies() {
  try {
    const result = await execa(
      './gradlew',
      ['dependencies', '--configuration', 'default'],
      { 
        cwd: PROJECT_ROOT,
        timeout: 120000,
        reject: false,
      }
    );
    
    // Parser les dépendances (simplifié)
    const dependencies = {};
    const lines = result.stdout.split('\n');
    
    for (const line of lines) {
      const match = line.match(/^\|    \|-- ([^->]+)( -> [^\s]+)?/);
      if (match) {
        const dep = match[1].trim();
        dependencies[dep] = true;
      }
    }
    
    return {
      success: true,
      count: Object.keys(dependencies).length,
      dependencies: Object.keys(dependencies),
    };
  } catch (error) {
    return {
      success: false,
      error: error.message,
      count: 0,
      dependencies: [],
    };
  }
}

/**
 * Compter les lignes de code par type de fichier
 * @param {string[]} files
 * @returns {Promise<{kotlin: number, cpp: number, python: number, total: number}>}
 */
async function countLinesByType(files) {
  const counts = {
    kotlin: 0,
    cpp: 0,
    python: 0,
    java: 0,
    total: 0,
  };

  for (const file of files) {
    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      const lines = content.split('\n').length;
      counts.total += lines;

      if (file.endsWith('.kt')) counts.kotlin += lines;
      else if (file.endsWith('.cpp') || file.endsWith('.h') || file.endsWith('.hpp') || file.endsWith('.c')) counts.cpp += lines;
      else if (file.endsWith('.py')) counts.python += lines;
      else if (file.endsWith('.java')) counts.java += lines;
    } catch {
      // Ignorer les fichiers illisibles
    }
  }

  return counts;
}

/**
 * Analyser la qualité du code
 * @param {ProjectStructure} structure
 * @returns {Promise<CodeQualityAnalysis>}
 */
async function analyzeCodeQuality(structure) {
  const lineCounts = await countLinesByType(structure.sourceFiles);
  
  return {
    totalFiles: structure.sourceFiles.length,
    totalLines: lineCounts.total,
    kotlinFiles: structure.sourceFiles.filter(f => f.endsWith('.kt')).length,
    cppFiles: structure.sourceFiles.filter(f => 
      f.endsWith('.cpp') || f.endsWith('.h') || f.endsWith('.hpp') || f.endsWith('.c')
    ).length,
    pythonFiles: structure.sourceFiles.filter(f => f.endsWith('.py')).length,
    javaFiles: structure.sourceFiles.filter(f => f.endsWith('.java')).length,
    testFiles: structure.sourceFiles.filter(f => 
      f.includes('test') || f.includes('Test') || f.includes('Spec')
    ).length,
    ...lineCounts,
  };
}

/**
 * Détecter les problèmes potentiels
 * @param {ProjectStructure} structure
 * @returns {Promise<Issue[]>}
 */
async function detectIssues(structure) {
  const issues = [];
  const config = CONFIG.restrictions;

  // Vérifier les fichiers sensibles
  const sensitiveFiles = [
    'local.properties',
    'debug.keystore',
    'release.keystore',
    'app/release.jks',
  ];

  for (const file of sensitiveFiles) {
    try {
      const stats = await fs.stat(resolve(PROJECT_ROOT, file));
      if (stats) {
        issues.push({
          severity: 'error',
          code: 'SENSITIVE_FILE_COMMITTED',
          message: `Fichier sensible committé: ${file}`,
          file,
          line: 0,
        });
      }
    } catch {
      // Fichier non trouvé, c'est normal
    }
  }

  // Vérifier les secrets dans les fichiers
  const secretPatterns = config.global.find(r => r.id === 'no-secrets-in-code')?.patterns || [];
  
  for (const file of structure.sourceFiles.concat(structure.configFiles)) {
    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      
      for (const pattern of secretPatterns) {
        if (content.includes(pattern)) {
          issues.push({
            severity: 'error',
            code: 'SECRET_DETECTED',
            message: `Motif de secret détecté: ${pattern}`,
            file,
            line: 0,
          });
        }
      }
    } catch {
      // Ignorer
    }
  }

  // Vérifier les textes en dur (simplifié)
  for (const file of structure.sourceFiles.filter(f => f.endsWith('.kt'))) {
    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      const lines = content.split('\n');
      
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        // Détecter les Text("...") sans ressource
        if (line.includes('Text("') && !line.includes('stringResource')) {
          issues.push({
            severity: 'warning',
            code: 'HARDCODED_STRING',
            message: 'Texte en dur détecté, utiliser stringResource pour i18n',
            file,
            line: i + 1,
          });
        }
      }
    } catch {
      // Ignorer
    }
  }

  // Vérifier les TODO/FIXME
  for (const file of structure.sourceFiles) {
    try {
      const content = await fs.readFile(resolve(PROJECT_ROOT, file), 'utf-8');
      const lines = content.split('\n');
      
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.includes('TODO') || line.includes('FIXME')) {
          issues.push({
            severity: 'info',
            code: 'TODO_DETECTED',
            message: 'TODO/FIXME détecté, documenter ou résoudre',
            file,
            line: i + 1,
          });
        }
      }
    } catch {
      // Ignorer
    }
  }

  return issues;
}

/**
 * Générer le rapport d'analyse
 * @param {AnalyzeResult} result
 * @returns {string}
 */
function generateReport(result) {
  const { timestamp, projectName, structure, dependencies, codeQuality, issues, summary } = result;
  
  let report = chalk.bold('\n╔════════════════════════════════════════════════════════════════╗\n');
  report += chalk.bold(`║  LieluPhoto - Rapport d'Analyse Vibe Code                ║\n`);
  report += chalk.bold(`║  ${timestamp}                                       ║\n`);
  report += chalk.bold('╚════════════════════════════════════════════════════════════════╝\n\n');

  // Structure
  report += chalk.bold.cyan('📁 STRUCTURE DU PROJET\n');
  report += chalk.cyan('─'.repeat(60)) + '\n';
  report += `Root: ${structure.root}\n`;
  report += `Répertoires clés: ${structure.keyDirectories.length}\n`;
  report += `  • ${structure.keyDirectories.join('\n  • ')}\n\n`;
  report += `Fichiers source: ${structure.sourceFiles.length}\n`;
  report += `Fichiers config: ${structure.configFiles.length}\n`;
  report += `Fichiers docs: ${structure.docFiles.length}\n\n`;

  // Qualité du code
  report += chalk.bold.cyan('📊 QUALITÉ DU CODE\n');
  report += chalk.cyan('─'.repeat(60)) + '\n';
  report += `Fichiers totaux: ${codeQuality.totalFiles}\n`;
  report += `Lignes totales: ${codeQuality.totalLines}\n`;
  report += `  • Kotlin: ${codeQuality.kotlinFiles} fichiers, ${codeQuality.kotlin} lignes\n`;
  report += `  • C++: ${codeQuality.cppFiles} fichiers, ${codeQuality.cpp} lignes\n`;
  report += `  • Python: ${codeQuality.pythonFiles} fichiers, ${codeQuality.python} lignes\n`;
  report += `  • Java: ${codeQuality.javaFiles} fichiers, ${codeQuality.java} lignes\n`;
  report += `Fichiers de test: ${codeQuality.testFiles}\n\n`;

  // Dépendances
  if (dependencies.gradle.success) {
    report += chalk.bold.cyan('📦 DÉPENDANCES GRADLE\n');
    report += chalk.cyan('─'.repeat(60)) + '\n';
    report += `Total: ${dependencies.gradle.count} dépendances\n`;
    report += `  (Détails: exécuter ./gradlew dependencies)\n\n`;
  } else {
    report += chalk.bold.cyan('📦 DÉPENDANCES GRADLE\n');
    report += chalk.cyan('─'.repeat(60)) + '\n';
    report += chalk.yellow(`⚠️ Impossible de récupérer les dépendances: ${dependencies.gradle.error}\n\n`);
  }

  // Problèmes
  if (issues.length > 0) {
    report += chalk.bold.red('⚠️ PROBLÈMES DÉTECTÉS\n');
    report += chalk.red('─'.repeat(60)) + '\n';
    
    const bySeverity = { error: [], warning: [], info: [] };
    for (const issue of issues) {
      bySeverity[issue.severity].push(issue);
    }
    
    for (const severity of ['error', 'warning', 'info']) {
      if (bySeverity[severity].length > 0) {
        const color = SEVERITY_COLORS[severity];
        const icon = SEVERITY_ICONS[severity];
        
        report += color(`${icon} ${severity.toUpperCase()} (${bySeverity[severity].length})\n`);
        
        for (const issue of bySeverity[severity]) {
          report += color(`  ${icon} [${issue.code}] ${issue.message}\n`);
          report += color(`      → ${issue.file}:${issue.line}\n`);
        }
        
        report += '\n';
      }
    }
  } else {
    report += chalk.bold.green('✅ AUCUN PROBLÈME DÉTECTÉ\n\n');
  }

  // Résumé
  report += chalk.bold.magenta('📝 RÉSUMÉ\n');
  report += chalk.magenta('─'.repeat(60)) + '\n';
  report += `${summary}\n\n`;

  report += chalk.bold('╔════════════════════════════════════════════════════════════════╗\n');
  report += chalk.bold(`║  Fin du rapport - Généré par Vibe Code (Mistral AI)       ║\n`);
  report += chalk.bold('╚════════════════════════════════════════════════════════════════╝\n');

  return report;
}

/**
 * Fonction principale d'analyse
 * @returns {Promise<AnalyzeResult>}
 */
async function analyze() {
  console.log(chalk.cyan('🔍 Analyse du projet LieluPhoto en cours...\n'));

  // Analyser la structure
  console.log(chalk.cyan('  ✓ Analyse de la structure...'));
  const structure = await analyzeStructure();

  // Analyser les dépendances
  console.log(chalk.cyan('  ✓ Analyse des dépendances Gradle...'));
  const gradleDeps = await analyzeGradleDependencies();

  // Analyser la qualité du code
  console.log(chalk.cyan('  ✓ Analyse de la qualité du code...'));
  const codeQuality = await analyzeCodeQuality(structure);

  // Détecter les problèmes
  console.log(chalk.cyan('  ✓ Détection des problèmes...'));
  const issues = await detectIssues(structure);

  // Générer le résumé
  const errorCount = issues.filter(i => i.severity === 'error').length;
  const warningCount = issues.filter(i => i.severity === 'warning').length;
  const infoCount = issues.filter(i => i.severity === 'info').length;

  const summary = `
Projet: ${CONFIG.project.name || 'LieluPhoto'}
Fichiers source: ${codeQuality.totalFiles}
Lignes de code: ${codeQuality.totalLines}
Problèmes: ${errorCount} erreurs, ${warningCount} avertissements, ${infoCount} infos
`.trim();

  const result = {
    timestamp: new Date().toISOString(),
    projectName: CONFIG.project.name || 'LieluPhoto',
    structure,
    dependencies: {
      gradle: gradleDeps,
      npm: { success: false, error: 'No package.json found' },
      python: { success: false, error: 'Not analyzed' },
    },
    codeQuality,
    issues,
    summary,
  };

  return result;
}

/**
 * Point d'entrée
 */
async function main() {
  try {
    // Vérifier que package.json existe pour les dépendances npm
    try {
      await fs.access(resolve(PROJECT_ROOT, 'package.json'));
    } catch {
      // Pas de package.json, c'est normal pour ce projet
    }

    // Exécuter l'analyse
    const result = await analyze();

    // Générer et afficher le rapport
    const report = generateReport(result);
    console.log(report);

    // Retourner le code de sortie approprié
    const hasErrors = result.issues.some(i => i.severity === 'error');
    process.exit(hasErrors ? 1 : 0);
  } catch (error) {
    console.error(chalk.red('❌ Erreur lors de l\'analyse:'), error);
    process.exit(1);
  }
}

// Exporter pour les tests
const __exportForTests = {
  analyzeStructure,
  analyzeGradleDependencies,
  countLinesByType,
  analyzeCodeQuality,
  detectIssues,
  generateReport,
  analyze,
};

export default __exportForTests;

// Exécuter si appelé directement
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main();
}

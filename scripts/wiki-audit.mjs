#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const args = new Set(process.argv.slice(2));
const jsonOutput = args.has("--json");
const changedSourceCheck = args.has("--changed-source-check");

const root = process.cwd();
const wikiRoot = path.join(root, "docs", "wiki");

const REQUIRED_FRONTMATTER_FIELDS = [
  "type",
  "status",
  "project",
  "created",
  "updated",
  "last_verified",
  "confidence",
  "source_files",
];

const ALLOWED_TYPES = new Set([
  "architecture",
  "codebase",
  "decision",
  "bug",
  "problem",
  "integration",
  "api",
  "database",
  "prompt",
  "roadmap",
  "research",
  "status",
  "feature",
  "log",
  "context",
  "maintenance",
]);

const ALLOWED_STATUSES = new Set([
  "active",
  "draft",
  "outdated",
  "archived",
  "superseded",
]);

const ALLOWED_CONFIDENCE = new Set(["high", "medium", "low"]);

const DATE_FIELDS = ["created", "updated", "last_verified"];

const MAX_HOT_CONTEXT_WORDS = 500;
const MAX_PROMPT_PAGE_WORDS = 1200;
const MAX_NORMAL_PAGE_WORDS = 1200;
const MAX_AGENTS_WORDS = 600;
const SECOND_BRAIN_RULES_START = "<!-- SECOND_BRAIN_RULES_START -->";
const SECOND_BRAIN_RULES_END = "<!-- SECOND_BRAIN_RULES_END -->";

const HOT_CONTEXT_REQUIRED_HEADINGS = [
  "## Current Focus",
  "## Must Know",
  "## Recent Important Changes",
  "## Current Risks / Open Questions",
  "## Read Next",
];

const allowedPromptFiles = new Set([
  "Agent_Rules.md",
  "New_Session_Start_Prompt.md",
  "Documentation_Update_Prompt.md",
  "Wiki_Maintenance_Prompt.md",
  "Problem_Resolution_Prompt.md",
  "IDE_Agent_Usage.md",
]);

const results = {
  tool: "wiki-audit",
  errors: [],
  warnings: [],
  health: "Green",
};

function exists(p) {
  return fs.existsSync(p);
}

function walk(dir) {
  if (!exists(dir)) return [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === ".obsidian" || entry.name === ".trash") continue;
      files.push(...walk(full));
    } else if (entry.isFile() && entry.name.endsWith(".md")) {
      files.push(full);
    }
  }

  return files;
}

function read(file) {
  return fs.readFileSync(file, "utf8");
}

function rel(file) {
  return path.relative(root, file).replaceAll("\\", "/");
}

function wikiRel(file) {
  return path.relative(wikiRoot, file).replaceAll("\\", "/");
}

function wordCount(text) {
  return text
    .replace(/^---\r?\n[\s\S]*?\r?\n---/, "")
    .split(/\s+/)
    .filter(Boolean).length;
}

function extractSecondBrainRulesSection(text) {
  const startIndex = text.indexOf(SECOND_BRAIN_RULES_START);
  const endIndex = text.indexOf(SECOND_BRAIN_RULES_END);

  if (startIndex === -1 && endIndex === -1) {
    return { section: null, status: "missing" };
  }

  if (startIndex === -1 || endIndex === -1 || endIndex <= startIndex) {
    return { section: null, status: "invalid" };
  }

  const section = text.slice(startIndex + SECOND_BRAIN_RULES_START.length, endIndex).trim();
  return { section, status: "found" };
}


function stripYamlScalar(value) {
  return value
    .trim()
    .replace(/^["']/, "")
    .replace(/["']$/, "");
}

function parseInlineList(value) {
  const trimmed = value.trim();
  if (trimmed === "[]") return [];
  if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null;
  const body = trimmed.slice(1, -1).trim();
  if (!body) return [];
  return body.split(",").map((item) => stripYamlScalar(item)).filter(Boolean);
}

function parseFrontmatter(text) {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return null;

  const values = {};
  let currentKey = null;

  for (const line of match[1].split(/\r?\n/)) {
    const keyMatch = line.match(/^([A-Za-z0-9_-]+):(?:\s*(.*))?$/);
    if (keyMatch) {
      const key = keyMatch[1];
      const rawValue = keyMatch[2] ?? "";
      const inlineList = parseInlineList(rawValue);
      currentKey = key;
      values[key] = inlineList ?? stripYamlScalar(rawValue);
      continue;
    }

    const listMatch = line.match(/^\s*-\s+(.+)$/);
    if (listMatch && currentKey) {
      if (!Array.isArray(values[currentKey])) {
        values[currentKey] = values[currentKey] ? [values[currentKey]] : [];
      }
      values[currentKey].push(stripYamlScalar(listMatch[1]));
    }
  }

  return {
    keys: new Set(Object.keys(values)),
    values,
  };
}

function asList(value) {
  if (Array.isArray(value)) return value.filter(Boolean);
  if (!value || value === "[]") return [];
  return [value];
}

function scalar(value) {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}

function add(level, file, message) {
  const item = { file: file ? rel(file) : "", message };
  if (level === "ERROR") results.errors.push(item);
  else results.warnings.push(item);
}

function addByRel(level, relativePath, message) {
  const item = { file: relativePath, message };
  if (level === "ERROR") results.errors.push(item);
  else results.warnings.push(item);
}

function finalizeHealth() {
  if (results.errors.length > 0) results.health = "Red";
  else if (results.warnings.length > 0) results.health = "Yellow";
  else results.health = "Green";
}

function printHuman() {
  for (const err of results.errors) {
    console.log(`ERROR: ${err.file} ${err.message}`);
  }
  for (const warn of results.warnings) {
    console.log(`WARN: ${warn.file} ${warn.message}`);
  }
  console.log("");
  console.log(`Wiki audit completed. Errors: ${results.errors.length}, warnings: ${results.warnings.length}`);
  console.log(`Wiki health: ${results.health}`);
}

function validateEnum(file, fm, key, allowed) {
  const value = scalar(fm.values[key]);
  if (value && !allowed.has(value)) {
    add("ERROR", file, `invalid frontmatter ${key}: ${value}`);
  }
}

function validateDate(file, fm, key) {
  const value = scalar(fm.values[key]);
  if (value && !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    add("WARN", file, `frontmatter ${key} should use YYYY-MM-DD. Current: ${value}`);
  }
}

function isInsideRoot(absPath) {
  const relative = path.relative(root, absPath);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

function validateSourceFiles(file, fm, sourceToWikiPages) {
  if (!fm.keys.has("source_files")) return;

  const sourceFiles = asList(fm.values.source_files);
  for (const sourceFile of sourceFiles) {
    if (!sourceFile || sourceFile === "[]") continue;

    if (sourceFile.startsWith("[[")) {
      add("ERROR", file, `source_files must use project-relative paths, not Obsidian links: ${sourceFile}`);
      continue;
    }

    if (/^https?:\/\//i.test(sourceFile)) {
      add("WARN", file, `source_files should point to local source/config/test files, not URLs: ${sourceFile}`);
      continue;
    }

    if (path.isAbsolute(sourceFile)) {
      add("ERROR", file, `source_files must be project-relative, not absolute: ${sourceFile}`);
      continue;
    }

    const absSourcePath = path.resolve(root, sourceFile);
    if (!isInsideRoot(absSourcePath)) {
      add("ERROR", file, `source_files path escapes the project root: ${sourceFile}`);
      continue;
    }

    if (!exists(absSourcePath)) {
      add("ERROR", file, `source_files path does not exist: ${sourceFile}`);
      continue;
    }

    const normalized = sourceFile.replaceAll("\\", "/");
    if (!sourceToWikiPages.has(normalized)) {
      sourceToWikiPages.set(normalized, []);
    }
    sourceToWikiPages.get(normalized).push(rel(file));
  }
}

function getWikiLinkTargets(text) {
  const matches = text.matchAll(/\[\[([^\]]+)\]\]/g);
  return [...matches].map((match) => match[1].split("|")[0].split("#")[0].trim()).filter(Boolean);
}

function buildWikiLookup(files) {
  const exact = new Set();
  const basenames = new Set();

  for (const file of files) {
    const relative = wikiRel(file).replace(/\.md$/, "");
    exact.add(relative);
    basenames.add(path.basename(relative));
  }

  return { exact, basenames };
}

function wikiLinkExists(target, currentFile, lookup) {
  if (!target || target.startsWith("#")) return true;
  if (/^https?:\/\//i.test(target)) return true;

  const normalized = target.replace(/\.md$/, "").replaceAll("\\", "/");
  const currentDir = path.dirname(wikiRel(currentFile)).replaceAll("\\", "/");
  const relativeCandidate = path.posix.normalize(path.posix.join(currentDir, normalized));

  return (
    lookup.exact.has(normalized) ||
    lookup.exact.has(relativeCandidate) ||
    (!normalized.includes("/") && lookup.basenames.has(normalized))
  );
}

function validateWikiLinks(file, text, lookup) {
  const targets = getWikiLinkTargets(text);
  for (const target of targets) {
    if (/\.(ts|tsx|js|jsx|mjs|cjs|py|rb|go|rs|java|kt|php|cs|cpp|c|h|hpp|css|scss|sass|less|html|vue|svelte|json|yml|yaml|toml|env|sql|mdx?)$/i.test(target)) {
      add("ERROR", file, `source file path used as Obsidian link: [[${target}]]`);
      continue;
    }

    if (!wikiLinkExists(target, file, lookup)) {
      add("ERROR", file, `broken wiki link: [[${target}]]`);
    }
  }
}

function validateHotContext(file, text) {
  for (const heading of HOT_CONTEXT_REQUIRED_HEADINGS) {
    if (!text.includes(heading)) {
      add("ERROR", file, `Hot_Context missing required heading: ${heading}`);
    }
  }
}

function validateIndex(file, text) {
  if (!text.includes("## Task Routing")) {
    add("ERROR", file, "Index.md is missing ## Task Routing.");
  }
  if (!text.includes("| Task Type |") || !text.includes("| Read First |")) {
    add("ERROR", file, "Index.md task routing table is missing required columns.");
  }
}

function getChangedFilesFromGit() {
  try {
    const output = execFileSync("git", ["diff", "--name-only", "HEAD"], {
      cwd: root,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    });
    return output.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  } catch {
    return null;
  }
}

function validateChangedSourceFiles(sourceToWikiPages) {
  if (!changedSourceCheck) return;

  const changedFiles = getChangedFilesFromGit();
  if (!changedFiles) {
    addByRel("WARN", "", "--changed-source-check requested, but git changed files could not be read.");
    return;
  }

  for (const changedFile of changedFiles) {
    const normalized = changedFile.replaceAll("\\", "/");
    const relatedWikiPages = sourceToWikiPages.get(normalized);
    if (relatedWikiPages?.length) {
      addByRel(
        "WARN",
        normalized,
        `changed source file is referenced by wiki pages; review durable memory if behavior changed: ${relatedWikiPages.join(", ")}`
      );
    }
  }
}

if (!exists(wikiRoot)) {
  results.errors.push({ file: "docs/wiki", message: "docs/wiki does not exist." });
  finalizeHealth();
  if (jsonOutput) console.log(JSON.stringify(results, null, 2));
  else printHuman();
  process.exit(1);
}

const files = walk(wikiRoot);
const wikiLookup = buildWikiLookup(files);
const sourceToWikiPages = new Map();

const requiredFiles = [
  path.join(wikiRoot, "Index.md"),
  path.join(wikiRoot, "Hot_Context.md"),
  path.join(wikiRoot, "Log.md"),
  path.join(wikiRoot, "08_Prompts", "Agent_Rules.md"),
];

for (const file of requiredFiles) {
  if (!exists(file)) {
    add("ERROR", file, "required file is missing.");
  }
}

for (const file of files) {
  const text = read(file);
  const fm = parseFrontmatter(text);

  const relative = rel(file);
  const isArchive = relative.includes("/archive/");
  const isTemplate = relative.includes("/10_Templates/");

  if (!fm && !isArchive && !isTemplate) {
    add("ERROR", file, "missing frontmatter.");
  }

  if (fm) {
    for (const field of REQUIRED_FRONTMATTER_FIELDS) {
      if (!fm.keys.has(field)) {
        add("WARN", file, `missing frontmatter field: ${field}`);
      }
    }

    validateEnum(file, fm, "type", ALLOWED_TYPES);
    validateEnum(file, fm, "status", ALLOWED_STATUSES);
    validateEnum(file, fm, "confidence", ALLOWED_CONFIDENCE);

    for (const field of DATE_FIELDS) {
      validateDate(file, fm, field);
    }

    validateSourceFiles(file, fm, sourceToWikiPages);
  }

  validateWikiLinks(file, text, wikiLookup);

  const wc = wordCount(text);

  if (relative === "docs/wiki/Hot_Context.md") {
    validateHotContext(file, text);
    if (wc > MAX_HOT_CONTEXT_WORDS) {
      add("ERROR", file, `Hot_Context exceeds ${MAX_HOT_CONTEXT_WORDS} words. Current: ${wc}`);
    }
  }

  if (relative === "docs/wiki/Index.md") {
    validateIndex(file, text);
  }

  if (relative.includes("docs/wiki/08_Prompts/") && wc > MAX_PROMPT_PAGE_WORDS) {
    add("WARN", file, `prompt page exceeds ${MAX_PROMPT_PAGE_WORDS} words. Current: ${wc}`);
  }

  if (!relative.includes("docs/wiki/08_Prompts/") && !isArchive && wc > MAX_NORMAL_PAGE_WORDS) {
    add("WARN", file, `page exceeds ${MAX_NORMAL_PAGE_WORDS} words. Current: ${wc}`);
  }
}

const promptsDir = path.join(wikiRoot, "08_Prompts");
if (exists(promptsDir)) {
  const promptFiles = fs.readdirSync(promptsDir).filter((name) => name.endsWith(".md"));
  const nonDefault = promptFiles.filter((name) => !allowedPromptFiles.has(name));

  if (nonDefault.length > 0) {
    add("WARN", promptsDir, `non-default 08_Prompts files found: ${nonDefault.join(", ")}`);
  }
}

const agentsFile = path.join(root, "AGENTS.md");
if (exists(agentsFile)) {
  const agentsText = read(agentsFile);
  const { section: secondBrainRulesSection, status: secondBrainRulesStatus } = extractSecondBrainRulesSection(agentsText);

  if (secondBrainRulesStatus === "found") {
    const wc = wordCount(secondBrainRulesSection);
    if (wc > MAX_AGENTS_WORDS) {
      add("WARN", agentsFile, `AGENTS.md Second Brain rules section is long (${wc} words). Keep the marker section short and move detailed procedures to docs/wiki/08_Prompts.`);
    }
  } else if (secondBrainRulesStatus === "invalid") {
    add("WARN", agentsFile, "SECOND_BRAIN_RULES markers are incomplete or incorrectly ordered. AGENTS.md length check may include non-wiki rules.");
    const wc = wordCount(agentsText);
    if (wc > MAX_AGENTS_WORDS) {
      add("WARN", agentsFile, `AGENTS.md is long (${wc} words), but marker section could not be measured.`);
    }
  } else {
    add("WARN", agentsFile, "SECOND_BRAIN_RULES markers not found. AGENTS.md length check may include non-wiki rules.");
    const wc = wordCount(agentsText);
    if (wc > MAX_AGENTS_WORDS) {
      add("WARN", agentsFile, `AGENTS.md is long (${wc} words). Add SECOND_BRAIN_RULES markers around the wiki-memory section for a more accurate audit.`);
    }
  }
} else {
  add("ERROR", agentsFile, "AGENTS.md is missing.");
}

validateChangedSourceFiles(sourceToWikiPages);

finalizeHealth();

if (jsonOutput) {
  console.log(JSON.stringify(results, null, 2));
} else {
  printHuman();
}

if (results.errors.length > 0) {
  process.exit(1);
}

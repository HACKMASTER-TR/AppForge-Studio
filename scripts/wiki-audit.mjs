#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const root = process.cwd();
const wiki = path.join(root, "docs", "wiki");
const json = process.argv.includes("--json");
const changed = process.argv.includes("--changed-source-check");
const errors = [];
const warnings = [];
const required = ["Index.md", "Hot_Context.md", "Log.md", "08_Prompts/Agent_Rules.md", "00_Governance/Coverage_Registry.md"];
const types = new Set(["architecture", "codebase", "decision", "bug", "problem", "integration", "api", "database", "prompt", "roadmap", "research", "status", "feature", "log", "context", "maintenance", "registry"]);
const statuses = new Set(["active", "draft", "outdated", "archived", "superseded"]);
const confidence = new Set(["high", "medium", "low"]);
const date = /^\d{4}-\d{2}-\d{2}$/;
const promptFiles = new Set(["Agent_Rules.md", "New_Session_Start_Prompt.md", "Documentation_Update_Prompt.md", "Wiki_Maintenance_Prompt.md", "Problem_Resolution_Prompt.md", "IDE_Agent_Usage.md"]);
const rulesStart = "<!-- SECOND_BRAIN_RULES_START -->";
const rulesEnd = "<!-- SECOND_BRAIN_RULES_END -->";
const normalize = value => value.replaceAll("\\", "/");
const rel = file => normalize(path.relative(root, file));
const add = (level, file, message) => (level === "error" ? errors : warnings).push({ file, message });
const isInsideRoot = candidate => {
  const relative = path.relative(root, candidate);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
};
const walk = dir => fs.existsSync(dir) ? fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
  const file = path.join(dir, entry.name);
  if (entry.isDirectory()) return [".obsidian", ".trash"].includes(entry.name) ? [] : walk(file);
  return entry.isFile() && entry.name.endsWith(".md") ? [file] : [];
}) : [];
const parse = text => {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return null;
  const values = {};
  let key = null;
  for (const line of match[1].split(/\r?\n/)) {
    const scalar = line.match(/^([\w-]+):\s*(.*)$/);
    if (scalar) { key = scalar[1]; values[key] = scalar[2].trim().replace(/^['"]|['"]$/g, ""); continue; }
    const item = line.match(/^\s+-\s+(.+)$/);
    if (item && key) values[key] = [...(Array.isArray(values[key]) ? values[key] : values[key] ? [values[key]] : []), item[1].trim().replace(/^['"]|['"]$/g, "")];
  }
  return values;
};
const asList = value => Array.isArray(value) ? value : !value || value === "[]" ? [] : [value];

if (!fs.existsSync(wiki)) add("error", "docs/wiki", "directory is missing");
for (const name of required) if (!fs.existsSync(path.join(wiki, name))) add("error", `docs/wiki/${name}`, "required file is missing");
const files = walk(wiki);
const names = new Set(files.map(file => path.basename(file, ".md")));
const wikiPaths = new Set(files.map(file => normalize(path.relative(wiki, file)).replace(/\.md$/, "")));
const referenced = new Map();

for (const file of files) {
  const text = fs.readFileSync(file, "utf8");
  const fm = parse(text);
  const fileRel = rel(file);
  if (!fm) { add("error", fileRel, "missing frontmatter"); continue; }
  for (const field of ["type", "status", "project", "created", "updated", "last_verified", "confidence", "source_files"]) if (!(field in fm)) add("warning", fileRel, `missing frontmatter field: ${field}`);
  if (fm.type && !types.has(fm.type)) add("error", fileRel, `invalid type: ${fm.type}`);
  if (fm.status && !statuses.has(fm.status)) add("error", fileRel, `invalid status: ${fm.status}`);
  if (fm.confidence && !confidence.has(fm.confidence)) add("error", fileRel, `invalid confidence: ${fm.confidence}`);
  for (const key of ["created", "updated", "last_verified"]) if (fm[key] && !date.test(fm[key])) add("warning", fileRel, `invalid date: ${key}`);
  for (const source of asList(fm.source_files)) {
    if (source.startsWith("[[") || path.isAbsolute(source)) { add("error", fileRel, `invalid source_files path: ${source}`); continue; }
    const sourcePath = path.resolve(root, source);
    if (!isInsideRoot(sourcePath)) { add("error", fileRel, `source_files path escapes the project root: ${source}`); continue; }
    if (!source || source === "[]") continue;
    if (!fs.existsSync(sourcePath)) add("error", fileRel, `source file does not exist: ${source}`);
    else referenced.set(normalize(source), [...(referenced.get(normalize(source)) ?? []), fileRel]);
  }
  for (const match of text.matchAll(/\[\[([^\]|#]+)[^\]]*\]\]/g)) {
    const target = normalize(match[1].trim().replace(/\.md$/, ""));
    if (/\.(?:js|mjs|py|kt|java|sql|json|ya?ml|html)$/i.test(target)) add("error", fileRel, `source path used as wiki link: [[${target}]]`);
    else if (!wikiPaths.has(target) && !names.has(path.basename(target))) add("error", fileRel, `broken wiki link: [[${target}]]`);
  }
  const words = text.replace(/^---[\s\S]*?---/, "").trim().split(/\s+/).filter(Boolean).length;
  if (fileRel.endsWith("Hot_Context.md") && words > 500) add("error", fileRel, "Hot Context exceeds 500 words");
  if (words > 1200 && !fileRel.includes("/archive/")) add("warning", fileRel, "page exceeds 1200 words");
}
const promptsDir = path.join(wiki, "08_Prompts");
if (fs.existsSync(promptsDir)) {
  const extras = fs.readdirSync(promptsDir).filter(name => name.endsWith(".md") && !promptFiles.has(name));
  if (extras.length) add("warning", "docs/wiki/08_Prompts", `non-procedure prompt candidates require review: ${extras.join(", ")}`);
}
const hot = path.join(wiki, "Hot_Context.md");
if (fs.existsSync(hot)) for (const heading of ["## Current Focus", "## Must Know", "## Recent Important Changes", "## Current Risks / Open Questions", "## Read Next"]) if (!fs.readFileSync(hot, "utf8").includes(heading)) add("error", rel(hot), `missing heading: ${heading}`);
const index = path.join(wiki, "Index.md");
if (fs.existsSync(index)) { const text = fs.readFileSync(index, "utf8"); if (!text.includes("## Task Routing") || !text.includes("| Task Type |")) add("error", rel(index), "task routing table is missing"); }
const agents = path.join(root, "AGENTS.md");
if (!fs.existsSync(agents)) add("error", "AGENTS.md", "missing"); else {
  const text = fs.readFileSync(agents, "utf8");
  const start = text.indexOf(rulesStart), end = text.indexOf(rulesEnd);
  if (start < 0 || end < 0 || end <= start) add("warning", "AGENTS.md", "wiki rule markers are missing or invalid");
  else {
    const words = text.slice(start + rulesStart.length, end).trim().split(/\s+/).filter(Boolean).length;
    if (words > 600) add("warning", "AGENTS.md", `wiki rule section exceeds 600 words (${words})`);
  }
}

const coverage = [];
const registry = path.join(wiki, "00_Governance", "Coverage_Registry.md");
if (fs.existsSync(registry)) {
  const lines = fs.readFileSync(registry, "utf8").split(/\r?\n/);
  const start = lines.findIndex(line => line.trim() === "<!-- COVERAGE_REGISTRY_START -->");
  const end = lines.findIndex(line => line.trim() === "<!-- COVERAGE_REGISTRY_END -->");
  if (start < 0 || end <= start) add("error", rel(registry), "coverage registry markers are missing or invalid");
  else for (const raw of lines.slice(start + 1, end)) {
    const line = raw.trim();
    if (!line || line.startsWith("|---") || line.startsWith("| Surface |")) continue;
    const cells = line.split("|").slice(1, -1).map(cell => cell.trim());
    if (cells.length !== 5) { add("error", rel(registry), `invalid coverage row: ${line}`); continue; }
    const [surface, roots, pages, status, followUp] = cells;
    if (!surface || !new Set(["covered", "deferred", "unverified"]).has(status)) { add("error", rel(registry), `invalid coverage status for ${surface || "unnamed surface"}`); continue; }
    const rootList = roots.split(";").map(value => value.trim()).filter(Boolean);
    const pageList = [...pages.matchAll(/\[\[([^\]|#]+)[^\]]*\]\]/g)].map(match => match[1].trim());
    if (!rootList.length || !pageList.length || !followUp) { add("error", rel(registry), `coverage row is incomplete for ${surface}`); continue; }
    for (const rootPath of rootList) {
      const absolute = path.resolve(root, rootPath);
      if (path.isAbsolute(rootPath) || !isInsideRoot(absolute) || !fs.existsSync(absolute)) add("error", rel(registry), `invalid coverage path for ${surface}: ${rootPath}`);
    }
    for (const page of pageList) if (!wikiPaths.has(page) && !names.has(path.basename(page))) add("error", rel(registry), `coverage page does not exist for ${surface}: [[${page}]]`);
    coverage.push({ surface, roots: rootList.map(normalize), pages: pageList, status });
  }
}
if (changed) try {
  const sources = execFileSync("git", ["diff", "--name-only", "HEAD"], { cwd: root, encoding: "utf8" }).split(/\r?\n/).filter(Boolean).map(normalize);
  const sourcePrefixes = ["android-app/", "build-service/", ".github/", ".appforge/", "scripts/", "README.md"];
  for (const source of sources) {
    if (referenced.has(source)) add("warning", source, `referenced by wiki pages: ${referenced.get(source).join(", ")}`);
    if (!sourcePrefixes.some(prefix => source === prefix || source.startsWith(prefix))) continue;
    const matches = coverage.filter(item => item.roots.some(rootPath => source === rootPath.replace(/\/$/, "") || source.startsWith(rootPath.replace(/\/$/, "") + "/")));
    if (!matches.length) add("warning", source, "changed source is not covered by Coverage Registry");
    else add("warning", source, `coverage review required: ${matches.map(item => `${item.surface} -> ${item.pages.join(", ")}`).join("; ")}`);
  }
} catch { add("warning", "", "could not read git changes"); }
const result = { tool: "wiki-audit", errors, warnings, health: errors.length ? "Red" : warnings.length ? "Yellow" : "Green" };
if (json) console.log(JSON.stringify(result, null, 2)); else { for (const item of errors) console.log(`ERROR: ${item.file} ${item.message}`); for (const item of warnings) console.log(`WARN: ${item.file} ${item.message}`); console.log(`Wiki health: ${result.health} (errors: ${errors.length}, warnings: ${warnings.length})`); }
process.exitCode = errors.length ? 1 : 0;

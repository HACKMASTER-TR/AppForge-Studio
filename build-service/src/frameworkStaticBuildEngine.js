import AdmZip from "adm-zip";
import { promises as fs } from "fs";
import path from "path";

const MAX_ZIP_ENTRIES = 12000;
const MAX_UNCOMPRESSED_BYTES = 250 * 1024 * 1024;

function safeInside(root, candidate) {
  const a = path.resolve(root);
  const b = path.resolve(candidate);
  return b === a || b.startsWith(a + path.sep);
}

function ignored(segments) {
  const names = new Set([
    ".git", ".idea", ".next", ".nuxt", ".output",
    "node_modules", "dist", "build", "out"
  ]);
  return segments.some(x => names.has(x));
}

async function extractZip(zipPath, destination) {
  const zip = new AdmZip(zipPath);
  const entries = zip.getEntries();

  if (entries.length > MAX_ZIP_ENTRIES) {
    throw new Error("Next.js / Nuxt projesinde çok fazla ZIP girdisi var.");
  }

  await fs.rm(destination, { recursive: true, force: true });
  await fs.mkdir(destination, { recursive: true });

  let total = 0;

  for (const entry of entries) {
    const raw = String(entry.entryName || "").replaceAll("\\", "/");

    if (!raw || raw.startsWith("/") || raw.includes("\0")) {
      throw new Error("Next.js / Nuxt ZIP yolu güvenli değil.");
    }

    const normalized = path.posix.normalize(raw);

    if (normalized === ".." || normalized.startsWith("../")) {
      throw new Error("Next.js / Nuxt ZIP dizin dışına çıkmaya çalışıyor.");
    }

    const segments = normalized.split("/").filter(Boolean);

    if (!segments.length || ignored(segments)) {
      continue;
    }

    const target = path.join(destination, ...segments);

    if (!safeInside(destination, target)) {
      throw new Error("Next.js / Nuxt ZIP hedef yolu güvenli değil.");
    }

    if (entry.isDirectory) {
      await fs.mkdir(target, { recursive: true });
      continue;
    }

    const data = entry.getData();
    total += data.length;

    if (total > MAX_UNCOMPRESSED_BYTES) {
      throw new Error("Next.js / Nuxt ZIP açıldığında boyut sınırını aşıyor.");
    }

    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.writeFile(target, data);
  }

  return { entries: entries.length, bytes: total };
}

async function walk(root, depth = 0, result = []) {
  if (depth > 6 || result.length >= 6000) return result;

  let entries;
  try {
    entries = await fs.readdir(root, { withFileTypes: true });
  } catch {
    return result;
  }

  for (const entry of entries) {
    if (result.length >= 6000) break;
    if (ignored([entry.name])) continue;

    const full = path.join(root, entry.name);

    if (entry.isDirectory()) {
      await walk(full, depth + 1, result);
    } else if (entry.isFile()) {
      result.push(full);
    }
  }

  return result;
}

async function findPackageRoot(root) {
  const files = await walk(root);

  const candidates = files
    .filter(file => path.basename(file).toLowerCase() === "package.json")
    .sort((a, b) => a.split(path.sep).length - b.split(path.sep).length);

  if (!candidates.length) {
    throw new Error("Next.js / Nuxt projesinde package.json bulunamadı.");
  }

  return path.dirname(candidates[0]);
}

async function readJson(file) {
  try {
    return JSON.parse(await fs.readFile(file, "utf8"));
  } catch {
    throw new Error("package.json geçerli JSON değil.");
  }
}

async function firstText(root, names) {
  for (const name of names) {
    const file = path.join(root, name);

    try {
      const stat = await fs.stat(file);

      if (stat.isFile() && stat.size <= 2 * 1024 * 1024) {
        return { file, text: await fs.readFile(file, "utf8") };
      }
    } catch {}
  }

  return { file: null, text: "" };
}

function deps(packageJson) {
  return {
    ...(packageJson?.dependencies || {}),
    ...(packageJson?.devDependencies || {})
  };
}

function nextStaticReady(packageJson, configText) {
  if (packageJson?.appforge?.staticExport === true) {
    return true;
  }

  const compact = String(configText || "").replace(/\s+/g, "");

  return (
    compact.includes('output:"export"') ||
    compact.includes("output:'export'")
  );
}

function nuxtGenerateScript(packageJson) {
  const scripts = packageJson?.scripts || {};

  for (const [name, command] of Object.entries(scripts)) {
    const text = String(command || "").toLowerCase().replace(/\s+/g, " ").trim();

    if (
      (name === "generate" || name === "build:static") &&
      (text.includes("nuxt generate") || text.includes("nuxi generate"))
    ) {
      return name;
    }
  }

  return null;
}

export async function prepareFrameworkStaticSource({
  projectZip,
  workDir,
  technology = null,
  onLog = null,
  cancelled = null
}) {
  if (!projectZip) {
    throw new Error("Next.js / Nuxt kaynak ZIP'i eksik.");
  }

  if (cancelled) await cancelled();

  const source = path.join(workDir, "source");
  const extracted = await extractZip(projectZip, source);
  const projectRoot = await findPackageRoot(source);
  const packageFile = path.join(projectRoot, "package.json");
  const packageJson = await readJson(packageFile);
  const dependencies = deps(packageJson);

  const isNext = Boolean(dependencies.next);
  const isNuxt = Boolean(dependencies.nuxt);

  if (!isNext && !isNuxt) {
    throw new Error("Proje Next.js veya Nuxt olarak doğrulanamadı.");
  }

  const nextConfig = await firstText(projectRoot, [
    "next.config.js", "next.config.mjs", "next.config.cjs", "next.config.ts"
  ]);

  const nuxtConfig = await firstText(projectRoot, [
    "nuxt.config.ts", "nuxt.config.js", "nuxt.config.mjs"
  ]);

  const framework = isNext ? "nextjs" : "nuxt";
  const generateScript = isNuxt ? nuxtGenerateScript(packageJson) : null;
  const staticReady = isNext
    ? nextStaticReady(packageJson, nextConfig.text)
    : Boolean(generateScript);

  const command = isNext
    ? (packageJson?.scripts?.build ? "npm run build" : null)
    : (generateScript ? `npm run ${generateScript}` : null);

  const outputDir = isNext ? "out" : ".output/public";

  const reason = staticReady
    ? (
        isNext
          ? "Next.js static export yapılandırması bulundu."
          : "Nuxt static generate scripti bulundu."
      )
    : (
        isNext
          ? "Next.js SSR olabilir; output: 'export' veya appforge.staticExport=true gerekli."
          : "Nuxt SSR olabilir; nuxt/nuxi generate scripti gerekli."
      );

  if (onLog) {
    await onLog(
      `🌐 ${framework} static export foundation • ${staticReady ? "hazır" : "kapalı"}`
    );
  }

  return {
    technology: technology || framework,
    framework,
    projectRoot,
    packageFile,
    packageJson,
    staticReady,
    command,
    outputDir,
    reason,
    nextConfigFile: nextConfig.file,
    nuxtConfigFile: nuxtConfig.file,
    generateScript,
    extractedEntries: extracted.entries,
    extractedBytes: extracted.bytes
  };
}

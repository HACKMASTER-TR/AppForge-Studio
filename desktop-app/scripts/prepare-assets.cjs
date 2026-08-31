const fs = require("fs");
const path = require("path");

const desktopRoot = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(desktopRoot, "..");
const destination = path.join(desktopRoot, "site");
const bundledMonaco = path.join(desktopRoot, "node_modules", "monaco-editor", "min", "vs");
const serviceMonaco = path.join(repositoryRoot, "build-service", "node_modules", "monaco-editor", "min", "vs");
const bundledFflate = path.join(desktopRoot, "node_modules", "fflate", "umd");

const sources = [
  [path.join(repositoryRoot, "build-service", "public", "studio"), path.join(destination, "studio")],
  [fs.existsSync(bundledMonaco) ? bundledMonaco : serviceMonaco, path.join(destination, "vendor", "monaco")],
  [bundledFflate, path.join(destination, "vendor", "fflate")]
];

fs.rmSync(destination, { recursive: true, force: true });
for (const [source, target] of sources) {
  if (!fs.existsSync(source)) throw new Error(`Eksik masaüstü varlığı: ${source}`);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.cpSync(source, target, { recursive: true });
}

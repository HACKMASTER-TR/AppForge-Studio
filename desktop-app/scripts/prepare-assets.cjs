const fs = require("fs");
const path = require("path");

const desktopRoot = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(desktopRoot, "..");
const destination = path.join(desktopRoot, "site");
const sources = [
  [path.join(repositoryRoot, "build-service", "public", "studio"), path.join(destination, "studio")],
  [path.join(repositoryRoot, "build-service", "node_modules", "monaco-editor", "min", "vs"), path.join(destination, "vendor", "monaco")]
];

fs.rmSync(destination, { recursive: true, force: true });
for (const [source, target] of sources) {
  if (!fs.existsSync(source)) throw new Error(`Eksik masaüstü varlığı: ${source}`);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.cpSync(source, target, { recursive: true });
}

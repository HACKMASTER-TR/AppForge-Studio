import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareFrameworkStaticSource
} from "../src/frameworkStaticBuildEngine.js";

async function makeZip(root, files) {
  const project = path.join(root, "project");

  for (const [relative, content] of Object.entries(files)) {
    const target = path.join(project, relative);
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.writeFile(target, content);
  }

  const zipPath = path.join(root, "project.zip");
  const zip = new AdmZip();
  zip.addLocalFolder(project);
  zip.writeZip(zipPath);
  return zipPath;
}

test("Next.js static export project is ready", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "appforge-next-"));

  try {
    const zipPath = await makeZip(root, {
      "package.json": JSON.stringify({
        dependencies: { next: "^16.0.0", react: "^19.0.0" },
        scripts: { build: "next build" }
      }),
      "next.config.mjs": 'export default { output: "export" };\n',
      "app/page.tsx": "export default function Page(){return <main>OK</main>}\n"
    });

    const prepared = await prepareFrameworkStaticSource({
      projectZip: zipPath,
      workDir: path.join(root, "work")
    });

    assert.equal(prepared.framework, "nextjs");
    assert.equal(prepared.staticReady, true);
    assert.equal(prepared.command, "npm run build");
    assert.equal(prepared.outputDir, "out");
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("Nuxt generate project is ready", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "appforge-nuxt-"));

  try {
    const zipPath = await makeZip(root, {
      "package.json": JSON.stringify({
        dependencies: { nuxt: "^4.0.0", vue: "^3.0.0" },
        scripts: { generate: "nuxt generate" }
      }),
      "nuxt.config.ts": "export default defineNuxtConfig({});\n",
      "app.vue": "<template><main>OK</main></template>\n"
    });

    const prepared = await prepareFrameworkStaticSource({
      projectZip: zipPath,
      workDir: path.join(root, "work")
    });

    assert.equal(prepared.framework, "nuxt");
    assert.equal(prepared.staticReady, true);
    assert.equal(prepared.generateScript, "generate");
    assert.equal(prepared.outputDir, ".output/public");
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

test("SSR-only Next.js stays blocked", async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "appforge-next-ssr-"));

  try {
    const zipPath = await makeZip(root, {
      "package.json": JSON.stringify({
        dependencies: { next: "^16.0.0" },
        scripts: { build: "next build" }
      }),
      "next.config.mjs": "export default {};\n"
    });

    const prepared = await prepareFrameworkStaticSource({
      projectZip: zipPath,
      workDir: path.join(root, "work")
    });

    assert.equal(prepared.staticReady, false);
    assert.match(prepared.reason, /SSR/);
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});

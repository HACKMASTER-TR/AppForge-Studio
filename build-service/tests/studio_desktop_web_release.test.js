import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../../", import.meta.url);
const read = path => readFile(new URL(path, root), "utf8");

test("Studio web release bundles V4, Monaco, ZIP runtime and trusted API CORS", async () => {
  const [studio, v4, server, config, workflow] = await Promise.all([
    read("build-service/public/studio/index.html"),
    read("build-service/public/studio/v4.js"),
    read("build-service/server.js"),
    read("build-service/src/config.js"),
    read(".github/workflows/web-studio-pages.yml")
  ]);

  assert.ok(studio.includes("APPFORGE_MONACO_ROOT"));
  assert.ok(studio.includes("APPFORGE_FFLATE_URL"));
  assert.ok(v4.includes("appforge-studio-production.up.railway.app"));
  assert.ok(!studio.includes("document.write("));
  assert.ok(!v4.includes("document.write("));
  assert.ok(v4.includes('document.createElement("script")'));
  assert.ok(v4.includes("loadMonacoLoader()"));
  assert.ok(v4.includes("standaloneWeb || desktopLoopback"));
  assert.ok(v4.includes(".then(()=>initMonaco())"));
  assert.ok(server.includes("isAllowedWebStudioOrigin"));
  assert.ok(server.includes("127\\.0\\.0\\.1"));
  assert.ok(config.includes("WEB_STUDIO_ALLOWED_ORIGINS"));
  assert.ok(workflow.includes("actions/deploy-pages@v4"));
  assert.ok(workflow.includes("monaco-editor@0.52.2"));
  assert.ok(workflow.includes("fflate@0.8.2"));
  assert.ok(workflow.includes("cp -R build-service/public/studio/. _site/"));
});

test("Windows Studio release keeps installer security, encrypted session and V4 desktop services", async () => {
  const [packageJson, main, preload, v4, workflow, prepareAssets] = await Promise.all([
    read("desktop-app/package.json"),
    read("desktop-app/main.cjs"),
    read("desktop-app/preload.cjs"),
    read("build-service/public/studio/v4.js"),
    read(".github/workflows/windows-studio-release.yml"),
    read("desktop-app/scripts/prepare-assets.cjs")
  ]);

  assert.match(packageJson, /"version": "4\.0\.0"/);
  assert.match(packageJson, /"target": "nsis"/);
  assert.match(packageJson, /"target": "portable"/);
  assert.match(packageJson, /"electron": "43\.4\.1"/);
  assert.match(packageJson, /"monaco-editor": "0\.52\.2"/);
  assert.match(packageJson, /"fflate": "0\.8\.2"/);
  assert.ok(packageJson.includes("AppForgeStudio-Setup-${version}.exe"));
  assert.ok(packageJson.includes('"createDesktopShortcut": true'));
  assert.ok(packageJson.includes('"createStartMenuShortcut": true'));

  assert.ok(main.includes("safeStorage"));
  assert.ok(main.includes("auth-token.bin"));
  assert.ok(main.includes("desktop-state.bin"));
  assert.ok(main.includes('ipcMain.handle("appforge:auth:get"'));
  assert.ok(main.includes('ipcMain.handle("appforge:auth:set"'));
  assert.ok(main.includes('ipcMain.handle("appforge:store:get"'));
  assert.ok(main.includes('ipcMain.handle("appforge:keystore:save"'));
  assert.ok(main.includes('ipcMain.handle("appforge:local-ai:chat"'));
  assert.ok(main.includes("http://127.0.0.1:11434/api/chat"));
  assert.ok(main.includes("app.setAppUserModelId(APP_USER_MODEL_ID)"));
  assert.ok(main.includes('localServer.listen(0, "127.0.0.1"'));
  assert.ok(main.includes("contextIsolation: true"));
  assert.ok(main.includes("nodeIntegration: false"));
  assert.ok(main.includes("sandbox: true"));

  assert.ok(preload.includes("ipcRenderer"));
  assert.ok(preload.includes("appforge:auth:get"));
  assert.ok(preload.includes("appforge:store:get"));
  assert.ok(preload.includes("appforge:keystore:save"));
  assert.ok(preload.includes("appforge:local-ai:chat"));
  assert.ok(preload.includes("APPFORGE_API_BASE_URL"));

  assert.ok(v4.includes("desktopAuth"));
  assert.ok(v4.includes("persistAuthToken"));
  assert.ok(v4.includes("restorePersistedToken"));
  assert.ok(v4.includes("resumeSession()"));
  assert.ok(!v4.includes('let token=localStorage.getItem("afs_jwt")||"";'));

  assert.ok(prepareAssets.includes('"fflate"'));
  assert.ok(workflow.includes("windows-latest"));
  assert.ok(workflow.includes("npm ci"));
  assert.ok(workflow.includes("AppForgeStudio-Setup-latest.exe"));
  assert.ok(workflow.includes("AppForgeStudio-Portable-latest.exe"));
  assert.ok(workflow.includes("AppForgeStudio-latest.exe"));
});

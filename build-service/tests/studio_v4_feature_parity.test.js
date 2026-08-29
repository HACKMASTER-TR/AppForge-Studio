import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../../", import.meta.url);
const read = path => readFile(new URL(path, root), "utf8");

test("V4 shared Windows/Web UI avoids responsive sticky overlap and narrow overflow", async () => {
  const [html, css] = await Promise.all([
    read("build-service/public/studio/index.html"),
    read("build-service/public/studio/v4.css")
  ]);

  assert.ok(css.includes(".nav{position:relative;top:auto;height:auto;flex-direction:row;overflow:auto"));
  assert.ok(css.includes(".step-header{position:static;top:auto}"));
  assert.match(css, /\.preview-toolbar\{[^}]*flex-wrap:wrap/);
  assert.match(css, /\.segmented\{[^}]*max-width:100%;overflow-x:auto/);
  assert.ok(css.includes(".topbar{min-height:64px;height:auto;"));
  assert.ok(css.includes("--control-border:"));
  assert.ok(css.includes("background:var(--secondary)"));
  assert.ok(css.includes("background:var(--chip)"));
  assert.ok(html.includes('<label class="span-2">Sunucu URL'));
  assert.ok(html.includes('id="authMsg" class="muted" role="status" aria-live="polite"'));
  assert.ok(html.includes('id="autosaveStatus" class="status muted" role="status" aria-live="polite"'));
  assert.ok(html.includes('aria-label="Ana menü"'));
});

test("V4 navigation animates the next panel while respecting reduced motion", async () => {
  const [script, css] = await Promise.all([
    read("build-service/public/studio/v4.js"),
    read("build-service/public/studio/v4.css")
  ]);

  assert.match(script, /classList\.add\("panel-enter"\)/);
  assert.match(css, /@keyframes panel-enter/);
  assert.match(css, /prefers-reduced-motion:reduce/);
});

test("Windows login remains usable when secure token persistence is unavailable", async () => {
  const v4 = await read("build-service/public/studio/v4.js");

  assert.ok(v4.includes("Desktop secure token persistence failed"));
  assert.ok(v4.includes("Keep the token only in renderer memory."));
  assert.ok(v4.includes('setText("autosaveStatus","Oturum geçici")'));
  assert.ok(v4.includes("const persisted=j?.token ? await persistAuthToken(j.token) : true;"));
  assert.ok(!v4.includes('sessionStorage.setItem("afs_jwt"'));
});

test("Windows security UI uses real Electron safeStorage availability", async () => {
  const [main, preload, v4] = await Promise.all([
    read("desktop-app/main.cjs"),
    read("desktop-app/preload.cjs"),
    read("build-service/public/studio/v4.js")
  ]);

  assert.ok(main.includes('ipcMain.handle("appforge:security:state"'));
  assert.ok(main.includes("safeStorage.isEncryptionAvailable()"));
  assert.ok(preload.includes('getState: () => ipcRenderer.invoke("appforge:security:state")'));
  assert.ok(!preload.includes("safeStorage: true"));
  assert.ok(v4.includes("desktop?.security?.getState"));
  assert.ok(v4.includes("await desktop.security.getState()"));
});

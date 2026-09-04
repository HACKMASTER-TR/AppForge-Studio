import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL("../../android-app/app/", import.meta.url);
const source = (path) =>
  readFile(new URL(`src/main/java/com/appforge/studio/${path}`, androidRoot), "utf8");

test("Ultimate LSP uses Content-Length framed JSON-RPC over local Linux stdio", async () => {
  const [protocol, session] = await Promise.all([
    source("terminal/UltimateLspProtocol.kt"),
    source("terminal/LinuxLspSession.kt")
  ]);

  assert.match(protocol, /class LspContentLengthFramer/);
  assert.match(protocol, /Content-Length/);
  assert.match(session, /"initialize"/);
  assert.match(session, /"initialized"/);
  assert.match(session, /textDocument\/didOpen/);
  assert.match(session, /textDocument\/didChange/);
  assert.match(session, /textDocument\/completion/);
  assert.match(session, /textDocument\/definition/);
  assert.match(session, /textDocument\/publishDiagnostics/);
});

test("LSP server is launched only through the packaged rootless Linux engine", async () => {
  const session = await source("terminal/LinuxLspSession.kt");

  assert.match(session, /PackagedLinuxEngine/);
  assert.match(session, /requireLauncher\(\)/);
  assert.match(session, /ProrootPinnedRuntime/);
  assert.match(session, /buildShellArguments/);
  assert.match(session, /ProcessBuilder/);
  assert.doesNotMatch(session, /\/system\/bin\/sh/);
  assert.doesNotMatch(session, /HttpURLConnection|URL\(/);
  assert.doesNotMatch(session, /installCommand/);
});

test("LSP document URIs remain inside the AppForge workspace", async () => {
  const protocol = await source("terminal/UltimateLspProtocol.kt");

  assert.match(protocol, /file:\/\/\/workspace\//);
  assert.match(protocol, /it == "\.\."/);
  assert.match(protocol, /fromWorkspaceUri/);
  assert.match(protocol, /offsetFor/);
});

test("editor exposes diagnostics autocomplete and go-to-definition without auto-installing servers", async () => {
  const [editor, core] = await Promise.all([
    source("terminal/UltimateCodeEditorPanel.kt"),
    source("terminal/UltimateCodeEditorCore.kt")
  ]);

  assert.match(editor, /LinuxLspSession/);
  assert.match(editor, /Tanılamalar/);
  assert.match(editor, /Tamamlama İste/);
  assert.match(editor, /Tanıma Git/);
  assert.match(core, /fun isTrusted/);
  assert.doesNotMatch(editor, /LinuxShellEngine|toolchainCommand/);
});

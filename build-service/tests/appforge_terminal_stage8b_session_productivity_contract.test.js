import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (name) =>
  readFile(
    new URL(
      `src/main/java/com/appforge/studio/terminal/${name}`,
      androidRoot
    ),
    "utf8"
  );

test("Stage 8B persists only safe session-productivity metadata for favorites and recent activation", async () => {
  const registry = await source("LinuxPtySessionRegistry.kt");

  assert.match(registry, /val favorite: Boolean/);
  assert.match(registry, /val lastActivatedAt: Long/);
  assert.match(registry, /fun setFavorite\(/);
  assert.match(registry, /fun markActivated\(/);
  assert.match(registry, /"favorite"/);
  assert.match(registry, /"lastActive"/);

  const persistStart = registry.indexOf("private fun persistLocked");
  const publishStart = registry.indexOf("private fun publishLocked");
  assert.ok(persistStart >= 0 && publishStart > persistStart);
  const persisted = registry.slice(persistStart, publishStart);

  assert.doesNotMatch(
    persisted,
    /snapshot|plainText|terminalOutput|commandText|password|accessToken|privateKey|environment/
  );
});

test("Stage 8B reopens the last-used session while favorites only control visible ordering", async () => {
  const panel = await source("LinuxMultiSessionTerminalPanel.kt");

  assert.match(panel, /\.maxByOrNull\s*\{\s*it\.lastActivatedAt\s*\}/s);
  assert.match(panel, /compareByDescending<LinuxManagedPtySessionState>\s*\{\s*it\.favorite/s);
  assert.match(panel, /\.thenByDescending\s*\{\s*it\.lastActivatedAt\s*\}/s);
  assert.match(panel, /LinuxPtySessionRegistry\s*\.markActivated/);
  assert.match(panel, /★ /);
});

test("Stage 8B session manager supports open, rename, favorite and safe descriptor duplication", async () => {
  const [panel, registry] = await Promise.all([
    source("LinuxMultiSessionTerminalPanel.kt"),
    source("LinuxPtySessionRegistry.kt")
  ]);

  assert.match(panel, /Oturum Yöneticisi/);
  assert.match(panel, /Aktif oturum adı/);
  assert.match(panel, /Adı Kaydet/);
  assert.match(panel, /★ Favori/);
  assert.match(panel, /☆ Favori/);
  assert.match(panel, /"Kopyala"/);
  assert.match(panel, /\.duplicateSession\(/);
  assert.match(registry, /fun duplicateSession\(/);
  assert.match(registry, /return createSession\(/);

  const duplicateStart = registry.indexOf("fun duplicateSession(");
  const startFunction = registry.indexOf("suspend fun start(", duplicateStart);
  const duplicateBody = registry.slice(duplicateStart, startFunction);
  assert.doesNotMatch(
    duplicateBody,
    /buffer\.snapshot|session\.start|snapshot|plainText|terminalOutput/
  );
});

test("Stage 8B stores bounded appearance preferences without commands, output or credentials", async () => {
  const prefs = await source("TerminalUxPreferences.kt");
  const panel = await source("LinuxMultiSessionTerminalPanel.kt");

  assert.match(prefs, /fontSizeSp: Float/);
  assert.match(prefs, /productivityKeysExpanded: Boolean/);
  assert.match(prefs, /font_size_sp_v1/);
  assert.match(prefs, /productivity_keys_expanded_v1/);
  assert.match(prefs, /\.coerceIn\(/);
  assert.match(panel, /TerminalUxPreferences\.load/);
  assert.match(panel, /\.saveFontSize\(/);
  assert.match(panel, /\.saveProductivityKeysExpanded\(/);

  assert.doesNotMatch(
    prefs,
    /["'](?:command|history|terminalOutput|snapshot|plainText|token|password|privateKey|clipboard)/i
  );
});

test("Stage 8B adds transient shell-editing control keys without introducing command history", async () => {
  const panel = await source("LinuxMultiSessionTerminalPanel.kt");

  for (const key of [
    '"Ctrl+A"',
    '"Ctrl+E"',
    '"Ctrl+R"',
    '"Ctrl+U"',
    '"Ctrl+W"'
  ]) {
    assert.ok(panel.includes(key), `missing productivity key ${key}`);
  }

  for (const sequence of [
    '"\\u0001"',
    '"\\u0005"',
    '"\\u0012"',
    '"\\u0015"',
    '"\\u0017"'
  ]) {
    assert.ok(panel.includes(sequence), `missing control sequence ${sequence}`);
  }

  assert.match(panel, /TerminalControlKey/);
  assert.match(panel, /LinuxPtySessionRegistry\s*\.write/);
  assert.doesNotMatch(panel, /commandHistory|savedCommand|recentCommand/);
  assert.doesNotMatch(panel, /Linux komutu \/ giriş/);
  assert.doesNotMatch(panel, /Text\("Gönder"\)/);
});

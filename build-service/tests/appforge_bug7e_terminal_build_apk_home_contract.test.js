import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const mainUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  import.meta.url
);

const homeUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt",
  import.meta.url
);

const terminalUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
  import.meta.url
);

test("five-build stress test is admin-only for both UI and execution", async () => {
  const source = await readFile(mainUrl, "utf8");

  assert.match(
    source,
    /if\s*\(\s*!isAdminOpsAccount\s*\)\s*\{[\s\S]{0,180}Bu test hesabın için yetkili değil/
  );
  assert.match(
    source,
    /step == 10 &&\s*isAdminOpsAccount/
  );
  assert.match(
    source,
    /"5 Build Testi • Maks\. 3 Paralel"/
  );
});

test("successful build hides stale compile CTA while result UI remains", async () => {
  const source = await readFile(mainUrl, "utf8");

  const label = source.indexOf('"UYGULAMAYI DERLE"');
  assert.ok(label >= 0, "compile CTA label missing");

  const guard = source.lastIndexOf(
    "if (!(step == 10 && buildSucceeded)) {",
    label
  );
  assert.ok(guard >= 0, "successful-build CTA guard missing");

  assert.match(source, /"✅ Derleme tamamlandı"/);
  assert.match(source, /"APK'YI TEKRAR İNDİR"/);
  assert.match(source, /"APK'YI KUR"/);
});

test("home successful APK section searches and sorts successful APK outputs", async () => {
  const source = await readFile(homeUrl, "utf8");

  assert.match(source, /"successful_apks"\s+to\s+"Başarılı APK'lar"/);
  assert.match(source, /OutlinedTextField\(/);
  assert.match(source, /successfulApkSearch/);
  assert.match(source, /successfulApkNewestFirst/);
  assert.match(
    source,
    /it\.status\.equals\(\s*"success",\s*ignoreCase\s*=\s*true\s*\)/
  );
  assert.match(source, /!it\.apkUrl[\s\S]{0,40}\.isNullOrBlank\(\)/);
  assert.match(source, /sortedByDescending\s*\{\s*it\.createdAt\s*\}/);
  assert.match(source, /sortedBy\s*\{\s*it\.createdAt\s*\}/);
  assert.match(source, /build\.projectName/);
  assert.match(source, /build\.packageName/);
  assert.match(
    source,
    /AppForgeBuildNumbers[\s\S]{0,80}\.label\(\s*build\.buildNo\s*\)/
  );
  assert.match(source, /onClick\s*=\s*onOpenHistory/);
});

test("terminal debounces PTY geometry resize during IME animation", async () => {
  const source = await readFile(terminalUrl, "utf8");

  assert.match(source, /TERMINAL_IME_GEOMETRY_SETTLE_MS\s*=\s*160L/);
  assert.match(source, /pendingGeometryResize[\s\S]{0,120}\?\.cancel\(\)/);
  assert.match(
    source,
    /pendingGeometryResize\s*=\s*scope\.launch\s*\{[\s\S]{0,220}delay\(\s*TERMINAL_IME_GEOMETRY_SETTLE_MS\s*\)[\s\S]{0,320}LocalPtySessionRegistry\s*\.resize\(/
  );
});

test("BUG7D bottom IME inset remains enabled", async () => {
  const source = await readFile(terminalUrl, "utf8");

  assert.match(
    source,
    /\.windowInsetsPadding\(\s*WindowInsets\.ime\.only\(\s*WindowInsetsSides\.Bottom\s*\)\s*\)/
  );
});

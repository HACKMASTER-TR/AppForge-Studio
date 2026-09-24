import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const main = new URL("../../android-app/app/src/main/java/com/appforge/studio/", import.meta.url);
const read = path => readFile(new URL(path, main), "utf8");

test("verified accountless owner gets distinct Terminal workspace without legacy migration", async () => {
  const source = await read("terminal/WorkspaceFileService.kt");
  assert.match(source, /accountScope\s*\(\s*context,\s*accountEmail/);
  assert.match(source, /if \(normalized\.isBlank\(\)\)/);
  assert.match(source, /OwnerAccessPolicy\.isActiveOwner\(context\)/);
  assert.match(source, /return "verified-owner-accountless-v1"/);
  assert.doesNotMatch(source, /"Aktif hesap bulunamadı\."/);
  assert.match(source, /accountEmail\.isNotBlank\(\)[\s\S]*?migrateLegacyWorkspaceForOwner/);
});

test("expired owner access has a back action before workspace resolution", async () => {
  const source = await read("terminal/TerminalWorkspaceScreen.kt");
  const screen = source.slice(source.indexOf("fun TerminalWorkspaceScreen("));
  const guard = screen.indexOf("if (!OwnerAccessPolicy.isActiveOwner(context, accountEmail))");
  const back = screen.indexOf("Button(onClick = onBack)", guard);
  const earlyReturn = screen.indexOf("\n        return", back);
  const workspace = screen.indexOf("TerminalWorkspaceResolver.resolve(", earlyReturn);
  assert.ok(guard >= 0 && back > guard && earlyReturn > back && workspace > earlyReturn);
});

test("MainActivity retains the owner-only Terminal route and accountless session", async () => {
  const source = await read("MainActivity.kt");
  assert.match(source, /screen\s*==\s*AppScreen\.TERMINAL\s*&&\s*!terminalOwner/);
  assert.match(source, /AppScreen\.TERMINAL\s*->[\s\S]*?TerminalWorkspaceScreen\([\s\S]*?session\s*\?\.email\s*\.orEmpty\(\)/);
});

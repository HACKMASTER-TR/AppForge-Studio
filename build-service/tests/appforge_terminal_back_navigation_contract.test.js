import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt",
    import.meta.url
  ),
  "utf8"
);

test("Terminal secondary tabs consume Android back first", () => {
  assert.match(
    source,
    /selectedTab !=[\s\S]*TerminalWorkspaceTab\.TERMINAL/
  );

  assert.match(
    source,
    /selectedTab =[\s\S]*TerminalWorkspaceTab\.TERMINAL/
  );

  assert.match(
    source,
    /else ->[\s\S]*onBack\(\)/
  );
});

test("dangerous command dialog consumes back before navigation", () => {
  assert.match(
    source,
    /pendingDangerousCommand != null[\s\S]*pendingDangerousCommand =[\s\S]*null/
  );
});

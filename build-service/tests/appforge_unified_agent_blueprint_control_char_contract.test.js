import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function read(relative) {
  return fs.readFileSync(
    new URL(`../../${relative}`, import.meta.url),
    "utf8"
  );
}

const json = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBlueprintJson.kt"
);
const prompt = read(
  "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBlueprintPrompt.kt"
);

test("blueprint JSON repair is limited to raw controls inside quoted strings", () => {
  assert.match(json, /normalizeRawStringControlCharacters/);
  assert.match(json, /char\.code < 0x20/);
  assert.match(json, /inString/);
  assert.match(json, /escaped/);
  assert.match(json, /Parser\(json\)\.parseDocument/);
});

test("blueprint prompt explicitly requires escaped JSON controls", () => {
  assert.match(prompt, /kontrol karakteri kullanma/);
  assert.match(prompt, /JSON escape/);
});

test("normalization remains bounded by the blueprint JSON size gate", () => {
  const occurrences = [...json.matchAll(/MAX_JSON_CHARS/g)].length;
  assert.ok(occurrences >= 4, `expected pre/post normalization size checks, got ${occurrences}`);
  assert.match(json, /Normalize edilmiş Blueprint JSON/);
});

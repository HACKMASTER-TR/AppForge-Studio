import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",
    import.meta.url
  );

test(
  "autopilot controls stay inside owner-only shortcut gate",
  async () => {
    const source = await readFile(sourceUrl, "utf8");

    const ownerHash =
      createHash("sha256")
        .update("28550040284a@gmail.com")
        .digest("hex");

    assert.equal(
      ownerHash,
      "1249d3064d7f482d584f75caf93ea01649f13e4a26d183c4729d2fae5d205589"
    );

    assert.match(
      source,
      /OWNER_ACCOUNT_EMAIL_SHA256[\s\S]{0,200}1249d3064d7f482d584f75caf93ea01649f13e4a26d183c4729d2fae5d205589/
    );

    const submit =
      source.indexOf('"SUBMIT"');

    assert.ok(
      submit >= 0,
      "SUBMIT shortcut bulunamadı"
    );

    const ownerGate =
      source.lastIndexOf(
        "ownerQuickActionsEnabled",
        submit
      );

    assert.ok(
      ownerGate >= 0,
      "SUBMIT öncesinde ownerQuickActionsEnabled gate bulunamadı"
    );

    const ownerBlockEnd =
      source.indexOf(
        'PtyKey("ESC"',
        submit
      );

    assert.ok(
      ownerBlockEnd > submit,
      "owner shortcut bloğunun sonu bulunamadı"
    );

    const ownerBlock =
      source.slice(
        ownerGate,
        ownerBlockEnd
      );

    for (const label of [
      "APK",
      "DASH",
      "SUBMIT",
      "PIPELINE",
      "CI",
      "REPORT",
      "PERF"
    ]) {
      assert.ok(
        ownerBlock.includes(`"${label}"`),
        `${label} owner-only blok içinde olmalı`
      );
    }
  }
);

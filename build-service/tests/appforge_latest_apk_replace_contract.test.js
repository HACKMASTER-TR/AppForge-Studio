import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const owner = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/OwnerFilesPanel.kt",
    import.meta.url
  ),
  "utf8"
);

const command = await readFile(
  new URL(
    "../../android-app/app/src/main/assets/terminal/appforge-apk",
    import.meta.url
  ),
  "utf8"
);

test("owner APK bridge survives synchronization", () => {
  assert.equal(
    owner.includes("downloads.delete()"),
    false
  );

  assert.ok(
    owner.includes('".part"')
  );

  assert.ok(
    owner.includes("filterNot { source ->")
  );
});

test("self-update cleanup covers latest and commit-named AppForge APKs", () => {
  assert.ok(
    owner.includes(
      "AppForgeStudio-(?:latest|[0-9a-f]{7,40})"
    )
  );
});

test("APK publication is atomic and recreates its bridge", () => {
  assert.ok(
    command.includes(
      ".AppForgeStudio-latest.apk.part"
    )
  );

  assert.ok(
    command.includes(
      'mkdir -p "$download_dir"'
    )
  );

  assert.ok(
    command.includes("mv -f")
  );
});

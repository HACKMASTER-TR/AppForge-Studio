import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const projects =
  new URL(
    "../src/projects.js",
    import.meta.url
  );

const migration =
  new URL(
    "../sql/010_permanent_project_trial_slots.sql",
    import.meta.url
  );

const library =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
    import.meta.url
  );

test("quota counts permanent free slot ledger", async () => {
  const text = await readFile(projects, "utf8");
  assert.equal(text.includes("FROM appforge_free_project_slots"), true);
  assert.equal(text.includes("deletionRestoresSlot"), true);
});

test("slot ledger survives active project deletion", async () => {
  const text = await readFile(migration, "utf8");
  assert.equal(text.includes("PRIMARY KEY(user_id, package_name)"), true);
  assert.equal(text.includes("REFERENCES appforge_projects"), false);
});

test("android mirrors lifetime slot ledger", async () => {
  const text = await readFile(library, "utf8");
  assert.equal(text.includes("free_project_slots.json"), true);
  assert.equal(text.includes("claimFreeProjectSlot"), true);
  assert.equal(text.includes("freeProjectSlotsUsed"), true);
});

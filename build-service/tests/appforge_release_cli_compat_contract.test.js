import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appforge = await readFile(
  new URL("../../scripts/appforge", import.meta.url),
  "utf8"
);

test(
  "release uses explicit repo and restores git plus gh into PATH",
  () => {
    assert.match(
      appforge,
      /def ensure_versioned_release\(main_sha\):/
    );
    assert.match(appforge, /release_env\["PATH"\]/);
    assert.match(appforge, /shutil\.which\("git"\)/);
    assert.match(appforge, /shutil\.which\("gh"\)/);
    assert.match(
      appforge,
      /"release",[\s\S]{0,200}?"create"[\s\S]{0,300}?"--repo"/
    );
  }
);

test(
  "failed CI log collection falls back for older gh versions",
  () => {
    assert.match(appforge, /"--log-failed"/);
    assert.match(
      appforge,
      /if p\.returncode != 0:[\s\S]{0,500}?"--log"/
    );
  }
);

test(
  "release falls back from gh release to gh api",
  () => {
    assert.match(
      appforge,
      /CREATED VIA API FALLBACK/
    );

    assert.match(
      appforge,
      /"api",[\s\S]{0,120}?"POST"/
    );

    assert.match(
      appforge,
      /repos\/\{repo\}\/releases/
    );

    assert.match(
      appforge,
      /generate_release_notes=true/
    );

    assert.match(
      appforge,
      /target_commitish=\{main_sha\}/
    );
  }
);

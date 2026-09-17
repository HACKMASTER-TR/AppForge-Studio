import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../scripts/appforge",
    import.meta.url
  ),
  "utf8"
);

const autopilotStart = source.indexOf(
  "def autopilot():"
);

const autopilotEnd = source.indexOf(
  "\ndef recover():",
  autopilotStart
);

assert.ok(
  autopilotStart >= 0,
  "autopilot() missing"
);

assert.ok(
  autopilotEnd > autopilotStart,
  "autopilot() end missing"
);

const autopilot = source.slice(
  autopilotStart,
  autopilotEnd
);

test(
  "autopilot executes delivery preflight before mutable pipeline state",
  () => {
    const preflight = autopilot.indexOf(
      "preflight = delivery_preflight_result()"
    );

    const testingState = autopilot.indexOf(
      'state_write("TESTING"'
    );

    const releaseVersion = autopilot.indexOf(
      "prepare_release_version()"
    );

    const localGate = autopilot.indexOf(
      "local_gate()"
    );

    assert.ok(preflight >= 0);
    assert.ok(testingState > preflight);
    assert.ok(releaseVersion > preflight);
    assert.ok(localGate > preflight);
  }
);

test(
  "autopilot reuses isolated preflight implementation",
  () => {
    assert.match(
      source,
      /def delivery_preflight_result\(\):/
    );

    assert.match(
      source,
      /runpy\.run_path\(/
    );

    assert.match(
      source,
      /run_name="appforge_delivery_preflight"/
    );

    assert.match(
      source,
      /namespace\.get\(\s*"preflight"\s*\)/
    );
  }
);

test(
  "Android release and Play publish require preflight eligibility",
  () => {
    assert.match(
      autopilot,
      /release_capability == "ELIGIBLE"/
    );

    assert.match(
      autopilot,
      /play_capability == "ELIGIBLE"/
    );

    assert.match(
      autopilot,
      /ensure_versioned_release\(\s*main_sha\s*\)/
    );

    assert.match(
      autopilot,
      /wait_play_publish\(\s*main_sha\s*\)/
    );
  }
);

test(
  "blocked or unverified Play distribution becomes a successful skip",
  () => {
    assert.match(
      autopilot,
      /SKIPPED_BY_PREFLIGHT/
    );

    assert.match(
      autopilot,
      /play_state/
    );

    assert.match(
      autopilot,
      /play_reason/
    );
  }
);

test(
  "merge remains after required PR CI",
  () => {
    const ci = autopilot.indexOf(
      "ci_watch()"
    );

    const merge = autopilot.indexOf(
      "merge_pr(num)"
    );

    assert.ok(ci >= 0);
    assert.ok(merge > ci);
  }
);

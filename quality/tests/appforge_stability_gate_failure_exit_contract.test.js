import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../scripts/appforge-stability-gate",
    import.meta.url
  ),
  "utf8"
);

test(
  "stability gate failure path exits with numeric status 1",
  () => {
    assert.ok(
      source.includes(
        'echo "APPFORGE STABILITY GATE: BLOCKED"'
      )
    );

    assert.ok(
      source.includes(
        'echo "fail $FAIL"'
      )
    );

    assert.equal(
      source.includes(
        "exit 1\\n"
      ),
      false
    );

    assert.match(
      source,
      /echo "fail \$FAIL"\nexit 1\s*$/
    );
  }
);

test(
  "stability gate remains portable POSIX sh",
  () => {
    assert.ok(
      source.startsWith(
        "#!/bin/sh\n"
      )
    );

    assert.equal(
      source.includes(
        "Illegal number"
      ),
      false
    );
  }
);

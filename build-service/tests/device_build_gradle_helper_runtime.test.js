import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../.."
);

const installer = fs.readFileSync(
  path.join(
    repoRoot,
    "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  ),
  "utf8"
);

function getHelper() {
  const lines = installer.split(/\r?\n/);
  const opener = `cat > "$ROOT/ensure-gradle" <<'EOF'`;

  const starts = lines.flatMap(
    (line, index) => line === opener ? [index] : []
  );

  assert.equal(
    starts.length,
    1,
    "Installer must define exactly one ensure-gradle helper"
  );

  const start = starts[0];
  const end = lines.findIndex(
    (line, index) => index > start && line === "EOF"
  );

  assert.ok(end > start, "Helper heredoc must close");

  assert.equal(
    lines.slice(end + 1, end + 5)
      .some(line => line.includes(
        'chmod 0755 "$ROOT/ensure-gradle"'
      )),
    true
  );

  assert.ok(
    start < lines.findIndex(line =>
      line.includes('if [ -f "$READY" ]')
    ),
    "Helper must exist before the ready shortcut"
  );

  const helper = lines
    .slice(start + 1, end)
    .join("\n") + "\n";

  assert.match(
    helper,
    /APPFORGE_OFFLINE_GRADLE_MISSING/
  );

  assert.match(
    helper,
    /sha256sum -c -/
  );

  return helper;
}

function runScenario({
  installed = false,
  cached = false,
  validChecksum = true
} = {}) {
  const version = "8.14.3";

  const root = fs.mkdtempSync(
    path.join(os.tmpdir(), "af-gradle-test-")
  );

  try {
    const bin = path.join(root, "test-bin");
    const cache = path.join(root, "cache");
    const gradleBin = path.join(
      root,
      `gradle-${version}`,
      "bin"
    );

    const curlLog = path.join(root, "curl-called");
    const unzipLog = path.join(root, "unzip-called");
    const script = path.join(root, "ensure-gradle.sh");

    fs.mkdirSync(bin, { recursive: true });
    fs.mkdirSync(cache, { recursive: true });

    const originalRoot =
      'root="/opt/appforge-device"';

    const original = getHelper();

    assert.ok(original.includes(originalRoot));

    fs.writeFileSync(
      script,
      original.replace(
        originalRoot,
        `root="${root}"`
      )
    );

    const fakeCurl = path.join(bin, "curl");

    fs.writeFileSync(
      fakeCurl,
      '#!/bin/sh\n' +
      'echo CALLED >> "$AF_CURL_LOG"\n' +
      'exit 97\n'
    );

    fs.chmodSync(fakeCurl, 0o700);

    const fakeUnzip = path.join(bin, "unzip");

    fs.writeFileSync(
      fakeUnzip,
      '#!/bin/sh\n' +
      'set -eu\n' +
      'echo CALLED >> "$AF_UNZIP_LOG"\n' +
      'mkdir -p "$AF_GRADLE_BIN"\n' +
      'printf "#!/bin/sh\\nexit 0\\n" > "$AF_GRADLE_BIN/gradle"\n' +
      'chmod 700 "$AF_GRADLE_BIN/gradle"\n'
    );

    fs.chmodSync(fakeUnzip, 0o700);

    if (installed) {
      fs.mkdirSync(
        gradleBin,
        { recursive: true }
      );

      const gradle = path.join(
        gradleBin,
        "gradle"
      );

      fs.writeFileSync(
        gradle,
        "#!/bin/sh\nexit 0\n"
      );

      fs.chmodSync(gradle, 0o700);
    }

    if (cached) {
      const zip = path.join(
        cache,
        `gradle-${version}-bin.zip`
      );

      const contents = Buffer.from(
        "APPFORGE_TEST_CACHE_ONLY\n"
      );

      fs.writeFileSync(zip, contents);

      const digest = crypto
        .createHash("sha256")
        .update(contents)
        .digest("hex");

      fs.writeFileSync(
        `${zip}.sha256`,
        validChecksum
          ? `${digest}\n`
          : `${"0".repeat(64)}\n`
      );
    }

    const result = spawnSync(
      "sh",
      [script, version],
      {
        encoding: "utf8",
        timeout: 10000,
        env: {
          ...process.env,
          PATH: `${bin}:${process.env.PATH ?? ""}`,
          APPFORGE_DEVICE_OFFLINE: "1",
          AF_CURL_LOG: curlLog,
          AF_UNZIP_LOG: unzipLog,
          AF_GRADLE_BIN: gradleBin
        }
      }
    );

    return {
      status: result.status,
      error: result.error,
      stderr: result.stderr,
      curlCalled: fs.existsSync(curlLog),
      unzipCalled: fs.existsSync(unzipLog)
    };
  } finally {
    fs.rmSync(
      root,
      { recursive: true, force: true }
    );
  }
}

test("installer has one persistent safe Gradle helper", () => {
  getHelper();
});

test("installed Gradle works offline without download", () => {
  const r = runScenario({ installed: true });

  assert.equal(r.error, undefined);
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.curlCalled, false);
  assert.equal(r.unzipCalled, false);
});

test("verified cache works offline without download", () => {
  const r = runScenario({ cached: true });

  assert.equal(r.error, undefined);
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.curlCalled, false);
  assert.equal(r.unzipCalled, true);
});

test("missing cache fails closed offline", () => {
  const r = runScenario();

  assert.equal(r.error, undefined);
  assert.equal(r.status, 42, r.stderr);

  assert.match(
    r.stderr,
    /APPFORGE_OFFLINE_GRADLE_MISSING/
  );

  assert.equal(r.curlCalled, false);
  assert.equal(r.unzipCalled, false);
});

test("invalid cache checksum fails closed offline", () => {
  const r = runScenario({
    cached: true,
    validChecksum: false
  });

  assert.equal(r.error, undefined);
  assert.equal(r.status, 42, r.stderr);

  assert.match(
    r.stderr,
    /APPFORGE_OFFLINE_GRADLE_MISSING/
  );

  assert.equal(r.curlCalled, false);
  assert.equal(r.unzipCalled, false);
});

/*
 * APPFORGE_WINDOWS_OUTPUT_ARTIFACT_FLOW_V2_CONTRACT
 *
 * Permanent source contract for:
 * - Portable ticket = exe
 * - Native ticket = native-exe
 * - persisted build history recovery
 * - distinct Windows filenames
 * - public Downloads classification
 * - legacy generic EXE = Portable
 * - legacy Unified Agent Native request = fail-closed
 */

import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  fileURLToPath
} from "node:url";


const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repo =
  path.resolve(
    here,
    "../.."
  );


const read =
  relative =>
    fs.readFileSync(
      path.join(
        repo,
        relative
      ),
      "utf8"
    );


const main =
  read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

const api =
  read(
    "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  );

const engine =
  read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );

const model =
  read(
    "android-app/app/src/main/java/com/appforge/studio/ui/BuildArtifactModel.kt"
  );

const downloads =
  read(
    "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  );


function extractBracedBlock(
  source,
  marker
) {
  const match =
    source.match(
      marker
    );

  assert.ok(
    match,
    `missing structural marker: ${marker}`
  );

  const start =
    match.index;

  const opening =
    source.indexOf(
      "{",
      start + match[0].length
    );

  assert.notEqual(
    opening,
    -1,
    "opening brace missing"
  );

  let depth = 0;
  let quote = null;
  let escaped = false;

  for (
    let index = opening;
    index < source.length;
    index += 1
  ) {
    const char =
      source[index];

    if (quote !== null) {
      if (escaped) {
        escaped = false;
        continue;
      }

      if (char === "\\") {
        escaped = true;
        continue;
      }

      if (char === quote) {
        quote = null;
      }

      continue;
    }

    if (
      char === '"' ||
      char === "'"
    ) {
      quote = char;
      continue;
    }

    if (char === "{") {
      depth += 1;
      continue;
    }

    if (char === "}") {
      depth -= 1;

      if (depth === 0) {
        return source.slice(
          start,
          index + 1
        );
      }
    }
  }

  assert.fail(
    `unterminated block: ${marker}`
  );
}


test(
  "Portable and Native expose distinct download ticket identities",
  () => {
    assert.match(
      model,
      /WINDOWS_PORTABLE_EXE\s*\([\s\S]{0,300}?ticketKind\s*=\s*"exe"/
    );

    assert.match(
      model,
      /WINDOWS_NATIVE_EXE\s*\([\s\S]{0,300}?ticketKind\s*=\s*"native-exe"/
    );
  }
);


test(
  "Windows artifact filenames carry explicit Portable and Native suffixes",
  () => {
    const builder =
      extractBracedBlock(
        model,
        /internal\s+fun\s+buildArtifactFileName\s*\(/
      );

    assert.match(
      builder,
      /BuildArtifactType\.WINDOWS_PORTABLE_EXE\s*->\s*"_windows-portable"/
    );

    assert.match(
      builder,
      /BuildArtifactType\.WINDOWS_NATIVE_EXE\s*->\s*"_windows-native"/
    );

    assert.match(
      builder,
      /windowsSuffix/
    );

    assert.match(
      builder,
      /record\.type\.extension/
    );

    /*
     * The complete filename is intentionally assembled:
     *
     * <project>_<build> + _windows-portable + .exe
     * <project>_<build> + _windows-native   + .exe
     *
     * A complete literal such as "_windows-native.exe"
     * is therefore not required in source.
     */
  }
);


test(
  "successful build history recovers Portable versus Native from buildOutput",
  () => {
    const available =
      extractBracedBlock(
        model,
        /internal\s+fun\s+SavedBuild\.availableArtifacts\s*\(/
      );

    assert.match(
      available,
      /buildOutput/
    );

    assert.match(
      available,
      /WINDOWS_PORTABLE_EXE/
    );

    assert.match(
      available,
      /WINDOWS_NATIVE_EXE/
    );
  }
);


test(
  "BuildStep requests Native with native-exe and Portable or legacy with exe",
  () => {
    assert.match(
      main,
      /private\s+fun\s+windowsExeDownloadTicketType\s*\([\s\S]{0,1000}?"native-exe"[\s\S]{0,500}?->\s*"native-exe"[\s\S]{0,500}?else\s*->\s*"exe"/
    );

    const typedCalls =
      main.match(
        /createDownloadTicket\s*\(\s*id\s*,\s*windowsExeDownloadTicketType\s*\(\s*buildOutput\s*\)\s*\)/g
      ) ?? [];

    assert.equal(
      typedCalls.length,
      3
    );

    assert.doesNotMatch(
      main,
      /createDownloadTicket\s*\(\s*id\s*,\s*"exe"\s*\)/
    );
  }
);


test(
  "BuildApiClient persisted device recovery is output-type aware",
  () => {
    const persisted =
      extractBracedBlock(
        api,
        /private\s+fun\s+persistedDeviceArtifact\s*\(/
      );

    assert.match(
      persisted,
      /normalizedArtifactTicketKind/
    );

    assert.match(
      persisted,
      /buildOutput/
    );

    assert.match(
      persisted,
      /native-exe/
    );

    assert.match(
      persisted,
      /return@runCatching\s+null/
    );
  }
);


test(
  "legacy Unified Agent generic EXE remains Portable and rejects Native fail-closed",
  () => {
    const unified =
      extractBracedBlock(
        api,
        /private\s+fun\s+persistedUnifiedAgentArtifact\s*\(/
      );

    assert.match(
      unified,
      /normalizedArtifactTicketKind\s*\(\s*kind\s*\)/
    );

    assert.match(
      unified,
      /ticketKind\s*==\s*"native-exe"/
    );

    assert.match(
      unified,
      /if\s*\(\s*ticketKind\s*==\s*"native-exe"\s*\)\s*\{\s*return@runCatching\s+null\s*\}/
    );

    /*
     * Presence of "native-exe" here is REQUIRED:
     * it is the rejection guard, not Native classification.
     */
  }
);


test(
  "DeviceBuildEngine recognizes Portable and Native as separate Windows artifact requests",
  () => {
    assert.match(
      engine,
      /WINDOWS_EXE/
    );

    assert.match(
      engine,
      /WINDOWS_NATIVE_EXE/
    );

    assert.match(
      engine,
      /native-exe/
    );

    assert.match(
      engine,
      /Windows Portable EXE/
    );

    assert.match(
      engine,
      /Windows Native EXE/
    );
  }
);


test(
  "public Downloads classifies Native before Portable and generic legacy EXE",
  () => {
    const classifier =
      extractBracedBlock(
        downloads,
        /private\s+fun\s+typeFromLocalFile\s*\(/
      );

    const nativeIndex =
      classifier.indexOf(
        "_windows-native.exe"
      );

    const portableIndex =
      classifier.indexOf(
        "_windows-portable.exe"
      );

    const genericIndex =
      classifier.indexOf(
        '".exe"'
      );

    assert.ok(
      nativeIndex >= 0,
      "Native filename classifier missing"
    );

    assert.ok(
      portableIndex >= 0,
      "Portable filename classifier missing"
    );

    assert.ok(
      genericIndex >= 0,
      "legacy generic EXE classifier missing"
    );

    assert.ok(
      nativeIndex < genericIndex,
      "Native must be classified before generic EXE fallback"
    );

    assert.ok(
      portableIndex < genericIndex,
      "Portable explicit name must be classified before generic EXE fallback"
    );

    assert.match(
      classifier,
      /WINDOWS_NATIVE_EXE/
    );

    assert.match(
      classifier,
      /WINDOWS_PORTABLE_EXE/
    );
  }
);

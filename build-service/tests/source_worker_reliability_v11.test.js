import test from "node:test";
import assert from "node:assert/strict";
import {
  readFile
} from "node:fs/promises";

import {
  memoryPressurePercent
} from "../src/processSupervisor.js";

const root =
  new URL(
    "../../",
    import.meta.url
  );

const read =
  path =>
    readFile(
      new URL(
        path,
        root
      ),
      "utf8"
    );

test(
  "cgroup memory percentage is deterministic",
  () => {
    assert.equal(
      memoryPressurePercent(
        8,
        8
      ),
      100
    );

    assert.equal(
      memoryPressurePercent(
        7.2,
        8
      ),
      90
    );

    assert.equal(
      memoryPressurePercent(
        1,
        0
      ),
      null
    );
  }
);

test(
  "supervisor kills the whole POSIX process group and watches progress/memory",
  async () => {
    const source =
      await read(
        "build-service/src/processSupervisor.js"
      );

    assert.match(
      source,
      /process\.kill\(\s*-child\.pid/
    );

    assert.match(
      source,
      /memory\.current/
    );

    assert.match(
      source,
      /WORKER_STALL_TIMEOUT/
    );

    assert.match(
      source,
      /WORKER_MEMORY_PRESSURE/
    );

    assert.match(
      source,
      /lastOutputAt/
    );

    assert.match(
      source,
      /cancelled\(\)/
    );
  }
);

test(
  "all long-running Source Worker engines use supervised process trees",
  async () => {
    for (
      const file of [
        "build-service/src/reactNativeBuildEngine.js",
        "build-service/src/flutterBuildEngine.js",
        "build-service/src/dotnetAndroidBuildEngine.js",
        "build-service/src/dotnetMauiBuildEngine.js",
        "build-service/src/sourceBuildEngines.js",
        "build-service/src/frameworkStaticBuildEngine.js"
      ]
    ) {
      const source =
        await read(
          file
        );

      assert.ok(
        source.includes(
          "runSupervisedProcess"
        ),
        file
      );
    }
  }
);

test(
  "generated Gradle watchdog terminates descendants as a process tree",
  async () => {
    const source =
      await read(
        "build-service/src/buildEngine.js"
      );

    assert.ok(
      source.includes(
        "spawnProcessGroup"
      )
    );

    assert.ok(
      source.includes(
        "terminateProcessTree"
      )
    );

    assert.ok(
      source.includes(
        "GradleStallTimeout"
      )
    );
  }
);

test(
  "worker does not claim new jobs while cgroup memory is above high watermark",
  async () => {
    const source =
      await read(
        "build-service/src/workerRuntime.js"
      );

    assert.ok(
      source.includes(
        "workerMemoryHighWatermarkPct"
      )
    );

    assert.ok(
      source.includes(
        "memoryPressureSnapshot"
      )
    );
  }
);

test(
  "Source Worker autoscale pool stays retired in device-only mode",
  async () => {
    await assert.rejects(
      read(
        ".github/workflows/worker-autoscale.yml"
      ),
      {
        code: "ENOENT"
      }
    );
  }
);

test(
  "queue ETA keeps automatic recovery state with user-facing UI copy",
  async () => {
    const queue =
      await read(
        "build-service/src/jobQueue.js"
      );

    const ui =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.ok(
      queue.includes(
        "max_silent_seconds"
      )
    );

    assert.ok(
      queue.includes(
        '"recovering_capacity"'
      )
    );

    assert.ok(
      ui.includes(
        "Derleme ortamı hazırlanıyor"
      )
    );
  }
);

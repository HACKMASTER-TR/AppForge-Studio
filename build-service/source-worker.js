import {
  config,
  assertCriticalConfig
} from "./src/config.js";
import {
  assertSourceBuildIsolation
} from "./src/sourceBuildIsolation.js";

assertCriticalConfig();

if (
  config.sourceBuildRequireIsolation !==
    true
) {
  throw new Error(
    "Dedicated source Worker SOURCE_BUILD_REQUIRE_ISOLATION=true olmadan başlatılamaz."
  );
}

const status =
  assertSourceBuildIsolation(
    {
      engine:
        "node-web",
      mode:
        config.sourceBuildIsolationMode,
      requireIsolation:
        true,
      workerCapabilities:
        config.workerCapabilities,
      requiredCapability:
        config.sourceBuildIsolationCapability
    }
  );

if (
  !status.attestedIsolation
) {
  throw new Error(
    "Dedicated source Worker isolation capability attestation başarısız."
  );
}

if (
  typeof process.getuid ===
    "function" &&
  process.getuid() ===
    0
) {
  throw new Error(
    "Dedicated source Worker root kullanıcıyla çalıştırılamaz."
  );
}

console.log(
  JSON.stringify(
    {
      type:
        "source_worker_isolation",
      mode:
        status.mode,
      capability:
        status.requiredCapability,
      uid:
        typeof process.getuid ===
          "function"
          ? process.getuid()
          : null,
      attested:
        status.attestedIsolation
    },
    null,
    2
  )
);

/*
 * source-worker.js yalnız deployment/startup guard'dır.
 * Gerçek proses/filesystem/network izolasyonu container/VM platformu
 * tarafından sağlanır. Guard geçtikten sonra mevcut Worker runtime açılır.
 */
await import(
  "./worker.js"
);

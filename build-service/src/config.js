import path from "path";

function boolEnv(name, fallback = false) {
  const raw = process.env[name];
  if (raw == null) return fallback;
  return String(raw).toLowerCase() === "true";
}

function csv(name, fallback = "") {
  return String(process.env[name] || fallback)
    .split(",")
    .map(x => x.trim())
    .filter(Boolean);
}

export const config = {
  port: Number(process.env.PORT || 8080),
  publicBaseUrl: String(process.env.PUBLIC_BASE_URL || "http://localhost:8080")
    .replace(/\/$/, ""),
  webStudioAllowedOrigins: csv(
    "WEB_STUDIO_ALLOWED_ORIGINS",
    "https://hackmaster-tr.github.io"
  ),

  gradleBin: process.env.GRADLE_BIN || "gradle",
  gradleCacheRoot: path.resolve(
    process.env.GRADLE_CACHE_ROOT || "./gradle-cache"
  ),
  workRoot: path.resolve(process.env.WORK_ROOT || "./work"),
  outputRoot: path.resolve(process.env.OUTPUT_ROOT || "./outputs"),
  sharedInputRoot: path.resolve(
    process.env.SHARED_INPUT_ROOT || "./shared-inputs"
  ),

  databaseUrl: String(process.env.DATABASE_URL || ""),
  jwtSecret: String(process.env.JWT_SECRET || ""),
  registrationEnabled: boolEnv("REGISTRATION_ENABLED", true),
  requireVerifiedEmailForBuild: boolEnv(
    "REQUIRE_VERIFIED_EMAIL_FOR_BUILD",
    false
  ),

  runInlineWorker: boolEnv("RUN_INLINE_WORKER", true),
  workerId:
    String(process.env.WORKER_ID || "").trim() ||
    `worker-${process.pid}`,
  workerPollMs: Math.max(
    250,
    Number(process.env.WORKER_POLL_MS || 500)
  ),
  workerHeartbeatMs: Math.max(
    1000,
    Number(process.env.WORKER_HEARTBEAT_MS || 5000)
  ),
  workerStaleAfterMs: Math.max(
    10000,
    Number(process.env.WORKER_STALE_AFTER_MS || 60000)
  ),
  workerCapabilities: csv(
    "WORKER_CAPABILITIES",
    "android-api-37,build-tools-36.0.0,java-17,gradle"
  ),
  workerStrictToolchain: boolEnv(
    "WORKER_STRICT_TOOLCHAIN",
    true
  ),
  expectedAndroidApi: Number(
    process.env.EXPECTED_ANDROID_API || 37
  ),
  expectedBuildTools: String(
    process.env.EXPECTED_BUILD_TOOLS || "36.0.0"
  ),
  expectedGradle: String(
    process.env.EXPECTED_GRADLE || "9.3.1"
  ),
  expectedJdkMajor: Number(
    process.env.EXPECTED_JDK_MAJOR || 17
  ),
  unityBuildEnabled: boolEnv(
    "UNITY_BUILD_ENABLED",
    false
  ),
  unityEditorPath:
    String(
      process.env.UNITY_EDITOR_PATH ||
      ""
    ).trim(),
  unityEditorVersion:
    String(
      process.env.UNITY_EDITOR_VERSION ||
      ""
    ).trim(),
  unityAndroidModulePath:
    String(
      process.env.UNITY_ANDROID_MODULE_PATH ||
      ""
    ).trim(),
  unityWorkerHome:
    path.resolve(
      process.env.UNITY_WORKER_HOME ||
      "./unity-worker-home"
    ),
  unityBuildTimeoutMs: Math.max(
    60_000,
    Number(
      process.env.UNITY_BUILD_TIMEOUT_MS ||
      3_600_000
    )
  ),
  buildConcurrency: Math.max(
    1,
    Number(process.env.BUILD_CONCURRENCY || 2)
  ),
  sourceBuildIsolationMode:
    String(
      process.env.SOURCE_BUILD_ISOLATION_MODE ||
      "shared"
    )
      .trim()
      .toLowerCase(),
  sourceBuildRequireIsolation: boolEnv(
    "SOURCE_BUILD_REQUIRE_ISOLATION",
    false
  ),
  sourceBuildIsolationCapability:
    String(
      process.env.SOURCE_BUILD_ISOLATION_CAPABILITY ||
      "source-isolation-dedicated"
    )
      .trim(),
  maxQueueSize: Math.max(
    1,
    Number(process.env.MAX_QUEUE_SIZE || 100)
  ),
  maxJobAttempts: Math.max(
    1,
    Number(process.env.MAX_JOB_ATTEMPTS || 2)
  ),
  buildCacheEnabled: boolEnv("BUILD_CACHE_ENABLED", true),
  buildCacheTtlHours: Math.max(
    1,
    Number(process.env.BUILD_CACHE_TTL_HOURS || 24)
  ),

  rateLimitPerHour: Math.max(
    1,
    Number(process.env.RATE_LIMIT_PER_HOUR || 30)
  ),
  outputRetentionHours: Math.max(
    1,
    Number(process.env.OUTPUT_RETENTION_HOURS || 72)
  ),
  liveLogPollMs: Math.max(
    250,
    Number(process.env.LIVE_LOG_POLL_MS || 750)
  ),
  liveLogMaxMinutes: Math.max(
    1,
    Number(process.env.LIVE_LOG_MAX_MINUTES || 30)
  ),
  idempotencyTtlHours: Math.max(
    1,
    Number(process.env.IDEMPOTENCY_TTL_HOURS || 24)
  ),
  downloadTicketMinutes: Math.max(
    1,
    Number(process.env.DOWNLOAD_TICKET_MINUTES || 5)
  ),

  redisUrl:
    String(process.env.REDIS_URL || "").trim(),
  redisRequired: boolEnv(
    "REDIS_REQUIRED",
    false
  ),
  redisPrefix:
    String(
      process.env.REDIS_PREFIX ||
      "appforge"
    ).trim() || "appforge",
  redisConnectTimeoutMs: Math.max(
    500,
    Number(
      process.env.REDIS_CONNECT_TIMEOUT_MS ||
      3000
    )
  ),

  storageDriver:
    String(process.env.STORAGE_DRIVER || "local").toLowerCase(),
  s3Endpoint: String(process.env.S3_ENDPOINT || ""),
  s3Region: String(process.env.S3_REGION || "us-east-1"),
  s3Bucket: String(process.env.S3_BUCKET || ""),
  s3AccessKeyId: String(process.env.S3_ACCESS_KEY_ID || ""),
  s3SecretAccessKey: String(process.env.S3_SECRET_ACCESS_KEY || ""),
  s3ForcePathStyle: boolEnv("S3_FORCE_PATH_STYLE", true),

  smtpHost: String(process.env.SMTP_HOST || ""),
  smtpPort: Number(process.env.SMTP_PORT || 587),
  smtpSecure: boolEnv("SMTP_SECURE", false),
  smtpUser: String(process.env.SMTP_USER || ""),
  smtpPass: String(process.env.SMTP_PASS || ""),
  smtpRequired: boolEnv(
    "SMTP_REQUIRED",
    false
  ),
  emailFrom: String(
    process.env.EMAIL_FROM ||
    "AppForge <no-reply@appforge.local>"
  ),

  sentryDsn:
    String(process.env.SENTRY_DSN || "").trim(),
  sentryEnvironment:
    String(
      process.env.SENTRY_ENVIRONMENT ||
      process.env.NODE_ENV ||
      "development"
    ),
  sentryTracesSampleRate: Math.max(
    0,
    Math.min(
      1,
      Number(
        process.env.SENTRY_TRACES_SAMPLE_RATE ||
        0.1
      )
    )
  ),

  totpEncryptionKey:
    String(process.env.TOTP_ENCRYPTION_KEY || ""),

  googlePlayServiceAccountJson:
    String(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || ""),

  playVerifierEnabled: boolEnv(
    "PLAY_VERIFIER_ENABLED",
    false
  ),
  playAllowedPackages: csv(
    "PLAY_ALLOWED_PACKAGES"
  ),
  playInappProducts: csv(
    "PLAY_INAPP_PRODUCTS"
  ),
  playConsumableProducts: csv(
    "PLAY_CONSUMABLE_PRODUCTS"
  ),
  playSubscriptionProducts: csv(
    "PLAY_SUBSCRIPTION_PRODUCTS"
  ),
  playVerifyRatePerHour: Math.max(
    10,
    Number(process.env.PLAY_VERIFY_RATE_PER_HOUR || 300)
  ),

  studioAndroidPackage: String(
    process.env.STUDIO_ANDROID_PACKAGE ||
    "com.appforge.studio"
  ),
  studioReleaseCertSha256: csv(
    "STUDIO_RELEASE_CERT_SHA256"
  ).map(value =>
    value.replaceAll(":", "").toUpperCase()
  ),
  studioProProductId: String(
    process.env.STUDIO_PRO_PRODUCT_ID ||
    "appforge_pro_lifetime"
  ),
  studioProMonthlyProductId: String(
    process.env.STUDIO_PRO_MONTHLY_PRODUCT_ID ||
    "appforge_pro_monthly"
  ),
  playIntegrityEnabled: boolEnv(
    "PLAY_INTEGRITY_ENABLED",
    false
  ),
  playIntegrityCloudProjectNumber: Number(
    process.env.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER ||
    0
  ),
  proRequireIntegrity: boolEnv(
    "PRO_REQUIRE_INTEGRITY",
    true
  ),

  freeProjectLimit: Math.max(
    1,
    Number(
      process.env.FREE_PROJECT_LIMIT ||
      5
    )
  )
};

export function assertCriticalConfig() {
  if (
    ![
      "shared",
      "dedicated",
      "container",
      "vm"
    ].includes(
      config.sourceBuildIsolationMode
    )
  ) {
    throw new Error(
      "SOURCE_BUILD_ISOLATION_MODE shared/dedicated/container/vm olmalı."
    );
  }

  if (
    config.sourceBuildRequireIsolation &&
    !config.sourceBuildIsolationCapability
  ) {
    throw new Error(
      "SOURCE_BUILD_ISOLATION_CAPABILITY gerekli."
    );
  }

  if (!config.databaseUrl) {
    throw new Error("DATABASE_URL gerekli.");
  }

  if (
    config.redisRequired &&
    !config.redisUrl
  ) {
    throw new Error(
      "REDIS_REQUIRED=true ise REDIS_URL gerekli."
    );
  }

  if (
    config.smtpRequired &&
    !config.smtpHost
  ) {
    throw new Error(
      "SMTP_REQUIRED=true ise SMTP_HOST gerekli."
    );
  }

  if (
    config.smtpHost &&
    config.smtpUser &&
    !config.smtpPass
  ) {
    throw new Error(
      "SMTP_USER tanımlıysa SMTP_PASS gerekli."
    );
  }

  if (!config.jwtSecret || config.jwtSecret.length < 32) {
    throw new Error("JWT_SECRET en az 32 karakter olmalı.");
  }

  if (config.storageDriver === "s3") {
    if (!config.s3Bucket) {
      throw new Error("S3_BUCKET gerekli.");
    }
    if (!config.s3AccessKeyId) {
      throw new Error("S3_ACCESS_KEY_ID gerekli.");
    }
    if (!config.s3SecretAccessKey) {
      throw new Error("S3_SECRET_ACCESS_KEY gerekli.");
    }
  }

  if (
    config.totpEncryptionKey &&
    config.totpEncryptionKey.length < 32
  ) {
    throw new Error(
      "TOTP_ENCRYPTION_KEY en az 32 karakter olmalı."
    );
  }
}

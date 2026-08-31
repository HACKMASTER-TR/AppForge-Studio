const EPHEMERAL_PROFILES =
  new Set([
    "low-memory",
    "native-android",
    "python-android"
  ]);


export function sourceGradleProfileName(
  engine,
  fallback = "throughput"
) {
  const normalized =
    String(engine || "")
      .trim()
      .toLowerCase();

  if (
    normalized ===
      "android-gradle"
  ) {
    return "native-android";
  }

  if (
    normalized ===
      "python-android"
  ) {
    return "python-android";
  }

  return fallback;
}

export function gradlePerformanceProfile(
  name = "throughput"
) {
  const normalized =
    String(name || "")
      .trim()
      .toLowerCase();

  /*
   * Chaquopy configuration-cache uyumlu değil.
   * Python Android build'leri tek worker ve ephemeral
   * daemon ile, configuration cache kapalı çalışır.
   */
  if (
    normalized ===
      "python-android"
  ) {
    return {
      name:
        "python-android",
      maxWorkers: 1,
      heapMb: 512,
      metaspaceMb: 320,
      codeCacheMb: 96,
      parallel: false,
      incremental: false,
      configurationCache: false,
      gc:
        "-XX:+UseSerialGC"
    };
  }

  /*
   * Native Android/Kotlin projeleri:
   *
   * - Tek worker
   * - Parallel kapalı
   * - Persistent daemon yok
   * - Dosya sistemi watcher kapalı
   * - Gradle launcher JVM daha küçük
   * - Build daemon heap'i korunurken metaspace/code-cache
   *   sınırlandırılarak container OOM riski azaltılır.
   */
  if (
    normalized ===
      "native-android"
  ) {
    return {
      name:
        "native-android",
      maxWorkers: 1,
      heapMb: 320,
      metaspaceMb: 224,
      codeCacheMb: 96,
      parallel: false,
      incremental: false,
      gc:
        "-XX:+UseSerialGC"
    };
  }

  if (
    normalized ===
      "low-memory"
  ) {
    return {
      name:
        "low-memory",
      maxWorkers: 1,
      heapMb: 320,
      metaspaceMb: 256,
      parallel: false,
      incremental: false,
      gc:
        "-XX:+UseSerialGC"
    };
  }

  if (
    normalized ===
      "balanced"
  ) {
    return {
      name:
        "balanced",
      maxWorkers: 2,
      heapMb: 640,
      metaspaceMb: 320,
      parallel: true,
      incremental: true,
      gc:
        "-XX:+UseG1GC"
    };
  }

  return {
    name:
      "throughput",
    maxWorkers: 4,
    heapMb: 1024,
    metaspaceMb: 512,
    parallel: true,
    incremental: true,
    gc:
      "-XX:+UseG1GC"
  };
}

export function gradleInvocationPlan(
  tasks,
  profileName = "throughput"
) {
  const unique =
    [...new Set(tasks)];

  if (
    EPHEMERAL_PROFILES.has(
      profileName
    )
  ) {
    return unique.map(
      task => [task]
    );
  }

  return unique.length
    ? [unique]
    : [];
}

export function gradleArguments(
  tasks,
  profile
) {
  const ephemeral =
    EPHEMERAL_PROFILES.has(
      profile.name
    );

  return [
    ...tasks,

    ephemeral
      ? "--no-daemon"
      : "--daemon",

    ...(
      [
        "native-android",
        "python-android"
      ].includes(
        profile.name
      )
        ? [
            "--no-watch-fs",
            "-x",
            "lintVitalAnalyzeRelease",
            "-x",
            "lintVitalReportRelease",
            "-x",
            "lintVitalRelease"
          ]
        : []
    ),

    "--build-cache",

    ...(
      profile.configurationCache ===
        false
        ? [
            "--no-configuration-cache"
          ]
        : [
            "--configuration-cache",
            "--configuration-cache-problems=warn"
          ]
    ),

    `--max-workers=${profile.maxWorkers}`,

    "-Pkotlin.compiler.execution.strategy=in-process",

    `-Dorg.gradle.parallel=${profile.parallel}`,

    `-Dorg.gradle.jvmargs=${gradleJvmOptions(profile)}`,

    "--stacktrace"
  ];
}

export function gradleClientJvmOptions(
  profile
) {
  if (
    profile.name ===
      "python-android"
  ) {
    return [
      "-Xmx96m",
      "-XX:MaxMetaspaceSize=128m",
      "-XX:ReservedCodeCacheSize=64m",
      "-XX:+UseSerialGC",
      "-Dfile.encoding=UTF-8"
    ].join(" ");
  }

  if (
    profile.name ===
      "native-android"
  ) {
    return [
      "-Xmx64m",
      "-XX:MaxMetaspaceSize=96m",
      "-XX:ReservedCodeCacheSize=64m",
      "-XX:+UseSerialGC",
      "-Dfile.encoding=UTF-8"
    ].join(" ");
  }

  if (
    profile.name ===
      "low-memory"
  ) {
    return [
      "-Xmx96m",
      "-XX:MaxMetaspaceSize=128m",
      "-XX:+UseSerialGC",
      "-Dfile.encoding=UTF-8"
    ].join(" ");
  }

  return gradleJvmOptions(
    profile
  );
}

export function gradleJvmOptions(
  profile
) {
  return [
    `-Xmx${profile.heapMb}m`,
    `-XX:MaxMetaspaceSize=${profile.metaspaceMb}m`,

    profile.codeCacheMb
      ? `-XX:ReservedCodeCacheSize=${profile.codeCacheMb}m`
      : null,

    profile.gc,

    "-Dfile.encoding=UTF-8"
  ]
    .filter(Boolean)
    .join(" ");
}

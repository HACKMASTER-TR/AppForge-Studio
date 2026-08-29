export function gradlePerformanceProfile(
  name = "balanced"
) {
  if (String(name).toLowerCase() === "low-memory") {
    return {
      name: "low-memory",
      maxWorkers: 1,
      heapMb: 320,
      metaspaceMb: 256,
      parallel: false,
      incremental: false,
      gc: "-XX:+UseSerialGC"
    };
  }

  return {
    name: "balanced",
    maxWorkers: 2,
    heapMb: 640,
    metaspaceMb: 320,
    parallel: true,
    incremental: true,
    gc: "-XX:+UseG1GC"
  };
}

export function gradleInvocationPlan(
  tasks,
  profileName = "balanced"
) {
  const unique = [...new Set(tasks)];
  if (profileName === "low-memory") {
    return unique.map(task => [task]);
  }
  return unique.length ? [unique] : [];
}

export function gradleArguments(
  tasks,
  profile
) {
  return [
    ...tasks,
    "--no-daemon",
    "--build-cache",
    `--max-workers=${profile.maxWorkers}`,
    `-Pkotlin.compiler.execution.strategy=in-process`,
    `-Dorg.gradle.parallel=${profile.parallel}`,
    "--stacktrace"
  ];
}

export function gradleJvmOptions(profile) {
  return [
    `-Xmx${profile.heapMb}m`,
    `-XX:MaxMetaspaceSize=${profile.metaspaceMb}m`,
    profile.gc,
    "-Dfile.encoding=UTF-8"
  ].join(" ");
}

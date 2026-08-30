export function gradlePerformanceProfile(
  name = "throughput"
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

  if (String(name).toLowerCase() === "balanced") {
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

  return {
    name: "throughput",
    maxWorkers: 4,
    heapMb: 1024,
    metaspaceMb: 512,
    parallel: true,
    incremental: true,
    gc: "-XX:+UseG1GC"
  };
}

export function gradleInvocationPlan(
  tasks,
  profileName = "throughput"
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
    profile.name === "low-memory"
      ? "--no-daemon"
      : "--daemon",
    "--build-cache",
    "--configuration-cache",
    "--configuration-cache-problems=warn",
    `--max-workers=${profile.maxWorkers}`,
    `-Pkotlin.compiler.execution.strategy=in-process`,
    `-Dorg.gradle.parallel=${profile.parallel}`,
    `-Dorg.gradle.jvmargs=${gradleJvmOptions(profile)}`,
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

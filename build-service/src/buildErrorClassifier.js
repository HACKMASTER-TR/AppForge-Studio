export function classifyBuildError(error) {
  const raw =
    String(
      error?.stack ||
      error?.message ||
      error ||
      ""
    );

  const text =
    raw.toLowerCase();

  const has = (...patterns) =>
    patterns.some(
      pattern =>
        text.includes(pattern)
    );

  if (
    has(
      "build_cancelled",
      "build iptal edildi"
    )
  ) {
    return {
      category: "cancelled",
      code: "BUILD_CANCELLED",
      retryable: false
    };
  }

  if (
    has(
      "econnreset",
      "etimedout",
      "eai_again",
      "enotfound",
      "socket hang up",
      "unexpected end of stream",
      "connection reset",
      "network is unreachable",
      "gateway timeout",
      "bad gateway",
      "service unavailable",
      "http 502",
      "http 503",
      "http 504"
    )
  ) {
    return {
      category: "network",
      code: "TRANSIENT_NETWORK",
      retryable: true
    };
  }

  if (
    has(
      "slowdown",
      "requesttimeout",
      "temporarily unavailable"
    )
  ) {
    return {
      category: "storage",
      code: "TRANSIENT_STORAGE",
      retryable: true
    };
  }

  if (
    has(
      "heartbeat",
      "stale worker",
      "worker unavailable",
      "worker disconnected",
      "container terminated",
      "sigterm"
    )
  ) {
    return {
      category: "worker",
      code: "TRANSIENT_WORKER",
      retryable: true
    };
  }

  if (
    has(
      "debug.keystore",
      "keystore",
      "apksigner",
      "signing",
      "key alias",
      "store password",
      "key password"
    )
  ) {
    return {
      category: "signing",
      code: "SIGNING_ERROR",
      retryable: false
    };
  }

  if (
    has(
      "google-services.json",
      "firebase",
      "google services plugin"
    )
  ) {
    return {
      category: "firebase",
      code: "FIREBASE_ERROR",
      retryable: false
    };
  }

  if (
    has(
      "unresolved reference",
      "compilation error",
      "compilereleasekotlin",
      "compiledebugkotlin",
      "type mismatch",
      "manifest merger failed",
      "resource linking failed"
    )
  ) {
    return {
      category: "user-code",
      code: "COMPILE_ERROR",
      retryable: false
    };
  }

  if (
    has(
      "gradle",
      "task failed",
      "build failed",
      "failure: build failed"
    )
  ) {
    return {
      category: "gradle",
      code: "GRADLE_ERROR",
      retryable: false
    };
  }

  return {
    category: "infrastructure",
    code: "BUILD_ERROR",
    retryable: false
  };
}

import * as Sentry from "@sentry/node";

const dsn =
  String(process.env.SENTRY_DSN || "")
    .trim();

const enabled = Boolean(dsn);

function sampleRate() {
  const raw = Number(
    process.env.SENTRY_TRACES_SAMPLE_RATE ||
    0.1
  );

  if (!Number.isFinite(raw)) {
    return 0.1;
  }

  return Math.max(
    0,
    Math.min(1, raw)
  );
}

function scrubEvent(event) {
  if (event?.request?.headers) {
    const headers = {
      ...event.request.headers
    };

    for (const name of [
      "authorization",
      "cookie",
      "x-api-key",
      "x-appforge-api-key"
    ]) {
      if (name in headers) {
        headers[name] = "[REDACTED]";
      }
    }

    event.request.headers = headers;
  }

  if (event?.request?.data) {
    event.request.data = "[REDACTED]";
  }

  return event;
}

if (enabled) {
  Sentry.init({
    dsn,
    environment:
      String(
        process.env.SENTRY_ENVIRONMENT ||
        process.env.NODE_ENV ||
        "development"
      ),
    release:
      String(
        process.env.SENTRY_RELEASE ||
        process.env.RAILWAY_GIT_COMMIT_SHA ||
        ""
      ) || undefined,
    tracesSampleRate: sampleRate(),
    sendDefaultPii: false,
    beforeSend: scrubEvent,
    registerEsmLoaderHooks: {
      onlyIncludeInstrumentedModules: true
    }
  });
}

process.on(
  "unhandledRejection",
  reason => {
    if (!enabled) return;

    const error =
      reason instanceof Error
        ? reason
        : new Error(String(reason));

    Sentry.captureException(error);
  }
);

process.on(
  "uncaughtExceptionMonitor",
  error => {
    if (enabled) {
      Sentry.captureException(error);
    }
  }
);

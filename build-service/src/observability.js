import * as Sentry from "@sentry/node";
import { config } from "./config.js";

export function observabilityStatus() {
  return {
    provider: "sentry",
    enabled: Boolean(config.sentryDsn),
    environment:
      config.sentryEnvironment,
    tracesSampleRate:
      config.sentryTracesSampleRate
  };
}

export function captureException(
  error,
  context = {}
) {
  if (!config.sentryDsn) {
    return;
  }

  try {
    Sentry.withScope(scope => {
      for (
        const [name, value] of
        Object.entries(context || {})
      ) {
        if (
          value == null ||
          ["string", "number", "boolean"]
            .includes(typeof value)
        ) {
          scope.setTag(
            name,
            String(value ?? "")
              .slice(0, 200)
          );
        }
      }

      Sentry.captureException(error);
    });
  } catch {}
}

export function setupExpressErrorHandling(
  app
) {
  if (!config.sentryDsn) {
    return;
  }

  Sentry.setupExpressErrorHandler(app);
}

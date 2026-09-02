import { config } from "./config.js";
import { acquireLease } from "./redis.js";

let fallbackNextAllowedAt = 0;

function errorText(error) {
  return String(
    error?.message ||
    error ||
    "unknown error"
  ).slice(0, 500);
}

function repositoryParts() {
  const parts =
    config.autoscaleDispatchRepository
      .split("/")
      .map(x => x.trim())
      .filter(Boolean);

  if (parts.length !== 2) {
    return null;
  }

  return {
    owner: parts[0],
    repo: parts[1]
  };
}

async function reserveDispatchSlot() {
  const distributed =
    await acquireLease(
      "autoscale-dispatch",
      config.autoscaleDispatchRepository,
      config.autoscaleDispatchCooldownSeconds
    );

  /*
   * Redis aktifse tüm API replica'ları aynı cooldown
   * kilidini paylaşır.
   */
  if (distributed !== null) {
    return distributed;
  }

  /*
   * Redis yoksa tek process için güvenli fallback.
   */
  const now = Date.now();

  if (
    now <
    fallbackNextAllowedAt
  ) {
    return false;
  }

  fallbackNextAllowedAt =
    now +
    (
      config.autoscaleDispatchCooldownSeconds *
      1000
    );

  return true;
}

export async function triggerWorkerAutoscale({
  reason = "queue_activity"
} = {}) {
  if (
    !config.autoscaleDispatchEnabled
  ) {
    return {
      dispatched: false,
      reason: "disabled"
    };
  }

  if (
    !config.autoscaleDispatchToken
  ) {
    console.warn(
      "AUTOSCALE_DISPATCH: GITHUB_AUTOSCALE_TOKEN eksik."
    );

    return {
      dispatched: false,
      reason: "token_missing"
    };
  }

  const repository =
    repositoryParts();

  if (!repository) {
    console.warn(
      "AUTOSCALE_DISPATCH: repository biçimi geçersiz."
    );

    return {
      dispatched: false,
      reason: "invalid_repository"
    };
  }

  const reserved =
    await reserveDispatchSlot();

  if (!reserved) {
    return {
      dispatched: false,
      reason: "cooldown"
    };
  }

  const url =
    "https://api.github.com/repos/" +
    encodeURIComponent(repository.owner) +
    "/" +
    encodeURIComponent(repository.repo) +
    "/actions/workflows/" +
    encodeURIComponent(
      config.autoscaleDispatchWorkflow
    ) +
    "/dispatches";

  const controller =
    new AbortController();

  const timeout =
    setTimeout(
      () => controller.abort(),
      10_000
    );

  try {
    const response =
      await fetch(
        url,
        {
          method: "POST",
          headers: {
            Accept:
              "application/vnd.github+json",
            Authorization:
              "Bearer " +
              config.autoscaleDispatchToken,
            "X-GitHub-Api-Version":
              "2026-03-10",
            "User-Agent":
              "AppForge-Build-Service",
            "Content-Type":
              "application/json"
          },
          body: JSON.stringify({
            ref:
              config.autoscaleDispatchRef,
            inputs: {
              min_replicas: "3"
            }
          }),
          signal:
            controller.signal
        }
      );

    const body =
      await response.text();

    if (!response.ok) {
      console.warn(
        "AUTOSCALE_DISPATCH: GitHub HTTP " +
        response.status +
        " " +
        body.slice(0, 300)
      );

      return {
        dispatched: false,
        reason: "http_error",
        status: response.status
      };
    }

    console.log(
      "AUTOSCALE_DISPATCH: workflow tetiklendi (" +
      reason +
      ")."
    );

    return {
      dispatched: true,
      status: response.status
    };
  } catch (error) {
    console.warn(
      "AUTOSCALE_DISPATCH: " +
      errorText(error)
    );

    return {
      dispatched: false,
      reason: "request_error"
    };
  } finally {
    clearTimeout(timeout);
  }
}

import { config } from "./config.js";
import {
  fixedWindowLimit
} from "./redis.js";

const localBuckets = new Map();
const localPurchaseBuckets = new Map();
const WINDOW_MS = 60 * 60 * 1000;
const WINDOW_SECONDS = 60 * 60;

function localLimit(
  buckets,
  key,
  limit
) {
  const now = Date.now();
  const recent =
    (buckets.get(key) || [])
      .filter(ts =>
        now - ts < WINDOW_MS
      );

  const allowed =
    recent.length < limit;

  if (allowed) {
    recent.push(now);
    buckets.set(key, recent);
  }

  const oldest = recent[0] || now;

  return {
    distributed: false,
    allowed,
    count:
      recent.length +
      (allowed ? 0 : 1),
    limit,
    remaining:
      Math.max(
        0,
        limit - recent.length
      ),
    resetSeconds:
      Math.max(
        0,
        Math.ceil(
          (WINDOW_MS - (now - oldest)) /
          1000
        )
      )
  };
}

function applyHeaders(
  res,
  result
) {
  res.set(
    "RateLimit-Limit",
    String(result.limit)
  );

  res.set(
    "RateLimit-Remaining",
    String(result.remaining)
  );

  res.set(
    "RateLimit-Reset",
    String(result.resetSeconds)
  );

  res.set(
    "X-AppForge-RateLimit-Backend",
    result.distributed
      ? "redis"
      : "memory-fallback"
  );
}

async function resolveLimit({
  namespace,
  identity,
  limit,
  fallbackBuckets
}) {
  const distributed =
    await fixedWindowLimit(
      namespace,
      identity,
      limit,
      WINDOW_SECONDS
    );

  if (distributed) {
    return distributed;
  }

  return localLimit(
    fallbackBuckets,
    identity,
    limit
  );
}

export async function buildRateLimit(
  req,
  res,
  next
) {
  try {
    /*
     * Admin production/yük testleri normal kullanıcı
     * saatlik build kotasına tabi değildir.
     * Global queue ve worker korumaları devam eder.
     */
    if (
      req.user?.role ===
      "admin"
    ) {
      res.set(
        "X-AppForge-RateLimit-Bypass",
        "admin"
      );

      return next();
    }

    const identity =
      req.user?.id ||
      req.ip ||
      "anonymous";

    const result =
      await resolveLimit({
        namespace: "build",
        identity,
        limit:
          config.rateLimitPerHour,
        fallbackBuckets:
          localBuckets
      });

    applyHeaders(res, result);

    if (!result.allowed) {
      return res
        .status(429)
        .json({
          error:
            "Saatlik build limiti aşıldı.",
          limit:
            config.rateLimitPerHour,
          retryAfterSeconds:
            result.resetSeconds
        });
    }

    next();
  } catch (error) {
    next(error);
  }
}

export async function purchaseVerifyRateLimit(
  req,
  res,
  next
) {
  try {
    const identity =
      req.ip ||
      "anonymous";

    const result =
      await resolveLimit({
        namespace:
          "purchase-verify",
        identity,
        limit:
          config.playVerifyRatePerHour,
        fallbackBuckets:
          localPurchaseBuckets
      });

    applyHeaders(res, result);

    if (!result.allowed) {
      return res
        .status(429)
        .json({
          ok: false,
          error:
            "Purchase verification rate limiti aşıldı.",
          retryAfterSeconds:
            result.resetSeconds
        });
    }

    next();
  } catch (error) {
    next(error);
  }
}

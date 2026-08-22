import { config } from "./config.js";

const buckets = new Map();

export function buildRateLimit(req, res, next) {
  const key = req.user?.id || req.ip || "anonymous";
  const now = Date.now();
  const hour = 60 * 60 * 1000;

  const recent = (buckets.get(key) || []).filter(ts => now - ts < hour);

  if (recent.length >= config.rateLimitPerHour) {
    return res.status(429).json({
      error: "Saatlik build limiti aşıldı.",
      limit: config.rateLimitPerHour
    });
  }

  recent.push(now);
  buckets.set(key, recent);
  next();
}


const purchaseBuckets = new Map();

export function purchaseVerifyRateLimit(
  req,
  res,
  next
) {
  const key =
    req.ip ||
    "anonymous";

  const now =
    Date.now();

  const hour =
    60 * 60 * 1000;

  const recent =
    (
      purchaseBuckets.get(
        key
      ) || []
    ).filter(
      ts =>
        now - ts < hour
    );

  if (
    recent.length >=
    config.playVerifyRatePerHour
  ) {
    return res
      .status(429)
      .json({
        ok: false,
        error:
          "Purchase verification rate limiti aşıldı."
      });
  }

  recent.push(now);

  purchaseBuckets.set(
    key,
    recent
  );

  next();
}

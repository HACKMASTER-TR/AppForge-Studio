import { createClient } from "redis";
import { config } from "./config.js";

let client = null;
let connectPromise = null;
let lastError = null;
const blockingClients = new Map();

function errorText(error) {
  return String(
    error?.message ||
    error ||
    "unknown redis error"
  ).slice(0, 500);
}

function makeClient() {
  const instance = createClient({
    url: config.redisUrl,
    disableOfflineQueue: true,
    socket: {
      connectTimeout:
        config.redisConnectTimeoutMs,
      reconnectStrategy(retries) {
        if (retries >= 5) {
          return new Error(
            "Redis reconnect deneme sınırı aşıldı."
          );
        }

        return Math.min(
          1000,
          100 * Math.pow(2, retries)
        );
      }
    }
  });

  instance.on("error", error => {
    lastError = errorText(error);
  });

  instance.on("ready", () => {
    lastError = null;
  });

  return instance;
}

async function getClient() {
  if (!config.redisUrl) {
    return null;
  }

  if (client?.isReady) {
    return client;
  }

  if (!connectPromise) {
    client = makeClient();

    connectPromise =
      client
        .connect()
        .then(() => client)
        .catch(error => {
          lastError = errorText(error);
          connectPromise = null;
          client = null;
          throw error;
        });
  }

  return connectPromise;
}

async function bestEffort(fn, fallback = null) {
  if (!config.redisUrl) {
    return fallback;
  }

  try {
    const active = await getClient();
    if (!active) return fallback;
    return await fn(active);
  } catch (error) {
    lastError = errorText(error);
    return fallback;
  }
}

function key(...parts) {
  return [
    config.redisPrefix,
    ...parts
      .map(value => String(value || ""))
      .map(value =>
        value.replace(/[^a-zA-Z0-9:_-]/g, "_")
      )
  ].join(":");
}

const FIXED_WINDOW_SCRIPT = `
local current = redis.call('INCR', KEYS[1])
if current == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
local ttl = redis.call('TTL', KEYS[1])
return { current, ttl }
`;

export async function fixedWindowLimit(
  namespace,
  identity,
  limit,
  windowSeconds
) {
  const result = await bestEffort(
    async active => {
      const bucket =
        Math.floor(
          Date.now() /
          (windowSeconds * 1000)
        );

      const redisKey = key(
        "rate",
        namespace,
        identity,
        bucket
      );

      const raw = await active.eval(
        FIXED_WINDOW_SCRIPT,
        {
          keys: [redisKey],
          arguments: [
            String(windowSeconds)
          ]
        }
      );

      const count = Number(raw?.[0] || 0);
      const ttl = Math.max(
        0,
        Number(raw?.[1] || 0)
      );

      return {
        distributed: true,
        allowed: count <= limit,
        count,
        limit,
        remaining:
          Math.max(0, limit - count),
        resetSeconds: ttl
      };
    },
    null
  );

  return result;
}

export async function cacheGetJson(
  namespace,
  cacheKey
) {
  return bestEffort(
    async active => {
      const raw =
        await active.get(
          key("cache", namespace, cacheKey)
        );

      if (!raw) return null;

      try {
        return JSON.parse(raw);
      } catch {
        return null;
      }
    },
    null
  );
}

export async function cacheSetJson(
  namespace,
  cacheKey,
  value,
  ttlSeconds
) {
  return bestEffort(
    async active => {
      await active.set(
        key("cache", namespace, cacheKey),
        JSON.stringify(value),
        {
          EX: Math.max(
            1,
            Math.floor(ttlSeconds)
          )
        }
      );
      return true;
    },
    false
  );
}

export async function acquireLease(
  namespace,
  identity,
  ttlSeconds
) {
  return bestEffort(
    async active => {
      const result =
        await active.set(
          key(
            "lease",
            namespace,
            identity
          ),
          String(Date.now()),
          {
            NX: true,
            EX: Math.max(
              1,
              Math.floor(ttlSeconds)
            )
          }
        );

      return result === "OK";
    },
    null
  );
}

function queueSignalKey() {
  return key("queue", "build-wakeup");
}

export async function signalBuildQueue(
  count = 1
) {
  return bestEffort(
    async active => {
      const amount = Math.max(
        1,
        Math.min(25, Number(count) || 1)
      );

      const values = Array.from(
        { length: amount },
        () => String(Date.now())
      );

      await active.lPush(
        queueSignalKey(),
        values
      );

      await active.lTrim(
        queueSignalKey(),
        0,
        999
      );

      return true;
    },
    false
  );
}

async function blocker(workerKey) {
  if (!config.redisUrl) {
    return null;
  }

  const existing =
    blockingClients.get(workerKey);

  if (existing?.isReady) {
    return existing;
  }

  try {
    const active = await getClient();
    if (!active) return null;

    const duplicate = active.duplicate();

    duplicate.on("error", error => {
      lastError = errorText(error);
    });

    await duplicate.connect();
    blockingClients.set(workerKey, duplicate);
    return duplicate;
  } catch (error) {
    lastError = errorText(error);
    return null;
  }
}

export async function waitForBuildSignal(
  workerKey,
  timeoutMs
) {
  const active =
    await blocker(
      String(workerKey || "worker")
    );

  if (!active) {
    await new Promise(resolve =>
      setTimeout(resolve, timeoutMs)
    );
    return false;
  }

  try {
    const result = await active.blPop(
      queueSignalKey(),
      Math.max(
        1,
        Math.ceil(timeoutMs / 1000)
      )
    );

    return Boolean(result);
  } catch (error) {
    lastError = errorText(error);
    blockingClients.delete(
      String(workerKey || "worker")
    );

    await new Promise(resolve =>
      setTimeout(resolve, timeoutMs)
    );

    return false;
  }
}

export async function redisHealth() {
  if (!config.redisUrl) {
    return {
      configured: false,
      required: config.redisRequired,
      ok: !config.redisRequired,
      error:
        config.redisRequired
          ? "REDIS_URL tanımlı değil."
          : null
    };
  }

  const started = Date.now();

  try {
    const active = await getClient();
    await active.ping();

    return {
      configured: true,
      required: config.redisRequired,
      ok: true,
      latencyMs:
        Date.now() - started,
      error: null
    };
  } catch (error) {
    lastError = errorText(error);

    return {
      configured: true,
      required: config.redisRequired,
      ok: false,
      latencyMs:
        Date.now() - started,
      error: lastError
    };
  }
}

export function redisStatus() {
  return {
    configured:
      Boolean(config.redisUrl),
    required:
      config.redisRequired,
    ready:
      Boolean(client?.isReady),
    lastError
  };
}

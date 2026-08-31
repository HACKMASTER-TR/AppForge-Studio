import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../", import.meta.url);

async function text(path) {
  return readFile(
    new URL(path, root),
    "utf8"
  );
}

test("production infrastructure is wired", async () => {
  const [
    pkg,
    config,
    redis,
    rate,
    queue,
    worker,
    cache,
    storage,
    mail,
    server,
    compose,
    env,
    instrument
  ] = await Promise.all([
    text("package.json"),
    text("src/config.js"),
    text("src/redis.js"),
    text("src/rateLimit.js"),
    text("src/jobQueue.js"),
    text("src/workerRuntime.js"),
    text("src/buildCache.js"),
    text("src/storage.js"),
    text("src/mail.js"),
    text("server.js"),
    text("docker-compose.yml"),
    text(".env.example"),
    text("instrument.mjs")
  ]);

  assert.match(pkg, /"redis"\s*:\s*"6\.2\.1"/);
  assert.match(pkg, /"@sentry\/node"\s*:\s*"10\.71\.0"/);
  assert.match(pkg, /--import \.\/instrument\.mjs/);

  assert.match(config, /redisUrl/);
  assert.match(config, /redisRequired/);
  assert.match(config, /smtpRequired/);
  assert.match(config, /sentryDsn/);

  assert.match(redis, /FIXED_WINDOW_SCRIPT/);
  assert.match(redis, /signalBuildQueue/);
  assert.match(redis, /waitForBuildSignal/);
  assert.match(redis, /cacheGetJson/);

  assert.match(rate, /fixedWindowLimit/);
  assert.match(rate, /memory-fallback/);
  assert.match(queue, /signalBuildQueue/);
  assert.match(worker, /waitForBuildSignal/);
  assert.match(cache, /cacheGetJson/);
  assert.match(cache, /cacheSetJson/);

  assert.match(storage, /HeadBucketCommand/);
  assert.match(storage, /verifyStorageConnection/);
  assert.match(mail, /verifyMailTransport/);

  assert.match(server, /"\/ready"/);
  assert.match(server, /redisHealth/);
  assert.match(server, /observabilityStatus/);
  assert.match(server, /setupExpressErrorHandling/);
  assert.match(server, /sourceBuildIsolation:/);
  assert.match(server, /config\.sourceBuildIsolationMode/);
  assert.match(server, /config\.sourceBuildRequireIsolation/);
  assert.match(server, /config\.sourceBuildIsolationCapability/);

  assert.match(compose, /redis:8-alpine/);
  assert.match(compose, /minio-init:/);
  assert.match(compose, /mailpit:/);

  assert.match(env, /REDIS_URL=/);
  assert.match(env, /SENTRY_DSN=/);
  assert.match(env, /SMTP_REQUIRED=/);
  assert.match(instrument, /Sentry\.init/);
  assert.match(instrument, /sendDefaultPii: false/);
});

# V9 Cloudflare staging: preflight and deployment boundary

Scope: isolated, **offline-safe preflight only** on dev branch `feat/v9-global-package-distribution-20261027`. No modifications to `main`, Android APK, R2, Play Production or existing Cloudflare Workers.

## Why a CI preflight first

Wrangler relies on native `workerd`, and direct use in Android Termux is not supported. `v9-cloudflare-staging-preflight.yml` compiles the Worker using Wrangler 4.153.0 on an Ubuntu GitHub runner, `--dry-run`, with **no Cloudflare credentials** and no deployment. The CI job validates that the staging Worker has `workers_dev=false`, `preview_urls=false`, no routes and no R2 bindings. It verifies real JavaScript witness tests and its own fail-closed preflight tests.

The isolated staging Worker name is `appforge-v9-witness-isolated-staging`, with a SQLite Durable Object, with mode `DISABLED`. It uses no production signers and its exported HTTP handler returns 503. **This configuration does not grant public or authenticated RPC access**. `workers_dev=false` alone does not guarantee URL privacy when an existing Worker has version URLs; `preview_urls=false` makes the policy explicit.

## Later live staging (separate explicit approval)

Only after green preflight and Cloudflare account ownership verification, review a *separate* manual deployment of this disabled-only staging configuration using an authenticated Linux environment and `wrangler deploy --config cloudflare/v9-witness/wrangler.staging.toml`. Do not use Railway, Render, R2, public routes, `--temporary`, or main/Play. No Cloudflare tokens in chat, in source, or in logs. Check account ID carefully. This first deployment, if approved, only provisions a **disabled, nonpublic** DO Worker, without exercising real release acceptance. An authenticated canary test interface and recovery tests require separate security implementation.

**Not production-ready**. Still missing authenticated enrollment, key protection, independent trusted high-water, signed fresh receipts, Android debug client, two-device physical acceptance and external review. SQLite DO PITR is not a non-rollbackable anchor against administrative restore.

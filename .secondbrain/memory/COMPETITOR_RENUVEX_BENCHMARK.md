# AppForge vs Renuvex Engineering Benchmark

Status: ACTIVE
Priority: HIGH
Updated: 2026-09-13

## Permanent Objective

AppForge Studio must become better than Renuvex Product Reviews not only
in feature count and platform scope, but also in engineering quality.

AppForge must exceed or equal Renuvex in:

- architecture
- maintainability
- testing
- real-user E2E coverage
- CI/CD
- security
- performance
- documentation
- project memory
- repository hygiene
- production reliability

## Current Directional Benchmark

Engineering / production discipline:

- Renuvex Product Reviews: ~92/100
- AppForge Studio: ~88/100

Platform capability / technical scope:

- AppForge Studio: ~96/100
- Renuvex Product Reviews: ~82/100

These are directional engineering benchmarks, not scientific measurements.

## AppForge Advantages

AppForge already has the broader technical platform:

- Android, Web and Windows generation
- Android APK/AAB build infrastructure
- multiple build workers
- Source Worker
- AppForge Terminal
- multi-session terminal
- Files
- Git
- SSH
- GitHub/Railway connections
- Preview and Test Lab
- APK/AAB analysis
- local AI
- SecondBrain
- server-authoritative Pro/Admin/quota controls
- immutable worker images
- production automation and rollback
- pipeline timing

The goal is to preserve this scope while raising engineering discipline.

## Areas To Exceed Renuvex

### 1. Architecture + ADR

Maintain clear and human-readable:

- Architecture maps
- ADR / Decisions
- Bugs and fixes
- Research
- Competitor analysis
- Hot context
- migration plans
- rollback plans
- verification evidence

Major architecture changes must explain:

- problem
- decision
- alternatives
- risks
- migration
- rollback
- test evidence

### 2. Real User E2E Tests

Expand focused E2E coverage for:

- Android real-device flows
- Web flows
- Free / Pro boundaries
- Admin boundaries
- Owner boundaries
- Billing
- purchase restore
- subscription management
- forced update
- Terminal
- Files
- Git
- SSH
- Connections
- AI
- build submission
- Worker processing
- generated artifact
- production health

Prefer focused scenario suites instead of oversized generic smoke tests.

### 3. Performance Budgets

Measure and eventually gate:

- app startup
- navigation latency
- Compose recomposition
- Terminal keyboard latency
- Terminal output latency
- large log rendering
- Files listing/search
- Git status
- worker image build time
- pipeline wall-clock time
- APK/AAB size where appropriate
- memory use
- CPU use

Never invent performance results.
Store real measured evidence.

Current CI benchmark:

- Source Worker V2: 21:40
- Source Worker V3: 14:12
- V3 improvement: ~34.5%
- Full Pipeline V2: 27:08
- Full Pipeline V3: 19:22
- Full Pipeline improvement: ~28.6%

Continue optimization without removing security or regression gates.

### 4. Generated Artifact Drift Protection

CI should detect stale generated outputs including:

- manifests
- generated configs
- API contracts
- runtime bundles
- release metadata
- build metadata
- generated source where applicable

Generated output must match its source.

### 5. Repository Hygiene

AppForge should be stricter than Renuvex.

Permanent direction:

- no APK files
- no AAB files
- no dashboard.sh
- no dashboard.sh.bak
- no obsolete .bak files
- no temporary reports
- no debug dumps
- no secrets
- no tokens
- no accidental generated junk
- no local backup copies in production paths

Forbidden files should hard-fail validation.

### 6. Maintainability / Modularization

Large modules must continue to be decomposed.

Priority domains:

- MainActivity / Compose UI
- Terminal
- Files
- Git
- SSH
- Connections
- Build
- AI
- Billing
- Admin
- Owner

Keep business logic away from oversized UI classes.

### 7. Security

Never regress:

- Pro != Admin
- Admin != Pro
- server-authoritative Pro status
- server-authoritative subscriptions
- server-authoritative quotas
- server-authoritative admin roles
- Google Play purchase verification
- replay protection
- refund/cancel/revoke synchronization
- Play Integrity
- release signature validation
- forced update enforcement
- no client-side privilege bypass
- no token/secret/internal-path leakage
- account isolation
- Terminal isolation
- Worker isolation

### 8. CI/CD

Preserve and improve:

- fail 0
- regression-first bug fixes
- hard stability gate
- immutable releases/images
- production health checks
- automatic rollback
- workflow timing
- full-pipeline wall-clock timing
- targeted CI where safe
- full CI for high-risk changes

CI speed must never be improved by deleting meaningful safety checks.

## Permanent Benchmark Question

For every important AppForge change ask:

"Does this make AppForge not only more capable than Renuvex,
but also at least as maintainable, testable, secure,
performant and production-safe?"

If the answer is no, the engineering work is incomplete.

## Priority Roadmap

1. Finish Source Worker and full-pipeline performance optimization.
2. Strengthen Architecture + ADR organization.
3. Add stronger real-user E2E suites.
4. Introduce measurable performance budgets.
5. Add generated-artifact drift gates.
6. Remove obsolete tracked backup files.
7. Continue Android / MainActivity modularization.
8. Improve repository hygiene enforcement.
9. Periodically re-audit Renuvex.
10. Implement stronger versions of useful competitor ideas.

## Final Target

AppForge should become:

- more capable than Renuvex
- at least as maintainable
- more extensively tested
- at least as secure
- faster to ship safely
- easier to understand
- easier to operate
- better documented
- stronger in production

The objective is not feature count.

The objective is the stronger engineering product.

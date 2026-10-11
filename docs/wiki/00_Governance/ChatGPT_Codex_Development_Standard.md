---
type: maintenance
status: active
project: AppForge Studio
created: 2026-10-08
updated: 2026-10-08
last_verified: 2026-10-08
confidence: high
tags:
  - governance
  - development
  - codex
related:
  - "[[Standing_Delivery_Authorization]]"
  - "[[Agent_Rules]]"
  - "[[New_Session_Start_Prompt]]"
source_files:
  - "AGENTS.md"
  - "scripts/appforge"
  - "scripts/generate-second-brain-snapshot.py"
  - "scripts/wiki-audit.mjs"
  - "scripts/wiki-secret-scan.py"
  - "quality/tests/second_brain_snapshot_v2_contract.test.js"
---

# ChatGPT + Codex Development Standard

## Approved default and authority

This is the canonical owner-approved default AppForge development workflow.
It supplements existing protected-system controls; it does not replace them.
The values below describe development governance, not application configuration.

```text
DEFAULT_APPFORGE_DEV_FLOW=ChatGPT + Codex CLI
CODEX_AUTH=ChatGPT account
CODEX_ENV=Ubuntu PRoot
WORK_MODE=isolated feature worktree
MAIN_DIRECT_EDIT=NO
AUTO_PUSH=NO
AUTO_MERGE=NO
AUTO_DEPLOY=NO
PLAY_PRODUCTION=NO
AUTO_RELEASE=NO
CODEX_RESULT_ALONE_IS_NOT_PASS=YES
EXTERNAL_GATE_REQUIRED=YES
EXACT_SHA_EVIDENCE_REQUIRED=YES
```

## Responsibilities and handoff

ChatGPT is the planning, verification and orchestration layer. It clarifies the
requested change, establishes scope, chooses an isolated branch/worktree strategy,
defines exact base branch/SHA guards, prepares Codex implementation or audit
prompts, and names protected systems and explicit do-not-touch boundaries.
It inspects Codex output and independently validates diffs, tests, Second Brain
freshness and exact-SHA CI where applicable. It distinguishes local success from
hosted CI and nonphysical acceptance from physical acceptance, then makes the
final PASS/FAIL decision from evidence.

Codex CLI is the repository discovery and implementation agent. Its scoped tasks
may include repository discovery, source inspection, implementation, focused
refactors, test execution, controlled repairs, documentation updates, and local
commits only when explicitly permitted by the task. Codex works only within the
isolated worktree supplied for that task.

The default handoff is:

1. ChatGPT: scope, guards and task prompt.
2. Codex CLI: discovery, implementation and local evidence.
3. ChatGPT or an independent gate: diff, tests, Second Brain, exact-SHA CI and acceptance.
4. Human authorization: merge, release and production actions when applicable.

Codex has no blanket authority over main, merges, pushes, production deployment,
Play Production, release creation, protected Cloudflare production resources,
secrets or destructive repository operations. Implementation success does not
authorize a push. A current task may explicitly authorize a feature-branch push
for hosted CI evidence; this remains separate from merge authorization.
[[Standing_Delivery_Authorization]] records historical delivery scope, but does
not grant Codex blanket authority or override current task restrictions. Full
delivery requires an explicit task request under the existing fail-stop rules.

## Worktree and Git guards

Before implementation, establish the exact target branch and base branch/SHA;
verify the supplied worktree branch, HEAD and cleanliness against those guards.
Normal development uses isolated feature worktrees. Direct edits to main are
prohibited. No automatic push, merge, production deployment, Play Production
action or release follows a local PASS. Do not force push unless the specific
task requires it and its safety is separately proven.

## Development environment and authentication

The current preferred environment is Ubuntu PRoot inside Termux. Codex normally
resides at `/root/.local/bin/codex`; an isolated AppForge worktree may be bound to
`/workspace`. This is an implementation tool, not AppForge runtime architecture.

When Codex sandbox isolation is incompatible with Termux/PRoot, the established
local workaround may use `--ask-for-approval never --sandbox danger-full-access`
only when all of these conditions hold:

- Codex runs inside the controlled Ubuntu PRoot development environment.
- Only the isolated task worktree is supplied as the repository workspace.
- The task prompt explicitly defines scope and protected-system boundaries.
- External validation follows.
- AppForge's in-product agent authority architecture is not weakened.

These CLI flags are a local development-environment workaround, not an AppForge
application security policy.

Codex authenticates with the user's ChatGPT account through the supported Codex
login flow. Do not put OpenAI API keys into AppForge for this workflow. Codex CLI
authentication and AppForge AI provider architecture are separate systems.
At the beginning of a new development chat/session, prefer checking existing
authentication before asking for login again:

```sh
proot-distro login ubuntu -- bash -lc \
'export PATH="$HOME/.local/bin:$PATH"; codex login status'
```

Reuse valid authentication; if invalid, require a fresh supported login. Runtime
status is authoritative. There is no invented fixed session lifetime such as
7 or 30 days; validity and refresh follow the Codex/OpenAI authentication
mechanism. Never store device-auth codes, tokens, cookies, secrets or credential
material in Second Brain, logs, repository files, screenshots, prompts or
clipboard automation.

## Independent evidence and stage reporting

Codex statements such as PASS, DONE, IMPLEMENTED, TESTS PASS or READY are not
sufficient evidence alone. Only stages actually executed and independently
evidenced may be marked PASS:

| Stage | Evidence required when applicable |
|---|---|
| IMPLEMENTED | Independently inspect changed scope and diff; validate any commit's exact SHA and diff. |
| TARGETED_TESTS | Independently rerun tests or inspect applicable execution evidence. |
| FULL_QUALITY | Evidence from the full applicable quality suite. |
| SECOND_BRAIN | Canonical generation and real freshness check against current source. |
| ANDROID_BUILD | Actual Android/JVM/build evidence; tests alone do not establish a build pass. |
| HOSTED_CI | Hosted run and result belonging to the exact expected commit SHA. |
| PHYSICAL_RUNTIME | Actual physical acceptance evidence; nonphysical gates are insufficient. |

Report stages not executed as NOT_RUN or NOT_APPLICABLE with a reason. Local
tests do not establish hosted CI success; hosted CI does not establish physical
runtime acceptance. No fake success. Infrastructure failures remain failures;
if a retry succeeds, retain the first failure's infrastructure classification
and the successful rerun, including their exact-SHA association.

Relevant evidence includes branch, base SHA, final SHA, worktree cleanliness,
changed-file scope, `git diff --check`, targeted tests, full quality, Second Brain
generation/check, secret scan, applicable Android/JVM/build evidence, hosted
exact-SHA CI, and physical runtime acceptance. A final gate reports only the
acceptance actually established, with remaining external stages explicit.

## Existing protections and Second Brain

Existing protections remain authoritative for main, Play Production, production
release paths, appforge-failover, D1 migrations, signing/publisher authority,
`.appforge` data, `~/.appforge-logs`, Terminal Linux and Device Build Runtime.
This development standard grants no authority to weaken those controls.

Update canonical wiki source, then use the existing evidence pipeline:

```sh
python3 scripts/generate-second-brain-snapshot.py
python3 scripts/generate-second-brain-snapshot.py --check
node scripts/wiki-audit.mjs
python3 scripts/wiki-secret-scan.py
```

The generated V2 snapshot reflects this policy through its source-basis hash and
coverage counts over wiki evidence. It is not a policy-text export or a live CI
query. Do not hand-edit generated snapshot content or create a duplicate policy
system. Future chats recover this standard through [[Index]], [[Agent_Rules]]
and [[New_Session_Start_Prompt]] instead of relying on chat memory.

## Second Brain V2 integrity basis policy

`SECOND_BRAIN_V2_BASIS_POLICY_V1` defines `sourceBasisSha256` as a
selected/versioned repository coverage attestation, not total project integrity.
The canonical inventory preserves the generator's allowed evidence prefixes and
extensions, selecting Git-tracked and nonignored untracked regular files.
Every immediate regular `cloudflare/control-plane/migrations/*.sql` file is
additionally included even if ignored, because that same inventory drives exact
expected migration-set validation. Unexpected SQL files remain fail closed;
this does not validate SQL semantics or reconcile live D1.

The digest commits to the policy identifier, sorted repository-relative POSIX
paths and file bytes. `sourceBasisFileCount` counts exactly those files;
wiki, quality-contract and Android unit-test counts derive from that inventory.
The generated snapshot excludes itself. `--check` separately verifies the exact
generated rendering, including fields beyond the digest.

Arbitrary files outside selected prefixes/extensions, AGENTS.md, `.appforge`
operational state, secrets/credentials, local logs, build artifacts, Git-local
or global configuration, device state and live GitHub/Cloudflare/D1/production
state remain intentionally outside this attestation unless already selected by
the canonical policy. Their exclusion does not diminish their authority.
No live, device or hosted acceptance is inferred from the basis hash.

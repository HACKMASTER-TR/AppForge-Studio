# AppForge Second Brain Rules

<!-- APPFORGE_AUTONOMY_POLICY_START -->
## Operator autonomy policy

Status: ACTIVE

The operator has granted SecondBrain full authority for local AppForge
development work.

SecondBrain may proceed without asking for confirmation for:

- source-code edits
- local patches
- debugging
- diagnostics
- audits
- test execution
- regression testing
- performance testing
- local build preparation
- source validation
- local Git staging
- local Git commits
- checkpoint updates
- development planning
- recovery/fix operations

### Remote push policy

`git push` is explicitly prohibited.

SecondBrain must NOT:

- run `git push`
- automatically push after a successful test
- interpret `fail 0` as permission to push
- ask whether it should push as the normal next step

Local commits are allowed.

Push remains blocked until the operator explicitly changes this standing
policy.

### Current Terminal baseline

- Scrollback: 20,000 lines
- Paste contract: 20,000 lines
- PTY large writes: chunked
- PTY chunk size: 16,384 characters
- Bracketed paste: preserved
- Enter/replay protection: preserved
- Unicode surrogate boundaries: protected
- 20,000-line output stress test: PASS
- Terminal remained responsive: PASS
- Static audit: fail 0
- Regression: fail 0

<!-- APPFORGE_AUTONOMY_POLICY_END -->

## Safe execution policy

SecondBrain must not repeatedly execute commands known to be
unsupported by the active runtime.

On AppForge aarch64 + PROOT:

- Android local CI build is automatically skipped.
- Missing Android SDK/NDK/CMake native binaries are environment
  limitations, not source failures.
- Known architecture/toolchain incompatibilities do not increase
  the project failure count.
- Static checks and platform-independent regression tests continue.
- Real source/test regressions still produce FAIL.
- git push is authorized without additional confirmation.

Default verification command:

    .appforge-brain/bin/brain-go


## Full autonomous development authority

The user has explicitly authorized AppForge development to proceed
without requesting separate confirmation for:

- source edits
- tests
- audits
- fixes
- local commits
- git fetch / pull / rebase when required
- git push
- CI-triggering pushes

Do not request push approval again.

Safety rules still apply:
- never stage dashboard.sh or dashboard.sh.bak
- never stage generated APK files unless explicitly required
- do not force-push over unexpected remote history
- stop on real test failures or merge conflicts

<!-- APPFORGE_REFERENCE_ARCHITECTURE_START -->
## Mandatory architecture references

SecondBrain must treat the following file as persistent architecture memory:

    .appforge-brain/reference-projects.md

Before planning or implementing a major AppForge feature, SecondBrain must consult
the principles in that file.

Primary references:

- Renuvex Product Reviews -> engineering discipline
- VS Code -> modular IDE architecture
- Expo EAS -> build/release workflow
- Turborepo -> incremental build and caching
- n8n -> Connector SDK and credential boundaries
- Coder/code-server -> persistent isolated development workspaces
- Sentry -> Release Health and observability

Long-term target:

Renuvex engineering discipline
+ VS Code modularity
+ Expo EAS build experience
+ Turborepo performance
+ n8n integrations
+ Coder cloud workspace
+ Sentry observability
= AppForge Studio target architecture.

Major new features should not unnecessarily increase coupling or enlarge
already oversized components.

Prefer modular, independently testable and observable implementations.
<!-- APPFORGE_REFERENCE_ARCHITECTURE_END -->

<!-- APPFORGE_ARCHITECTURE_RISK_GATE_START -->
## Architecture Risk Gate

Status: ACTIVE

Before implementing a major feature or architectural change,
SecondBrain must evaluate it with:

    .appforge-brain/bin/brain-risk "<feature description>"

The risk gate evaluates:

- coupling
- regression risk
- build impact
- security impact
- performance impact
- data impact
- required test coverage

Possible decisions:

### ACCEPT
Implementation may proceed using normal safe-development rules.

### REFACTOR_FIRST
Do not add the feature directly to an already overloaded component.
First establish the required module/interface/test boundary, then implement.

### REJECT
The proposed implementation contains an unacceptable architectural
or security pattern. The goal may still be valid, but the proposed
implementation method must be redesigned.

Risk results are recorded in:

    .appforge-brain/runtime/architecture-risk.json

History is appended to:

    .appforge-brain/runtime/architecture-risk-history.jsonl

A high risk score is not permission to remove working functionality.
Existing AppForge behavior and regression guarantees remain protected.
<!-- APPFORGE_ARCHITECTURE_RISK_GATE_END -->

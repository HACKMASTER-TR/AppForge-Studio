# Architecture Decisions

Durable architecture decisions go here.

For every important decision record:
- Date
- Decision
- Reason
- Alternatives
- Consequences
- Source files
- Tests
## 2026-09-13 — Agent Mode + Design Blueprint V1

Status: MAIN VALIDATED

### Decision
AppForge Studio will evolve toward a combined FireVibe-style visual design workflow and Emergent-style full-stack agent workflow without replacing the existing verified AppForge pipeline.

### Architecture
- Keep UltimateProjectPipeline as the authoritative install -> test -> build -> deploy-gate pipeline.
- Add Agent Mode as an orchestration layer around the existing pipeline instead of creating a second competing pipeline.
- Add Design Blueprint as the shared application design contract.
- Design Blueprint owns shared design tokens, routes, screens, components and actions.
- Initial target platforms: Android/Jetpack Compose, Flutter, React Native and Web.
- Existing Terminal Ultimate, Files, Git, Connections, SSH and Tools behavior is a protected contract.

### Agent Mode safety
- Agent Mode requires explicit user confirmation before execution.
- No blind AI-generated shell commands or unrestricted source modifications.
- Transient install/network failures may receive at most 2 bounded safe retries.
- TEST or BUILD failures requiring source changes stop and create/use the existing masked AI handoff path.
- Repeated or unsafe failures stop instead of looping indefinitely.
- Deployment never starts silently and remains behind the existing explicit deployment confirmation gate.

### Design Blueprint V1
- One prompt can be represented as a structured multi-screen application blueprint.
- All screens share centralized design tokens and navigation routes.
- Blueprint validation rejects duplicate routes, invalid routes, invalid design tokens and invalid screen/action contracts.
- V1 provides the validated foundation; autonomous prompt-to-code generation is a later controlled phase.

### Validation policy
- Main acceptance criterion remains: fail 0.
- Existing tests must not be weakened, skipped or bypassed.
- Agent Mode is not marked VALIDATED until Android unit tests, git diff checks and SecondBrain post-checks pass.
- No commit, push, merge, deploy or publish without explicit user approval.

### Next phase after V1 validation
Prompt -> AI Blueprint -> deterministic consistent multi-screen generation -> pipeline test/build -> bounded error analysis -> constrained patch -> retest -> APK/AAB.

### Source files planned
- AppForgeAgentBlueprint.kt
- UltimateAgentMode.kt
- AppForgeAgentBlueprintTest.kt
- UltimateAgentModeTest.kt
- UltimateProjectPipelinePanel.kt integration

### Tests planned
- Blueprint validation tests
- Duplicate/missing route tests
- Design-token validation
- Transient install retry policy
- Retry-budget enforcement
- Build/test failure AI-handoff policy
- Existing Android regression tests
- SecondBrain post-validation

### Current validation state
- Local AppForge Hard Stability Gate: PASS
- Local acceptance result: fail 0
- git diff --check: PASS
- SecondBrain selftest: PASS
- SecondBrain doctor: PASS
- Android Gradle/JUnit: PASS via GitHub Actions
- Local Termux Gradle still aborts natively with exit 134; authoritative Android validation completed successfully in GitHub Actions.

### CI validation
- GitHub Actions workflow: AppForge Android Debug
- Run ID: 34778077043
- Result: SUCCESS
- Android unit tests: PASS
- Debug APK build: PASS
- Signing verification: PASS
- APK SDK/package verification: PASS
- Local AppForge Hard Stability Gate: PASS / fail 0
- Termux native Gradle exit 134 is an environment limitation, not an application test failure.

### Main validation
- Pull request: #17
- Feature branch after main sync: 303bc345b871e4d6846b585e3cf0535e85def301
- Synced feature Android CI run: 34780105749
- Synced feature Android CI result: SUCCESS
- Main merge commit: 14c7e0898fbd1da9f607a586f340ee67cab3187f
- Main Android Debug CI run: 34780988730
- Main Android Debug CI result: SUCCESS
- Debug APK build: PASS
- APK SDK/package/signature verification: PASS
- Latest APK release step: PASS
- Final V1 state: MAIN VALIDATED

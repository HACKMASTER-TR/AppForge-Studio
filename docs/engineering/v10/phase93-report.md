# V10 Phase 9.3 — promoted PyO3 0.29.0

Date: 2026-10-29. Implementation promoted; post-promotion host acceptance
**NOT_RUN**. This record supersedes current-state statements in the historical
`phase9-pyo3-report.md`, which is preserved unchanged.

## Identity and provenance

Authorized existing dirty feature worktree: `feat/v10-oss-unified-20261028`,
HEAD/base `bb3eb4d85337455631475cbd7fdbc1f07cbb199a`. Initial tracked changes
were only Cargo.toml and Cargo.lock. All four untracked `.t21-*` directories
were preserved. Neither Cargo file was written by this continuation.

The caller supplied the authentic Cargo-generated lock exported after real
candidate locked build and CPython import/behavior PASS from
[run 40836459198, job 123322262093](https://github.com/HACKMASTER-TR/AppForge-Studio/actions/runs/40836459198/job/123322262093).
This continuation verified the supplied bytes locally, without fetching or
launching GitHub CI. Remote provenance and candidate success are supplied prior
CI evidence, not newly independently retrieved remote evidence. No candidate
result is transferred to the promoted dirty source as a fresh hosted PASS.

- Cargo.lock: 3,391 bytes; SHA-256
  `a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a`.
- Cargo.toml SHA-256:
  `9f8851ff02115b93227ce168e11c6f3f0af7412baecaf5a3374e168d2e7c2609`.
- Exact shipped dependency: `=0.29.0`, retaining `extension-module` and
  `abi3-py312`; all five locked PyO3 family crates are 0.29.0.

## Changes and acceptance boundaries

The host script defaults to shipped mode, copies only manifest/lock/source into
mktemp, forces its target directory inside that fixture, validates exact pin,
features, authentic lock digest/size, root and five-crate family before Cargo.
Linux x86_64, Rust/Cargo 1.85.1 and CPython 3.12.12 are required. Real
`cargo tree --locked` and `cargo build --locked --release` precede actual shared
library import, 1,000 answer()==42 checks and extra-argument TypeError rejection.
The resolved lock is compared byte-for-byte after behavior checks. Temporary
build data is removed on exit. Optional explicit `candidate` mode rechecks this
same promoted pair in isolation; it does not resolve an alternative version.
There is no cargo update, generated replacement lock or lock export path.

The redundant active candidate/export workflow step was removed. Shipped locked
acceptance remains mandatory. Existing hash-pinned actions, read-only contents,
branch guards, credential-free checkout, Android/Hermes and Windows jobs remain
unchanged. No artifacts, delivery, publishing or signing steps were added.
The former export-only contract was replaced with a promotion contract, retaining
host/version/ordering/behavior checks and executing the actual pair validator
against authentic shipped bytes plus corrupted-copy and wrong-pin negatives.
No tests were skipped, disabled or weakened to conceal a failing promotion.

## Local verification

Logs: ignored `.appforge/tmp/v10/phase93/`.

- Targeted workflow and Stability Gate contracts: PASS 10/10, including host
  contracts 5/5 (`contracts.log`).
- V10 Python discovery `test_v10_*.py`: PASS 14/14 (`v10.log`).
- V9 Python discovery `test_v9_*.py`: PASS 440/440 (`v9.log`).
- `npm test --prefix windows-host`: PASS 9/9 Node tests (`windows.log`);
  this is not native Windows execution.
- `bash -n scripts/v10/pyo3-host-acceptance.sh`: PASS.
- Initial `sh scripts/appforge-stability-gate ci`: BLOCKED, fail 1, solely
  SNAPSHOT_OUT_OF_DATE before regeneration (`stability.log`). No bypass applied.
- After source/docs freeze, canonical snapshot generation and `--check`: PASS.
  `sh scripts/appforge-stability-gate ci` after regeneration: PASS, fail 0
  (`stability-after-generation.log`); the initial failure remains recorded.
- Wiki changed-source audit: zero errors, 18 advisory warnings (`wiki-audit.log`).
- `git diff --check`: PASS.

Full quality (1,155+): NOT_RUN. Fresh OSV scan: NOT_RUN. Prior OSV assessment
reported **10 advisory IDs / 4 HIGH**, historical only; no current advisory
count, vulnerability removal or security clearance is inferred from promotion.
No verified PRoot/OSV tool execution was performed in this continuation.
Local Cargo/rustc are unavailable; no compilers/packages were installed.
Post-promotion Linux build/import/behavior, hosted CI, native Windows,
Android/Hermes and physical device acceptance remain NOT_RUN.

No reset, clean, commit, push, merge, signing, deploy, release, main or Play
Production action occurred. No GitHub workflow was attempted.

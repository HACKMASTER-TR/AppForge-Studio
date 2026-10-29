# V10 Phase 9 — pinned PyO3 security migration preparation

Date: 2026-10-29. **PREPARED_NOT_RUN; SHIPPED_MIGRATION_BLOCKED.**

## Resume evidence precedence

This report was already present in the interrupted dirty worktree. The sections
below the resume record are retained prior-session evidence, including their
original full-quality/security claims; they are not fresh validation of this
resume's edits. The current record in this section supersedes their references
to final/current acceptance. No failed evidence or `.t21-*` directory was removed.

Resume verified branch `feat/v10-oss-unified-20261028` and HEAD/base
`a5913836f81fb46ce5461ab0c337b311cf7cb4b7` before edits; all four supplied
modified paths and this untracked report were reviewed and preserved. Initial
worktree was dirty, explicitly authorized by the caller. No reset occurred.

Termux PATH and Ubuntu PRoot PATH/conventional Cargo locations still provide
neither Cargo nor rustc; PRoot Python reports 3.13.13. No supported local
Cargo/Rust/Python host is available, and no packages were installed. Shipped
Cargo.toml, Cargo.lock and lib.rs were independently compared byte-for-byte with
HEAD and remain unchanged: **PyO3 =0.23.5**, candidate **=0.29.0**. No candidate
lock was manufactured, reconstructed, retrieved or applied.

The existing minimal CI log-export approach is retained. Resume adds an explicit
required local-root check to the lock validator (previously the root was checked
only if present), and preserves its contract. Only Cargo-generated lock bytes
can be emitted after locked compile, import, negative-argument test and lock-byte
comparison. No action, action SHA, permission, artifact upload or unrelated
workflow was introduced. Android compile and Windows job text equals HEAD.
Read-only `contents: read`, branch restrictions and credential-free checkout
remain unchanged. Nothing publishes executables, secrets or release artifacts.

The connected GitHub `fetch_workflow_job_logs` read tool was exercised for
baseline Python job 123300029888, run 40829447862. Decoded output contains the
exact baseline SHA and actual compile/import/runtime PASS, but no lock export
marker. This proves availability of the existing decoded-log retrieval route,
not a future export's success or integrity. The previously described strict
length/hash/base64/TOML and run/job/source identity review remains required.
**Lock export PREPARED_NOT_RUN**: a separately authorized future CI run must
resolve, accept and export its actual lock. **Migration BLOCKED** until those
bytes are retrieved and independently accepted. Historical hosted Windows,
Android/Hermes and isolated candidate success does not validate these edits.

Fresh commands executed during resume (logs under ignored
`.appforge/tmp/v10/phase9-resume/`):

- `node --test quality/tests/v10_host_verification_contract.test.js
  quality/tests/appforge_stability_gate_failure_exit_contract.test.js
  quality/tests/appforge_stability_gate_portable_tmp_contract.test.js
  quality/tests/appforge_stability_gate_proot_diff_contract.test.js`: PASS 10/10,
  including all 5 Windows/host workflow contracts (`contracts.log`).
- `python3 -m unittest discover -s quality/tests -p 'test_v9_*.py'`: PASS 440/440
  (`v9.log`). V9 implementation was not edited.
- `python3 -m unittest discover -s quality/tests -p 'test_v10_*.py'`: PASS 14/14
  (`python.log`).
- `bash -n scripts/v10/pyo3-host-acceptance.sh`: PASS. Embedded Python heredocs
  compiled through Python `compile()`: PASS. Actual shipped Cargo.lock passed
  to the export validator: rejected with nonzero exit and no output, as required
  (`validator-negative.log` preserves expected rejection). No fake lock fixture.
- After selected-source edits stopped,
  `python3 scripts/generate-second-brain-snapshot.py`, then `--check`: PASS.
  `node --test quality/tests/second_brain_snapshot_v2_contract.test.js`: PASS
  20/20 (`snapshot.log`).
- `sh scripts/appforge-stability-gate ci`: PASS, fail 0 (`stability.log`).
- `node scripts/wiki-audit.mjs --changed-source-check`: zero errors, 18 advisory
  warnings (`wiki-audit.log`). `git diff --check`: PASS.

**Full quality NOT_RUN for this resumed dirty checkout**, per the caller's
Termux resource/SIGKILL constraint. Prior 1,154-test claims below are historical;
no new full-quality PASS is asserted. Local Rust build/import/negative-argument
acceptance and modified-source hosted CI are NOT_RUN. PowerShell native parsing,
Windows native runtime and Android/Hermes were not rerun locally.

Phase 5 and Phase 6 security reports were reviewed. Existing
`.appforge/tmp/v10/osv/phase9-security/summary.json` was inspected: historical
scanner 2.7.0, project exit 1 FINDINGS, 22 manifests, ten advisory IDs; prior
report classifies four HIGH. This resume did not rerun OSV or real Gitleaks and
does not reverify database freshness or infer security clearance. OSV database,
scope, flags, negative controls, Gitleaks settings, advisories and severities
remain unchanged. All five original PyO3 relationships remain shipped.
**Security BLOCKED; 10/4-HIGH is historical only.**

No commit, push, dispatch, merge, main edit, release, deploy, signing, Play
Production action or artifact publication was performed.

## Preserved interrupted-session report (historical evidence)

Shipped PyO3 remains exactly **0.23.5**. Candidate remains exactly **0.29.0**.
No Cargo manifest or lockfile was edited or reconstructed. No original PyO3
advisory relationship has been removed from the shipped graph.

## Identity and independently checked hosted evidence

Continued only supplied isolated `feat/v10-oss-unified-20261028`, base/HEAD
`a5913836f81fb46ce5461ab0c337b311cf7cb4b7`. Tracked files initially clean;
four preexisting untracked `.t21-*` folders were preserved. Available V9 base
is `afb3ad9209bcce8b84773d8873d037b15b4d804d`; no local main ref exists.
No commit, push, workflow dispatch, merge, deployment, release, signing, Play,
main or V9 mutation occurred. Android/Windows jobs are byte-identical to HEAD;
SDK/NDK, Expo and scanner policy files remain unchanged.

Independently read the GitHub REST run, jobs, Python job logs and artifact list
using the connected GitHub read tools. [Run 40829447862](https://github.com/HACKMASTER-TR/AppForge-Studio/actions/runs/40829447862)
is completed/success, push event, exact V10 branch and base SHA above, attempt 1.
All three jobs succeeded: compile 123300030003, python-extension 123300029888,
windows-native 123300029681. Python logs independently bind checkout to that SHA.
Both shipped/candidate release compiles and actual shared-library import/runtime
checks succeeded on Ubuntu 24.04 x64, Rust **1.85.1**
`4eb161250 2025-03-15`, Cargo **1.85.1** `d73d2caf9 2024-12-31`, CPython
**3.12.12**. Each runs answer() 1,000 times and rejects an extra argument.
This is historical acceptance of actual base sources, not hosted acceptance of
this dirty checkout or a future exported lock. No artifacts exist on that run.

Candidate resolver log SHA-256:
`a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a`.
The log contains a dependency tree and hash, but not complete Cargo.lock bytes;
the script deleted its temporary fixture. Reconstructing that lock from the
tree/checksums would violate the task, so it was not attempted.

## Source, ABI and dependency inspection

Actual lib.rs uses `#[pyfunction] fn answer() -> i32 {42}` and
`#[pymodule] fn appforge_native(module: &Bound<'_, PyModule>) -> PyResult<()>`,
with `add_function(wrap_pyfunction!(answer, module)?)`. Hosted candidate compile
and import directly exercise these APIs without source substitutions. Manifest
is edition 2021/cdylib, exact `=0.23.5`, `extension-module` and `abi3-py312`.
The latter selects Python's stable ABI with a Python 3.12 floor; a successful
3.12.12 import is not testing every later interpreter, free-threading or device.
Phase 5's verified 0.29.0 artifact declares Rust 1.83 minimum and retains both
features, compatible with the actually tested 1.85.1 toolchain.

Parsed shipped format-4 lock: one local root plus 20 registry packages.
All 20 pinned checksums independently match crates.io version metadata; results
are under ignored `.appforge/tmp/v10/phase9/shipped-registry-checksums.json`.
This comparison is registry metadata integrity, while historical Cargo builds
verify downloaded crate content against the resolved lock. Phase 5 candidate
artifact SHA-256 is
`cd274650b21d4bfc26a0a47587962c1edb425f69287324355cd040c3ea66071c`.
All five candidate 0.29.0 crate archives were freshly fetched and their actual
SHA-256 values matched crates.io metadata; the main manifest minimum/features
were rechecked from the downloaded archive. Results are in
`.appforge/tmp/v10/phase9/candidate-crate-integrity.json`. These checks do not
establish the complete resolved candidate graph without its authentic lock.

Hosted candidate resolution updates all five PyO3 family crates to 0.29.0 and
target-lexicon 0.12.16 to 0.13.5; removes autocfg, cfg-if, indoc, memoffset,
rustversion and unindent. Candidate tree retains libc 0.2.191, once_cell 1.21.4,
heck 0.5.0, proc-macro2 1.0.107, quote 1.0.47, syn 2.0.119 and unicode-ident
1.0.27. Portable-atomic is in the old full lock but absent from the Linux
candidate tree; only a complete exported lock can establish all target edges.
These observations were never used to handwrite a lock.

## Smallest read-only CI retrieval route

Neither Termux PATH nor Ubuntu PRoot PATH/conventional Cargo locations expose
Cargo or rustc. Local Python is 3.13.13. No toolchain installation was attempted.
Shipped manifest, lock and lib.rs remain byte-identical to HEAD.

Only the existing branch-guarded Linux candidate step opts into
`PYO3_EXPORT_LOCK=1`. The existing real resolver operates in mktemp, retains
exact candidate `=0.29.0`, builds with `--locked`, imports the actual shared
object, and validates behavior before export. Export additionally requires exact
Rust/Cargo 1.85.1 and Python 3.12.12. Lock bytes are compared before/after
locked build; failed resolution/build/import/validation emits no export record.

After acceptance a Python TOML check requires a regular non-symlink lock of at
most 64 KiB, format 4, unique package names, exact five-crate 0.29.0 family,
crates.io sources, hexadecimal checksums, resolved dependency names and expected
local root. It emits exactly one `APPFORGE_PYO3_CANDIDATE_LOCK_V1=` JSON record
containing byte length, SHA-256 and base64 of Cargo.lock ONLY. Encoding preserves
bytes through timestamped GitHub logs. No binary, environment value, token,
archive, executable, release or upload action is exported. Existing actions and
full SHA pins remain unchanged; permissions remain contents:read and checkout
persist-credentials:false. No new action needs provenance verification.

This route uses the already verified GitHub job-log read capability instead of
adding an artifact action. It is locally prepared, not yet hosted/executed.
A later separately authorized push/run must have its exact reviewed SHA, branch,
workflow, successful Python job and toolchain verified through GitHub run/jobs.
Retrieve that job's decoded logs through the same read-only API, find the single
record whose line starts with the marker after removing the log timestamp, parse
JSON, base64-decode with validation, compare length/SHA-256, and parse the TOML.
Write only those decoded bytes to an isolated regular Cargo.lock; never execute
log content, infer missing bytes or use a failed/truncated export. Retain run/job
IDs, source SHA, lock hash and all registry checksum evidence. Do not require
matching the old hash if new registry resolution differs: review the complete
new graph and its actually tested hash instead. Only after full review and
fresh locked acceptance may manifest plus complete lock be applied together.
This task performs neither retrieval from a future run nor partial migration.

## Security and verification

Fresh unchanged OSV-Scanner 2.7.0 harness execution `phase9-security` verifies all
four complete database exports under the original 72-hour freshness policy,
22-manifest scope and identical offline/no-resolve/all-packages/all-vulns/no-ignore
flags. All missing/corrupt database, positive vulnerable-dependency and inherited
kernel network-denial controls PASS. Project exit **1 = FINDINGS**;
**10 advisory IDs / 4 HIGH**, 1,987 occurrences, 925 unique coordinates; same ID
set as phase6-final. The five original PyO3 relationships remain:
GHSA-36hh-v3qg-5jq4, GHSA-chgr-c6px-7xpp, GHSA-pph8-gcv7-4qj5,
RUSTSEC-2025-0020, RUSTSEC-2026-0177. No suppression or mitigation claimed.
Raw new evidence remains under ignored `.appforge/tmp/v10/phase9/` and
`.appforge/tmp/v10/osv/phase9-security/`; historical evidence is preserved.

Focused workflow contracts PASS 5/5; JavaScript/Bash syntax, YAML parse and all
inline Bash syntax PASS. Export validator rejects the real shipped lock with no
output. Android and Windows job byte equality and unchanged shipped Rust files
PASS. Canonical Second Brain regeneration/freshness PASS; wiki drift and secret
checks executed without changing security scope. Full PRoot quality and fresh
Gitleaks results are recorded below after execution; historical hosted success
is never substituted for these modified-checkout checks.

Final modified-source full quality: **PASS 1,154/1,154, zero failures/cancelled/
skipped** in 200.747 seconds, command exit 0. Reused the exact preserved Phase 6
PRoot command array, Ubuntu-first PATH, Java 25 and existing workspace/tmp/curl
bindings; no global packages installed or test thresholds changed. Command and
exit record: `.appforge/tmp/v10/phase9/quality-result.json`; raw log:
`.appforge/tmp/v10/phase9/quality-proot.log`. Canonical snapshot check passes
against the final selected source. Report-only evidence updates do not change
its selected source basis. Wiki changed-source audit: zero errors, 18 advisory
coverage warnings; wiki secret hygiene Green. Local actionlint and native
PowerShell parsing were not executed; existing Windows on-host parsing is
unchanged. No new hosted acceptance is claimed.

Real Gitleaks **8.30.1** verified binary SHA-256
`00e91bbe655bd7c47753e8cfe61cb76ea1a5d7e7702fe161ee40102b46b3823b`:
positive detection and all eight negative-path controls PASS. Unchanged source
scope (tracked plus nonignored untracked regular files), configuration, redaction
and exclusions: fresh scan PASS, zero findings. The source scan is repeated
following this final report rendering, with final inventory/exit evidence in
`.appforge/tmp/v10/phase9/gitleaks-source-evidence.json`.

Outcome: **PYO3_CI_LOCK_EXPORT_READY=YES_LOG_ROUTE_PREPARED_NOT_HOSTED**.
The safe retrieval route exists without new actions or artifact uploads.
**PYO3_CARGO_LOCK=UNCHANGED_AUTHENTIC_CANDIDATE_NOT_RETRIEVED**.
Historical shipped/candidate build acceptance PASS; local Rust build NOT_RUN
because Cargo/rustc are absent; modified export hosted acceptance NOT_RUN.
Migration and security clearance remain BLOCKED; no delivery action performed.

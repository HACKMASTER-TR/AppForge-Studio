# V10 Phase 8 — Windows CI repair

Date: 2026-10-29. Status: IMPLEMENTED locally; full quality FAIL;
Windows native acceptance PENDING a successful hosted Windows run.

## Scope and Git identity

Continued the supplied isolated feature worktree on
`feat/v10-oss-unified-20261028`, initially clean, at
`2ecf73fcbda900a2a658da72fff90ea44bf76b63` (task base and unchanged HEAD).
The available V9 reference has merge-base
`afb3ad9209bcce8b84773d8873d037b15b4d804d`; no local main reference exists,
so no main merge-base is claimed. Only the V10 workflow, its contract test,
and this report are changed. No commit, push, merge, deployment, release,
main/V9 edit, signing or Play action occurred.

## Failure and repair

User-confirmed hosted run 40825961887 at 2ecf73f passed Android compilation
and Hermes, and Rust/PyO3 build/import. Windows failed before packaging:
npm ci installed 285 packages but electron/dist/electron.exe was absent.
These are supplied historical results, not independently fetched results or
acceptance of this uncommitted patch.

Electron 42+ requires explicit binary installation rather than the former
automatic npm postinstall download. The project pins Electron 43.8.1.
After the checked npm ci, the Windows prerequisite step now enters windows-host
and executes `npx --no-install install-electron --no`, resolving the CLI from
the installed local package. Nonzero installer exit throws immediately;
finally restores the repository working directory. Existing file checks still
require both electron/dist/electron.exe and node_modules/.bin/electron-builder.cmd.
There are no generated placeholder executables or validation bypasses.

Node/npm/Electron pins, exact branch trigger, read-only permissions and action
SHA pins remain unchanged. Android and Rust jobs are byte-for-byte unchanged.
Real unsigned portable packaging, PE and Authenticode checks, authored smoke
payload, bounded launch/relaunch, and localStorage/IndexedDB persistence checks
remain mandatory and unchanged. No release artifact integrity checks removed.
OSV findings, exclusions, security gates and application/build modules untouched.

The new workflow contract requires npm ci, its exit check, local installer and
its exit check, working-directory restoration, both executable checks, their
exit check, and native acceptance invocation in order. It rejects
continue-on-error and ignore-scripts in the Windows job.

## Local validation

- Workflow contract: PASS, 4/4 tests.
- YAML: PASS using existing windows-host js-yaml parser.
- Bash syntax: PASS, all six inline workflow Bash blocks via bash -n and
  scripts/v10/pyo3-host-acceptance.sh.
- JavaScript syntax: PASS, node --check on the changed contract.
- Windows host Node tests: PASS, 9/9.
- V10 Python gate tests: PASS, 14/14.
- Broader V10 JS tests: FAIL, 5/7 pass; Gradle telemetry and Kotlin catalog
  fixtures abort in the local Java environment (ProtoLogNative registration).
- Complete quality suite: FAIL, 1118/1153 pass, 35 fail, zero skips.
  Failures include local Java/Kotlin/Gradle aborts, absent
  /usr/lib/apt/methods/http, unavailable /tmp fixture directories, and
  SNAPSHOT_OUT_OF_DATE. The selected-source snapshot includes quality contracts;
  no unrelated generated snapshot was rewritten in this narrowly scoped repair.
- git diff --check: PASS.

Raw local logs are outside the repository in the Termux temporary directory:
`v10-phase8-quality.log`, `v10-phase8-focused.log`,
`v10-phase8-windows-tests.log`, and `v10-phase8-python.log`.
Initial log redirection to /tmp failed with permission denied before tests ran;
actual test runs used /data/data/com.termux/files/usr/tmp.

PowerShell is unavailable locally. The existing hosted PowerShell AST parsing
step remains mandatory. The installer, native packaging and launch/relaunch
were NOT_RUN here. Prior Windows result remains FAIL; this fix is not Windows
native PASS. A future explicitly authorized hosted run must establish acceptance.
No hosted run was triggered and no delivery is authorized by local results.

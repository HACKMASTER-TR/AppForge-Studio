# V10 residual security remediation — 2026-10-28

**V10_RESIDUAL_STATUS=BLOCKED.** Unresolved vulnerabilities, Android inventory/SDK,
native Windows acceptance and distribution notices remain BLOCKED.

## Identity and safety

Continued the supplied isolated `feat/v10-oss-unified-20261028` worktree at
HEAD/recorded base `afb3ad9209bcce8b84773d8873d037b15b4d804d`.
The tree was already dirty; previous work is preserved. No local main ref exists,
so an independent main merge-base remains UNKNOWN. No commit, push, merge,
deploy, release, signing, Android SDK/AGP or V9 authorization changes occurred.

## Supported compatible fix

Both `examples/rust-expo-jsi/package-lock.json` and
`examples/rust-expo-turbo/package-lock.json` change only
`node_modules/node-forge`: **1.4.0 → 1.4.2**, with npm-generated registry URL and
integrity metadata. Direct manifests, parent versions and dependency graph stay
unchanged. Existing @expo/code-signing-certificates `^1.2.1` and Expo CLI
`^1.3.1` ranges accept this patch. License remains BSD-3-Clause OR GPL-2.0;
distribution notice approval remains separate and BLOCKED.

The [upstream Forge changelog](https://github.com/digitalbazaar/forge/blob/main/CHANGELOG.md)
documents the nested DigestAlgorithm signature-forgery fix in 1.4.1 and PEM
Buffer compatibility restoration in 1.4.2. This addresses the weakness recorded
as **GHSA-86w9-cpqp-85rv** (CVE-2026-33894, CVE-2026-85393 and
GHSA-ppp5-5v6c-4jwp aliases in the pinned scan). Upstream uses
GHSA-2g46-22mc-6gwp for the nested-element fix; IDs are not silently substituted.

`quality/tests/v10_forge_real_runtime.cjs` exercises actual low-exponent RSA:
valid signature accepted, altered message rejected, malformed extra nested
DigestAlgorithm element rejected, PEM Buffer decoding and key round-trip.
The same behavioral test (only expected installed version changed) fails on
1.4.0 at the malformed-element assertion and passes on 1.4.2. No tests were
weakened or suppressed. Exact artifact installation and offline `npm ci` pass
in the isolated smoke directory. Both real Expo locks pass offline npm
package-lock-only resolution unchanged after update. This does not establish
complete offline Expo tarball-pack availability, native app execution or signing
acceptance; refreshed distributable packs remain BLOCKED.

## Every remaining affected pair — BLOCKED

| Package | Version | Exact advisory | Pinned severity |
| --- | --- | --- | --- |
| pyo3 | 0.23.5 | GHSA-36hh-v3qg-5jq4 | HIGH |
| image-size | 1.2.1 | GHSA-5p2g-fcmc-qvqq | HIGH |
| http-cache-semantics | 4.2.0 | GHSA-ch52-4w7c-c8xp | HIGH |
| pyo3 | 0.23.5 | GHSA-chgr-c6px-7xpp | MODERATE |
| deepmerge | 4.3.1 | GHSA-gcqx-w48m-w2h4 | LOW |
| fast-xml-parser | 4.5.7 | GHSA-gh4j-gqv2-49f6 | MODERATE |
| zod | 3.25.76 | GHSA-mrhx-c7xc-m3w5 | HIGH |
| pyo3 | 0.23.5 | GHSA-pph8-gcv7-4qj5 | LOW |
| image-size | 1.2.1 | GHSA-w3rx-r6r6-pgpr | HIGH |
| pyo3 | 0.23.5 | RUSTSEC-2025-0020 | UNKNOWN |
| pyo3 | 0.23.5 | RUSTSEC-2026-0177 | UNKNOWN |

Counts deduplicate ecosystem/name/version/advisory, preserving GHSA/RUSTSEC alias
overlap. HIGH uses the original pinned advisory labels, not inferred CVSS or
proof of exploitation.

## Reachability, priorities and blocked alternatives

- **Windows production boundary:** package.json ships main.cjs, payload.cjs,
  storage.cjs and adm-zip. None of the residual packages is a declared production
  dependency or included by the explicit package files list. Actual EXE contents
  and physical Windows acceptance remain BLOCKED; manifest analysis cannot clear
  an uninspected artifact.
- **image-size 1.2.1, two HIGH IDs:** Expo JSI/Turbo → Metro → image-size
  `^1.0.2`. Untrusted asset metadata can reach development/bundling tooling.
  Snapshot fixes require 2.0.3; forcing it violates the owning parent's major
  range. Compatible parent migration and adversarial parser acceptance BLOCKED.
  Restrict inputs to trusted assets; no tested parser mitigation is claimed.
- **Zod 3.25.76, HIGH:** Expo → @expo/mcp-tunnel → zod `^3.25.76`.
  Oversized constrained collections require an input-validation boundary;
  deployed tunnel/server exposure is UNKNOWN. Fixed boundary 4.6.6 requires
  parent migration. No application-wide monkey patch or superficial byte limit
  is asserted to fix collection allocation. BLOCKED.
- **PyO3 0.23.5, five IDs including one HIGH:** direct rust-python extension,
  `extension-module`/`abi3-py312`; source exposes only answer() and registration.
  Vulnerable nth/nth_back, new_closure and PyString::from_object APIs are not
  called directly. Transitive/native safety is not proven. Fixed boundaries
  0.24.1 and 0.29.0 are breaking pre-1.0 changes, and no Cargo compiler is
  available in this host. Compile/import and Python acceptance remain BLOCKED.
- **fast-xml-parser 4.5.7:** RN CLI Android config and Apple platform helpers use
  `^4.4.1`; XMLBuilder comment/CDATA injection requires builder output and
  attacker-controlled delimiters. Exact vulnerable builder call path through
  those consumers is unproven. Snapshot fixed boundary 5.7.0 needs major parent
  migration. Keep untrusted project/config execution restricted. BLOCKED.
- **deepmerge 4.3.1, unpatched:** @expo/config in both Expo locks and RN CLI,
  cli-config and cli-doctor in the RN lock. Config merges can consume project
  values; actual malicious prototype flow is unproven. Registry latest is
  4.3.1; no fixed event exists in the snapshot. No global prototype mutation,
  vendor edit or security suppression. BLOCKED.
- **http-cache-semantics 4.2.0, HIGH/unpatched in snapshot:** Windows build-only
  app-builder-lib → @electron/get 3 → got 11 → cacheable-request 7 → policy.
  Installed got defaults cache to undefined; downloader passes options through.
  No HTTP shared-cache configuration is set by this repository; Electron's
  artifact file cache is a separate mechanism. Thus the shared-response exploit
  prerequisite is not established for this build path, and it is outside the
  explicit shipped dependency list. Custom downloader options or future cache
  changes require reevaluation. Registry 4.3.0 exists, but snapshot supplies no
  fixed event and no verified patch for this claim was established: upgrading
  solely beyond last_affected would be scanner clearance, not proven repair.
  The [upstream dispute](https://github.com/github/advisory-database/issues/10139)
  does not authorize suppression or acceptance. Keep build/download caches
  isolated by trust domain; enforcement beyond existing defaults is unverified.
  BLOCKED.

No additional residual mitigation is claimed: **OSV_MITIGATED=0**. The Forge
change is a tested dependency fix, not an operational mitigation. Recommended
trust restrictions are not counted as implemented controls. Every unresolved
pair remains BLOCKED regardless of reachability observations.

## Real scan and verification

Final execution evidence is recorded below after completion. Raw logs are kept
in ignored `.appforge/tmp/v10/residual/`; OSV raw evidence is in
`.appforge/tmp/v10/osv/residual-after/`. No ignore rules, scan scope, security
configuration or tests were changed.

### Final executed results

Real OSV-Scanner 2.7.0, binary SHA-256
`4061ef4c021ed3c47f203b818f337e351336cc5cd67c0274fc36bd1dfc728dd7`,
verified the same four complete database exports and 72-hour freshness policy.
All 22 manifest paths match the prior scope, and final source hashes were
rechecked against the actual files. Same --offline, --no-resolve,
--no-call-analysis=go/rust, --all-packages, --all-vulns, --no-ignore and recursive
flags; kernel exec-child network-denial control unchanged. Missing/corrupt DB,
known-vulnerable lodash detection and network boundary controls all PASS.

Project scanner exit **1 = FINDINGS**: **11 advisory IDs / 11 pairs / 5 HIGH /
2 no-fixed-event pairs**, 1,993 package occurrences, 928 unique coordinates.
Exactly GHSA-86w9-cpqp-85rv / node-forge@1.4.0 no longer matches; no new
advisory or package/advisory relationship. Both remaining no-fixed-event pairs
are deepmerge and http-cache-semantics. Overall historical comparison:
47 → 12 → 11 IDs; 52 → 12 → 11 pairs. Scanner clearance alone is not platform
or production acceptance.

| Check actually executed | Result |
| --- | --- |
| Full quality, Ubuntu PRoot with existing Termux Node/JVM and curl bindings | PASS 1,151/1,151; zero skips |
| V9 Python regression | PASS 440/440 |
| V10 Python | PASS 14/14; JS V10 contracts included in full quality |
| Windows Node tests | PASS 9/9; native Windows launch/packaging NOT_RUN/BLOCKED |
| Forge targeted real runtime | PASS; original version negative control fails as expected |
| Offline install / locks | PASS isolated exact-artifact ci; both Expo offline lock resolutions unchanged |
| Gitleaks 8.30.1 controls | PASS positive detection and eight negative-path controls |
| Gitleaks real source scan | PASS zero findings; tracked + nonignored untracked source scope, redacted reporting |
| Local stability gate ci | PASS |
| Second Brain snapshot freshness and changed-source audit | PASS; regenerated from canonical source |
| Android | BLOCKED: no configured SDK, local.properties or SDK in checked standard host/PRoot locations; build/tests NOT_RUN |
| Hosted CI, physical acceptance, delivery | NOT_RUN; no delivery authorized |

Exact commands, exits, four database hashes, scan inventory and private-log
SHA-256 values are in [structured evidence](osv-residual-evidence.json).
The full quality suite ran while remediation was in progress; independent final
snapshot and source hygiene checks follow documentation completion. No hosted or
physical acceptance is inferred. No tests, ignore rules, signing or safety
controls were weakened. OSV_MITIGATED counts newly enforced workarounds only and
is zero; the one repaired advisory is recorded separately above.

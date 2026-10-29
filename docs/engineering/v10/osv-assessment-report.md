# V10 OSV assessment — genuine offline execution

**OSV FINDINGS; V10 overall BLOCKED.** This is local internal assessment evidence, not clean dependency, Android, production, license-distribution, or release acceptance. Existing V10 changes and the production fail-closed OSV gate are preserved. No dependency was upgraded.

Branch `feat/v10-oss-unified-20261028`; base/HEAD `afb3ad9209bcce8b84773d8873d037b15b4d804d`. The supplied dirty isolated worktree has no local `main` ref; an independent merge-base to main is unavailable. No commit, ref update, push, merge, deploy, release, signing or production action occurred.

## Verified executable and environment

Official [OSV-Scanner v2.7.0](https://github.com/google/osv-scanner/releases/tag/v2.7.0) Linux ARM64 executable SHA-256 is `4061ef4c021ed3c47f203b818f337e351336cc5cd67c0274fc36bd1dfc728dd7`. Downloaded bytes match both the release API digest and the versioned `osv-scanner_SHA256SUMS` entry. ELF machine 183 and real version execution confirm AArch64/2.7.0. The existing Ubuntu ARM64 PRoot executes it; Android Bionic executable compatibility is not claimed. No system/rootfs/toolchain installation occurred.

The [versioned Apache-2.0 license](https://raw.githubusercontent.com/google/osv-scanner/v2.7.0/LICENSE) hashes to `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`. Binary signature/attestation and transitive redistribution license closure are NOT_VERIFIED/NOT_APPROVED. This test does not lift the production wrapper gate.

## Database provenance and freshness

Whole ecosystem exports come from the OSV-maintained GCS bucket identified by the [versioned offline guide](https://github.com/google/osv-scanner/blob/v2.7.0/docs/offline-mode.md). Acquisition pins each GCS object generation, checks published object size/MD5, computes SHA-256, verifies every ZIP entry CRC and parses every JSON record. All matching ecosystem counts equal record counts. No selective advisory subset was substituted. The 72-hour assessment freshness bound rejects missing, corrupt, stale or future-dated inputs; scan preflight rechecks local size/SHA-256/age without fetching anything.

Trust is HTTPS origin plus generation/content consistency, not a signed database provenance attestation. The captured upstream CRC32C is recorded but not independently recomputed; ZIP entry CRCs are verified. Exact acquisition/update timestamps, immutable generation URLs, sizes and hashes are in [the evidence manifest](osv-assessment-evidence.json).

| Ecosystem | JSON records | Age at acquisition | SHA-256 |
| --- | ---: | ---: | --- |
| npm | 230857 | 0.70 h | `ac6849f148cbb22f923c798f9e2c2a55f65ca4841631cd434eb3d63f1d452567` |
| Maven | 7206 | 16.41 h | `51048a424aea097722980d0903613b420695fd324eca076a37fd641bf0fa9126` |
| PyPI | 26677 | 9.44 h | `d399bbfba748c8ae0b808731cda13a80ae20d93b73205f4abcbbbac1cbb678ea` |
| crates.io | 3002 | 1.21 h | `b70461f2db453ed385482277b539fb4dfc08688ba943d5023c0b87655beb24d8` |

The pinned implementation differs from its offline guide: [the exact Scalibr source](https://github.com/google/osv-scalibr/blob/5be78a00e3c2/enricher/vulnmatch/osvlocal/matcher.go) uses `OSV_SCALIBR_LOCAL_DB_CACHE_DIRECTORY` and `{cache}/osv-scalibr/{ecosystem}/all.zip`. The older `OSV_SCANNER` variable and `osv-scanner` directory in the guide do not load these databases. This was verified by retained failed and successful actual scans.

## Enforced offline execution and controls

`quality/tools/v10_osv_offline_exec.py` installs a kernel seccomp filter before launching PRoot/scanner. It enables no-new-privileges, kills alternate syscall ABIs, denies AArch64 socket/socketpair/connect/sendto/sendmsg/sendmmsg and io_uring syscalls, closes inherited descriptors above stdio and refuses socket stdio. Both IPv4 and IPv6 socket creation return EPERM in a separately executed child inside PRoot. The scanner version and all accepted scans execute under that same launcher. PRoot itself is a compatibility environment, not a hostile-code security sandbox. The earlier unchanged network namespace probe is not used as isolation evidence.

Scanner flags include `scan source --offline --no-resolve --no-call-analysis=go --no-call-analysis=rust --all-packages --all-vulns --format=json --no-ignore --recursive`. No download flag, online fallback, dependency resolver, build script or imported scanner config is enabled. Only copied supported manifests are scanned; explicit `--no-ignore` is necessary because the private fixture directory is beneath ignored `.appforge`. Scanner stdout/stderr and full reports remain private in the ignored cache.

| Real control | Exit/result |
| --- | --- |
| Missing npm database | 127; explicit no-offline-database error; integrity preflight also rejects |
| Corrupted npm database | 127; invalid-ZIP error; integrity preflight also rejects |
| Registry lodash 4.17.20 | 1; five advisories, including GHSA-35jh-r3h4-6jhm |
| Exec child IPv4/IPv6 sockets | EPERM for both; probe exit 0 |
| Project dependency assessment | 1; findings preserved |

The first attempt skipped an ignored fixture, the second classified a fixture without registry provenance as LOCAL and returned 0, and the third used the obsolete documented DB layout. Each failed the positive-control assertions and is retained as failure evidence. The accepted fourth attempt corrects source extraction, registry provenance and the actual cache layout. No earlier empty report is promoted to PASS.

Repeatable opt-in commands (no downloads during assessment):

```sh
python3 docs/engineering/v10/osv-acquire.py .appforge/tmp/v10/osv/database-new
# After review, use the verified assets at the harness-documented cache path.
python3 quality/tests/v10_osv_real_scanner.py assessment-new
```

The acquisition helper is a separate explicit network action. The assessment requires existing locally verified assets, refuses an existing evidence directory, and performs no network acquisition. It is deliberately outside the default quality runner and does not implement a production gate bypass.

## Findings and coverage

22 manifests were copied byte-for-byte with individual SHA-256 records. Nineteen nonempty result sources contain **1,993 package occurrences / 930 unique ecosystem-name-version coordinates**: npm 898, crates.io 30, Maven 2. Two comment-only Python requirements and one empty Cargo lock yield no packages. PyPI data is verified but no project PyPI dependency was assessed. Cargo output includes first-party identities; this is no first-party code audit.

**47 advisory IDs affect 22 package occurrences.** Advisory IDs can share aliases; this is not 47 deduplicated CVEs. The table groups repeated coordinates across manifests. All paths, aliases, severity/CVSS and exact affected ranges remain in the evidence JSON; UNKNOWN severity is preserved. Database matches do not establish runtime reachability. No finding is suppressed, reclassified or automatically remediated.

| Ecosystem / package / version | Manifests affected | Advisory IDs |
| --- | ---: | --- |
| crates.io `pyo3@0.23.5` | 1 | [GHSA-36hh-v3qg-5jq4](https://osv.dev/vulnerability/GHSA-36hh-v3qg-5jq4), [GHSA-chgr-c6px-7xpp](https://osv.dev/vulnerability/GHSA-chgr-c6px-7xpp), [GHSA-pph8-gcv7-4qj5](https://osv.dev/vulnerability/GHSA-pph8-gcv7-4qj5), [RUSTSEC-2025-0020](https://osv.dev/vulnerability/RUSTSEC-2025-0020), [RUSTSEC-2026-0177](https://osv.dev/vulnerability/RUSTSEC-2026-0177) |
| npm `@babel/core@7.25.2` | 1 | [GHSA-4x5r-pxfx-6jf8](https://osv.dev/vulnerability/GHSA-4x5r-pxfx-6jf8) |
| npm `@babel/runtime@7.25.0` | 1 | [GHSA-968p-4wvh-cqc8](https://osv.dev/vulnerability/GHSA-968p-4wvh-cqc8) |
| npm `adm-zip@0.6.0` | 1 | [GHSA-7q85-xj36-vmfc](https://osv.dev/vulnerability/GHSA-7q85-xj36-vmfc), [GHSA-8238-w5pm-2374](https://osv.dev/vulnerability/GHSA-8238-w5pm-2374), [GHSA-c6fg-446q-cg94](https://osv.dev/vulnerability/GHSA-c6fg-446q-cg94), [GHSA-j5f4-cc29-5x44](https://osv.dev/vulnerability/GHSA-j5f4-cc29-5x44), [GHSA-p634-w6r4-rjp2](https://osv.dev/vulnerability/GHSA-p634-w6r4-rjp2), [GHSA-rcw4-f5rp-g42v](https://osv.dev/vulnerability/GHSA-rcw4-f5rp-g42v), [GHSA-vwc7-r8mq-g2x9](https://osv.dev/vulnerability/GHSA-vwc7-r8mq-g2x9) |
| npm `body-parser@1.20.8` | 1 | [GHSA-27r3-jxmh-p828](https://osv.dev/vulnerability/GHSA-27r3-jxmh-p828) |
| npm `deepmerge@4.3.1` | 3 | [GHSA-gcqx-w48m-w2h4](https://osv.dev/vulnerability/GHSA-gcqx-w48m-w2h4) |
| npm `electron@43.4.1` | 1 | [GHSA-2p8c-gr87-2676](https://osv.dev/vulnerability/GHSA-2p8c-gr87-2676), [GHSA-3qxv-5xgr-q7w9](https://osv.dev/vulnerability/GHSA-3qxv-5xgr-q7w9), [GHSA-98mx-cjp9-gchm](https://osv.dev/vulnerability/GHSA-98mx-cjp9-gchm), [GHSA-997q-gvvc-jwmx](https://osv.dev/vulnerability/GHSA-997q-gvvc-jwmx), [GHSA-fw47-9c62-h3vw](https://osv.dev/vulnerability/GHSA-fw47-9c62-h3vw), [GHSA-h3v5-h239-pg29](https://osv.dev/vulnerability/GHSA-h3v5-h239-pg29), [GHSA-jr5w-w6x2-84p9](https://osv.dev/vulnerability/GHSA-jr5w-w6x2-84p9), [GHSA-qmv3-fv6v-rmhq](https://osv.dev/vulnerability/GHSA-qmv3-fv6v-rmhq), [GHSA-x562-297c-g43g](https://osv.dev/vulnerability/GHSA-x562-297c-g43g) |
| npm `fast-uri@3.1.8` | 1 | [GHSA-333x-w4r6-6crp](https://osv.dev/vulnerability/GHSA-333x-w4r6-6crp), [GHSA-w74x-v6f9-j3xg](https://osv.dev/vulnerability/GHSA-w74x-v6f9-j3xg), [GHSA-wjr9-j795-jxcr](https://osv.dev/vulnerability/GHSA-wjr9-j795-jxcr), [GHSA-xhq7-xmqj-769h](https://osv.dev/vulnerability/GHSA-xhq7-xmqj-769h) |
| npm `fast-xml-parser@4.5.7` | 1 | [GHSA-gh4j-gqv2-49f6](https://osv.dev/vulnerability/GHSA-gh4j-gqv2-49f6) |
| npm `http-cache-semantics@4.2.0` | 1 | [GHSA-ch52-4w7c-c8xp](https://osv.dev/vulnerability/GHSA-ch52-4w7c-c8xp) |
| npm `image-size@1.2.1` | 2 | [GHSA-5p2g-fcmc-qvqq](https://osv.dev/vulnerability/GHSA-5p2g-fcmc-qvqq), [GHSA-w3rx-r6r6-pgpr](https://osv.dev/vulnerability/GHSA-w3rx-r6r6-pgpr) |
| npm `node-forge@1.4.0` | 2 | [GHSA-86w9-cpqp-85rv](https://osv.dev/vulnerability/GHSA-86w9-cpqp-85rv) |
| npm `postcss@8.4.49` | 2 | [GHSA-6g55-p6wh-862q](https://osv.dev/vulnerability/GHSA-6g55-p6wh-862q), [GHSA-fxqj-rqcc-2cmp](https://osv.dev/vulnerability/GHSA-fxqj-rqcc-2cmp), [GHSA-qx2v-qp2m-jg93](https://osv.dev/vulnerability/GHSA-qx2v-qp2m-jg93), [GHSA-r28c-9q8g-f849](https://osv.dev/vulnerability/GHSA-r28c-9q8g-f849) |
| npm `undici@6.28.1` | 1 | [GHSA-2m5v-pch6-74hx](https://osv.dev/vulnerability/GHSA-2m5v-pch6-74hx), [GHSA-4qv8-2p9j-2685](https://osv.dev/vulnerability/GHSA-4qv8-2p9j-2685), [GHSA-9fm3-97px-rjf6](https://osv.dev/vulnerability/GHSA-9fm3-97px-rjf6), [GHSA-qrxm-fqhp-jwvm](https://osv.dev/vulnerability/GHSA-qrxm-fqhp-jwvm), [GHSA-xjj6-f33q-929c](https://osv.dev/vulnerability/GHSA-xjj6-f33q-929c) |
| npm `undici@7.29.1` | 1 | [GHSA-2m5v-pch6-74hx](https://osv.dev/vulnerability/GHSA-2m5v-pch6-74hx), [GHSA-4qv8-2p9j-2685](https://osv.dev/vulnerability/GHSA-4qv8-2p9j-2685), [GHSA-4w7w-5xrc-9h26](https://osv.dev/vulnerability/GHSA-4w7w-5xrc-9h26), [GHSA-55wc-3pg8-jfw6](https://osv.dev/vulnerability/GHSA-55wc-3pg8-jfw6), [GHSA-9fm3-97px-rjf6](https://osv.dev/vulnerability/GHSA-9fm3-97px-rjf6), [GHSA-f72v-6w45-q472](https://osv.dev/vulnerability/GHSA-f72v-6w45-q472), [GHSA-qrxm-fqhp-jwvm](https://osv.dev/vulnerability/GHSA-qrxm-fqhp-jwvm), [GHSA-xjj6-f33q-929c](https://osv.dev/vulnerability/GHSA-xjj6-f33q-929c) |
| npm `zod@3.25.76` | 2 | [GHSA-mrhx-c7xc-m3w5](https://osv.dev/vulnerability/GHSA-mrhx-c7xc-m3w5) |

Main `android-app` build scripts do not supply a resolved application dependency lockfile. The missing Android SDK still blocks the resolved Android/transitive/build/plugin inventory. User-generated projects, native bundled runtimes, APK/EXE contents, installed dependencies and Git history are outside this manifest assessment. Offline commit matching/call analysis is not claimed. These gaps and the actual findings prevent overall V10 acceptance. The production OSV adapter remains BLOCKED unchanged; its gate must not be made clean by accepting partial coverage.

## Preservation and local verification

Gitleaks policy/default rules, exact allowlists and production license guards remain unchanged. Current-source scanner and positive/negative detector controls are rerun separately; their final command results are appended to the evidence manifest. V10 security/host contracts and V9 regressions are independently rerun. Canonical Second Brain generation/check and secret/diff checks accompany this report. Historical Android, V9 physical, signing and production acceptance are not inherited from local tests.

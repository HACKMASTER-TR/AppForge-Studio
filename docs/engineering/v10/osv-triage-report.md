# V10 OSV vulnerability triage

**V10_OVERALL=BLOCKED. All findings remain open.** No dependencies, Android SDK/AGP, signing, V9 security, APK or production code were changed. No delivery action occurred.

## Evidence and counting rules

Analysis date: 2026-10-28. Branch `feat/v10-oss-unified-20261028`; HEAD and recorded V10 base `afb3ad9209bcce8b84773d8873d037b15b4d804d`. The supplied isolated feature worktree was already dirty. The local V9 branch shares that SHA; no local main ref exists, so an independent main merge-base is UNKNOWN. Existing edits were preserved.

Latest original structured scanner evidence: `.appforge/tmp/v10/osv/assessment-final/project.json`, SHA-256 `61e80a7abcc6622a2a7d796462e49f0bbdaf21ff00f03f5c05645b4b98c77bea`. Its `summary.json`, `commands.json` and `source-inventory.json` preserve execution context. Earlier `assessment-retry4/project.json` has the same advisory set; its paths differ. Raw reports stay private/ignored. See [assessment evidence](osv-assessment-evidence.json) and [assessment report](osv-assessment-report.md) for scanner 2.7.0, pinned complete database exports, hashes, freshness and kernel network-denial controls.

All 22 scanned manifest hashes were independently rechecked against current files and matched. The latest real scan exited 1: 1,993 package occurrences, 930 unique coordinates and 22 affected package occurrences. Positive-control lodash IDs are excluded. This task analyzes the retained real scan; no new scanner/build/dependency resolution was performed.

Deduplication key is `(ecosystem, name, installed version, advisory ID)`: **52 affected pairs**, 16 affected coordinates, **47 distinct advisory IDs**. Repeated manifests are occurrence evidence, not duplicate pairs. Installed versions stay separate. GHSA/RUSTSEC aliases stay listed; this is not a count of distinct CVEs.

Severity totals count unique IDs. Dependency/fix totals count pairs. MODERATE maps to MEDIUM. Labels come from actual advisory `database_specific.severity`; absent severity remains UNKNOWN, including RUSTSEC entries with labeled GHSA aliases. Scanner group maximums do not override advisory labels. CVSS vectors below are verbatim; numeric base scores are UNKNOWN where not provided. No numeric score was invented. Vector/label disagreement requires review.

Unique-ID severities: {'LOW': 8, 'HIGH': 19, 'MEDIUM': 18, 'UNKNOWN': 2}. Pair severities: {'LOW': 8, 'HIGH': 22, 'MEDIUM': 20, 'UNKNOWN': 2}. Direct pairs: 23; transitive: 29. Fixed-event pairs: 48; no fixed event: 4. All 52 installed pairs remain unresolved.

Fixed events are for matching ecosystem/package records, and may represent different release branches. They are candidate boundaries, not verified available releases, compatibility approvals or clearance. No-fixed-event means unpatched/UNKNOWN in this pinned snapshot, not proof no upstream patch exists today. Post-scan upstream state is UNKNOWN.

## All 47 advisory IDs

| Advisory ID | Severity | Aliases | Summary | CVSS evidence (numeric score UNKNOWN unless explicit) |
| --- | --- | --- | --- | --- |
| [GHSA-27r3-jxmh-p828](https://osv.dev/vulnerability/GHSA-27r3-jxmh-p828) | HIGH | CVE-2026-111181 | body-parser vulnerable to Denial of Service via trailing bytes after a compressed request body | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-2m5v-pch6-74hx](https://osv.dev/vulnerability/GHSA-2m5v-pch6-74hx) | HIGH | CVE-2026-109383 | undici vulnerable to TLS certificate validation bypass via dropped tls options in Agent | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:N |
| [GHSA-2p8c-gr87-2676](https://osv.dev/vulnerability/GHSA-2p8c-gr87-2676) | MEDIUM | CVE-2026-113796 | Electron: SameSite cookie restrictions are not applied to windows opened from links | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:L/A:N |
| [GHSA-333x-w4r6-6crp](https://osv.dev/vulnerability/GHSA-333x-w4r6-6crp) | LOW | CVE-2026-110725 | fast-uri vulnerable to scheme injection via an unescaped first-segment colon in suffix references | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:L/A:N |
| [GHSA-36hh-v3qg-5jq4](https://osv.dev/vulnerability/GHSA-36hh-v3qg-5jq4) | HIGH | RUSTSEC-2026-0176 | PyO3 has an Out-of-bounds Read in `nth` / `nth_back` for `PyList` and `PyTuple` iterators | CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:N/VA:N/SC:N/SI:N/SA:N |
| [GHSA-3qxv-5xgr-q7w9](https://osv.dev/vulnerability/GHSA-3qxv-5xgr-q7w9) | LOW | CVE-2026-114122 | Electron: Frame subscription trusts GPU-supplied frame geometry | CVSS_V3: CVSS:3.1/AV:L/AC:H/PR:H/UI:N/S:C/C:L/I:N/A:L |
| [GHSA-4qv8-2p9j-2685](https://osv.dev/vulnerability/GHSA-4qv8-2p9j-2685) | MEDIUM | CVE-2026-109924 | undici vulnerable to Denial of Service via unbounded memory in the EventSource parser | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-4w7w-5xrc-9h26](https://osv.dev/vulnerability/GHSA-4w7w-5xrc-9h26) | HIGH | CVE-2026-109829 | undici vulnerable to cache poisoning and cross-user disclosure via cross-origin redirects | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:N |
| [GHSA-4x5r-pxfx-6jf8](https://osv.dev/vulnerability/GHSA-4x5r-pxfx-6jf8) | LOW | CVE-2026-49356 | @babel/core: Arbitrary File Read via sourceMappingURL Comment | CVSS_V3: CVSS:3.1/AV:L/AC:H/PR:N/UI:N/S:C/C:L/I:N/A:N |
| [GHSA-55wc-3pg8-jfw6](https://osv.dev/vulnerability/GHSA-55wc-3pg8-jfw6) | MEDIUM | CVE-2026-87029 | undici vulnerable to Denial of Service via unhandled exception in the decompress interceptor | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-5p2g-fcmc-qvqq](https://osv.dev/vulnerability/GHSA-5p2g-fcmc-qvqq) | HIGH | CVE-2025-71329 | image-size: JXL and HEIF parsers allow denial of service through infinite loops | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H; CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:N/VA:H/SC:N/SI:N/SA:N |
| [GHSA-6g55-p6wh-862q](https://osv.dev/vulnerability/GHSA-6g55-p6wh-862q) | HIGH | CVE-2026-45623 | PostCSS: Arbitrary file read and information disclosure via attacker-controlled sourceMappingURL in CSS comments | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N |
| [GHSA-7q85-xj36-vmfc](https://osv.dev/vulnerability/GHSA-7q85-xj36-vmfc) | HIGH | CVE-2026-77301 | adm-zip: Uncontrolled memory allocation via the declared uncompressed size (DoS) | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-8238-w5pm-2374](https://osv.dev/vulnerability/GHSA-8238-w5pm-2374) | HIGH | CVE-2026-114102 | adm-zip: Unhandled error event in async DEFLATE decompression crashes Node.js process (DoS) | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-86w9-cpqp-85rv](https://osv.dev/vulnerability/GHSA-86w9-cpqp-85rv) | HIGH | CVE-2026-33894, CVE-2026-85393, GHSA-ppp5-5v6c-4jwp | node-forge RSA PKCS#1 v1.5 signature verification accepts extra nested DigestAlgorithm elements | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:H/A:N; CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:H/VA:N/SC:N/SI:N/SA:N |
| [GHSA-968p-4wvh-cqc8](https://osv.dev/vulnerability/GHSA-968p-4wvh-cqc8) | MEDIUM | CVE-2025-27789 | Babel has inefficient RegExp complexity in generated code with .replace when transpiling named capturing groups | CVSS_V3: CVSS:3.1/AV:L/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-98mx-cjp9-gchm](https://osv.dev/vulnerability/GHSA-98mx-cjp9-gchm) | LOW | CVE-2026-113797 | Electron: Extension tabs.query returns tab URLs and titles without the tabs permission | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N |
| [GHSA-997q-gvvc-jwmx](https://osv.dev/vulnerability/GHSA-997q-gvvc-jwmx) | MEDIUM | CVE-2026-114121 | Electron: openExternal permission requests can be attributed to the wrong origin | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:C/C:L/I:L/A:N |
| [GHSA-9fm3-97px-rjf6](https://osv.dev/vulnerability/GHSA-9fm3-97px-rjf6) | MEDIUM | CVE-2026-109932 | undici WebSocket client vulnerable to Denial of Service via unbounded automatic PONG buffering | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-c6fg-446q-cg94](https://osv.dev/vulnerability/GHSA-c6fg-446q-cg94) | MEDIUM | CVE-2026-114103 | adm-zip: getDataAsync() bypasses the maxOutputLength size guard enforced by the synchronous getData() path | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L |
| [GHSA-ch52-4w7c-c8xp](https://osv.dev/vulnerability/GHSA-ch52-4w7c-c8xp) | HIGH | CVE-2026-93748 | http-cache-semantics max-stale handling can disclose cross-user cached responses | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N; CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:N/VA:N/SC:N/SI:N/SA:N |
| [GHSA-chgr-c6px-7xpp](https://osv.dev/vulnerability/GHSA-chgr-c6px-7xpp) | MEDIUM | RUSTSEC-2026-0177 | PyO3 has a missing `Sync` bound on `PyCFunction::new_closure` closures | CVSS_V4: CVSS:4.0/AV:N/AC:H/AT:P/PR:N/UI:N/VC:L/VI:L/VA:L/SC:N/SI:N/SA:N |
| [GHSA-f72v-6w45-q472](https://osv.dev/vulnerability/GHSA-f72v-6w45-q472) | MEDIUM | CVE-2026-109841 | undici vulnerable to Subresource Integrity bypass via non-lowercase algorithm tokens | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:H/A:N |
| [GHSA-fw47-9c62-h3vw](https://osv.dev/vulnerability/GHSA-fw47-9c62-h3vw) | MEDIUM | CVE-2026-113800 | Electron: HID and USB permission checks can be attributed to the wrong origin | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:C/C:L/I:L/A:N |
| [GHSA-fxqj-rqcc-2cmp](https://osv.dev/vulnerability/GHSA-fxqj-rqcc-2cmp) | MEDIUM | CVE-2026-69153 | PostCSS: incomplete fix of GHSA-6g55-p6wh-862q — attacker-controlled sourceMappingURL reads arbitrary .map files when `from` is unset | CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:P/PR:N/UI:N/VC:L/VI:N/VA:N/SC:N/SI:N/SA:N |
| [GHSA-gcqx-w48m-w2h4](https://osv.dev/vulnerability/GHSA-gcqx-w48m-w2h4) | LOW | CVE-2026-93753 | deepmerge mergeObject() allows prototype poisoning of returned objects | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:L/A:N; CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:L/VA:N/SC:N/SI:N/SA:N/E:U |
| [GHSA-gh4j-gqv2-49f6](https://osv.dev/vulnerability/GHSA-gh4j-gqv2-49f6) | MEDIUM | CVE-2026-41650 | fast-xml-parser XMLBuilder: XML Comment and CDATA Injection via Unescaped Delimiters | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N |
| [GHSA-h3v5-h239-pg29](https://osv.dev/vulnerability/GHSA-h3v5-h239-pg29) | LOW | CVE-2026-114124 | Electron: Off-screen rendering on macOS trusts GPU-supplied frame geometry | CVSS_V3: CVSS:3.1/AV:L/AC:H/PR:H/UI:N/S:C/C:L/I:N/A:L |
| [GHSA-j5f4-cc29-5x44](https://osv.dev/vulnerability/GHSA-j5f4-cc29-5x44) | HIGH | CVE-2026-102282 | adm-zip extraction preserves SUID/SGID bits from untrusted ZIPs -> local privilege escalation | CVSS_V3: CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N |
| [GHSA-jr5w-w6x2-84p9](https://osv.dev/vulnerability/GHSA-jr5w-w6x2-84p9) | HIGH | CVE-2026-115570 | Electron: Mitigation for compromised eMudhra intermediate certificates | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:N |
| [GHSA-mrhx-c7xc-m3w5](https://osv.dev/vulnerability/GHSA-mrhx-c7xc-m3w5) | HIGH | CVE-2023-54404 | Zod can exhaust memory while validating oversized constrained collections | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H; CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:P/PR:N/UI:N/VC:N/VI:N/VA:H/SC:N/SI:N/SA:N |
| [GHSA-p634-w6r4-rjp2](https://osv.dev/vulnerability/GHSA-p634-w6r4-rjp2) | MEDIUM | CVE-2026-114104 | adm-zip: Duplicate ZIP entry names: getEntry() and extractAllTo() resolve to different content | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:H/A:N |
| [GHSA-pph8-gcv7-4qj5](https://osv.dev/vulnerability/GHSA-pph8-gcv7-4qj5) | LOW | RUSTSEC-2025-0020 | PyO3 Risk of buffer overflow in `PyString::from_object` | CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:P/PR:N/UI:N/VC:N/VI:N/VA:L/SC:N/SI:N/SA:N/E:P |
| [GHSA-qmv3-fv6v-rmhq](https://osv.dev/vulnerability/GHSA-qmv3-fv6v-rmhq) | HIGH | CVE-2026-102677 | Electron: Sandboxed preload code cache can be poisoned by a compromised renderer | CVSS_V3: CVSS:3.1/AV:L/AC:H/PR:L/UI:N/S:C/C:H/I:H/A:H |
| [GHSA-qrxm-fqhp-jwvm](https://osv.dev/vulnerability/GHSA-qrxm-fqhp-jwvm) | HIGH | CVE-2026-87018 | undici vulnerable to Denial of Service via TLS 1.2 certificate-changing renegotiation | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-qx2v-qp2m-jg93](https://osv.dev/vulnerability/GHSA-qx2v-qp2m-jg93) | MEDIUM | CVE-2026-41305 | PostCSS has XSS via Unescaped </style> in its CSS Stringify Output | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N |
| [GHSA-r28c-9q8g-f849](https://osv.dev/vulnerability/GHSA-r28c-9q8g-f849) | HIGH | CVE-2026-73646 | PostCSS: Path Traversal in Previous Source Map Auto-Loading (sourceMappingURL) leads to Arbitrary .map File Disclosure | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N |
| [GHSA-rcw4-f5rp-g42v](https://osv.dev/vulnerability/GHSA-rcw4-f5rp-g42v) | HIGH | CVE-2026-92000 | adm-zip: Decompression-bomb protection (fix for CVE-2026-39244) can be bypassed by declaring uncompressed size as 0 | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [GHSA-vwc7-r8mq-g2x9](https://osv.dev/vulnerability/GHSA-vwc7-r8mq-g2x9) | MEDIUM | CVE-2026-76845 | adm-zip extraction follows destination symlinks, allowing arbitrary file overwrite | CVSS_V3: CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:C/C:N/I:H/A:N; CVSS_V4: CVSS:4.0/AV:L/AC:L/AT:N/PR:L/UI:N/VC:N/VI:H/VA:N/SC:N/SI:N/SA:N |
| [GHSA-w3rx-r6r6-pgpr](https://osv.dev/vulnerability/GHSA-w3rx-r6r6-pgpr) | HIGH | CVE-2025-71330 | image-size: ICNS parser allows denial of service through an infinite loop | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H; CVSS_V4: CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:N/VA:H/SC:N/SI:N/SA:N/E:X/CR:X/IR:X/AR:X/MAV:X/MAC:X/MAT:X/MPR:X/MUI:X/MVC:X/MVI:X/MVA:X/MSC:X/MSI:X/MSA:X/S:X/AU:X/R:X/V:X/RE:X/U:X |
| [GHSA-w74x-v6f9-j3xg](https://osv.dev/vulnerability/GHSA-w74x-v6f9-j3xg) | MEDIUM | CVE-2026-110306 | fast-uri vulnerable to URI scheme spoofing via percent-encoded scheme decoding | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N |
| [GHSA-wjr9-j795-jxcr](https://osv.dev/vulnerability/GHSA-wjr9-j795-jxcr) | MEDIUM | CVE-2026-110180 | fast-uri vulnerable to host confusion via unchecked WHATWG host canonicalization | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:L/I:L/A:N |
| [GHSA-x562-297c-g43g](https://osv.dev/vulnerability/GHSA-x562-297c-g43g) | MEDIUM | CVE-2026-113798 | Electron: Pointer lock permission requests can be attributed to the wrong frame | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:L |
| [GHSA-xhq7-xmqj-769h](https://osv.dev/vulnerability/GHSA-xhq7-xmqj-769h) | LOW | CVE-2026-110095 | fast-uri vulnerable to improper path equivalence in relative reference normalization | CVSS_V3: CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:L/A:N |
| [GHSA-xjj6-f33q-929c](https://osv.dev/vulnerability/GHSA-xjj6-f33q-929c) | HIGH | CVE-2026-109869 | undici vulnerable to Denial of Service via unhandled error on excessive content-encodings | CVSS_V3: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H |
| [RUSTSEC-2025-0020](https://osv.dev/vulnerability/RUSTSEC-2025-0020) | UNKNOWN | GHSA-pph8-gcv7-4qj5 | Risk of buffer overflow in `PyString::from_object` | UNKNOWN (absent) |
| [RUSTSEC-2026-0177](https://osv.dev/vulnerability/RUSTSEC-2026-0177) | UNKNOWN | GHSA-chgr-c6px-7xpp | Missing `Sync` bound on `PyCFunction::new_closure` closures | UNKNOWN (absent) |

## Deduplicated affected pairs

| Ecosystem | Package | Installed | Advisory | Severity | Known fixed events | Relationship |
| --- | --- | --- | --- | --- | --- | --- |
| crates.io | pyo3 | 0.23.5 | GHSA-36hh-v3qg-5jq4 | HIGH | 0.29.0 | direct |
| crates.io | pyo3 | 0.23.5 | GHSA-chgr-c6px-7xpp | MEDIUM | 0.29.0 | direct |
| crates.io | pyo3 | 0.23.5 | GHSA-pph8-gcv7-4qj5 | LOW | 0.24.1 | direct |
| crates.io | pyo3 | 0.23.5 | RUSTSEC-2025-0020 | UNKNOWN | 0.24.1 | direct |
| crates.io | pyo3 | 0.23.5 | RUSTSEC-2026-0177 | UNKNOWN | 0.29.0 | direct |
| npm | @babel/core | 7.25.2 | GHSA-4x5r-pxfx-6jf8 | LOW | 7.29.6, 8.0.0-rc.6 | direct |
| npm | @babel/runtime | 7.25.0 | GHSA-968p-4wvh-cqc8 | MEDIUM | 7.26.10, 8.0.0-alpha.17 | direct |
| npm | adm-zip | 0.6.0 | GHSA-7q85-xj36-vmfc | HIGH | 0.6.1 | direct |
| npm | adm-zip | 0.6.0 | GHSA-8238-w5pm-2374 | HIGH | 0.6.1 | direct |
| npm | adm-zip | 0.6.0 | GHSA-c6fg-446q-cg94 | MEDIUM | 0.6.1 | direct |
| npm | adm-zip | 0.6.0 | GHSA-j5f4-cc29-5x44 | HIGH | 0.6.1 | direct |
| npm | adm-zip | 0.6.0 | GHSA-p634-w6r4-rjp2 | MEDIUM | 0.6.1 | direct |
| npm | adm-zip | 0.6.0 | GHSA-rcw4-f5rp-g42v | HIGH | 0.6.1 | direct |
| npm | adm-zip | 0.6.0 | GHSA-vwc7-r8mq-g2x9 | MEDIUM | UNKNOWN / no fixed event | direct |
| npm | body-parser | 1.20.8 | GHSA-27r3-jxmh-p828 | HIGH | 1.20.9, 2.4.0 | transitive |
| npm | deepmerge | 4.3.1 | GHSA-gcqx-w48m-w2h4 | LOW | UNKNOWN / no fixed event | transitive |
| npm | electron | 43.4.1 | GHSA-2p8c-gr87-2676 | MEDIUM | 42.11.3, 43.7.0, 44.3.0, 45.0.0-alpha.6 | direct |
| npm | electron | 43.4.1 | GHSA-3qxv-5xgr-q7w9 | LOW | 42.11.6, 43.7.3, 44.4.3, 45.0.0-alpha.9 | direct |
| npm | electron | 43.4.1 | GHSA-98mx-cjp9-gchm | LOW | 42.11.1, 43.5.1, 44.1.1, 45.0.0-alpha.4 | direct |
| npm | electron | 43.4.1 | GHSA-997q-gvvc-jwmx | MEDIUM | 42.11.3, 43.7.0, 44.3.0, 45.0.0-alpha.5 | direct |
| npm | electron | 43.4.1 | GHSA-fw47-9c62-h3vw | MEDIUM | 42.11.3, 43.7.0, 44.3.0, 45.0.0-alpha.5 | direct |
| npm | electron | 43.4.1 | GHSA-h3v5-h239-pg29 | LOW | 42.11.6, 43.7.3, 44.4.3, 45.0.0-alpha.9 | direct |
| npm | electron | 43.4.1 | GHSA-jr5w-w6x2-84p9 | HIGH | 42.11.16, 43.8.1, 44.9.1, 45.0.0-beta.6 | direct |
| npm | electron | 43.4.1 | GHSA-qmv3-fv6v-rmhq | HIGH | 42.10.0, 43.5.0, 44.0.0-beta.6 | direct |
| npm | electron | 43.4.1 | GHSA-x562-297c-g43g | MEDIUM | 42.11.9, 43.7.6, 44.5.0, 45.0.0-alpha.13 | direct |
| npm | fast-uri | 3.1.8 | GHSA-333x-w4r6-6crp | LOW | 2.4.8, 3.1.9, 4.2.3 | transitive |
| npm | fast-uri | 3.1.8 | GHSA-w74x-v6f9-j3xg | MEDIUM | 2.4.8, 3.1.9, 4.2.3 | transitive |
| npm | fast-uri | 3.1.8 | GHSA-wjr9-j795-jxcr | MEDIUM | 2.4.8, 3.1.9, 4.2.3 | transitive |
| npm | fast-uri | 3.1.8 | GHSA-xhq7-xmqj-769h | LOW | 2.4.8, 3.1.9, 4.2.3 | transitive |
| npm | fast-xml-parser | 4.5.7 | GHSA-gh4j-gqv2-49f6 | MEDIUM | 5.7.0 | transitive |
| npm | http-cache-semantics | 4.2.0 | GHSA-ch52-4w7c-c8xp | HIGH | UNKNOWN / no fixed event | transitive |
| npm | image-size | 1.2.1 | GHSA-5p2g-fcmc-qvqq | HIGH | 2.0.3 | transitive |
| npm | image-size | 1.2.1 | GHSA-w3rx-r6r6-pgpr | HIGH | 2.0.3 | transitive |
| npm | node-forge | 1.4.0 | GHSA-86w9-cpqp-85rv | HIGH | UNKNOWN / no fixed event | transitive |
| npm | postcss | 8.4.49 | GHSA-6g55-p6wh-862q | HIGH | 8.5.12 | transitive |
| npm | postcss | 8.4.49 | GHSA-fxqj-rqcc-2cmp | MEDIUM | 8.5.23 | transitive |
| npm | postcss | 8.4.49 | GHSA-qx2v-qp2m-jg93 | MEDIUM | 8.5.10 | transitive |
| npm | postcss | 8.4.49 | GHSA-r28c-9q8g-f849 | HIGH | 8.5.18 | transitive |
| npm | undici | 6.28.1 | GHSA-2m5v-pch6-74hx | HIGH | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 6.28.1 | GHSA-4qv8-2p9j-2685 | MEDIUM | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 6.28.1 | GHSA-9fm3-97px-rjf6 | MEDIUM | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 6.28.1 | GHSA-qrxm-fqhp-jwvm | HIGH | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 6.28.1 | GHSA-xjj6-f33q-929c | HIGH | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-2m5v-pch6-74hx | HIGH | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-4qv8-2p9j-2685 | MEDIUM | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-4w7w-5xrc-9h26 | HIGH | 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-55wc-3pg8-jfw6 | MEDIUM | 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-9fm3-97px-rjf6 | MEDIUM | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-f72v-6w45-q472 | MEDIUM | 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-qrxm-fqhp-jwvm | HIGH | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | undici | 7.29.1 | GHSA-xjj6-f33q-929c | HIGH | 6.29.1, 7.30.1, 8.12.2 | transitive |
| npm | zod | 3.25.76 | GHSA-mrhx-c7xc-m3w5 | HIGH | 4.6.6 | transitive |

## Usage, directness and licenses

Directness is relative to the owning project manifest. npm root declarations, exact versioned lock nodes and resolved lock dependency edges were inspected. PyO3 is direct in Cargo.toml [dependencies]. Lock production flags establish graph classification, not proof of APK/EXE inclusion. No affected dependency was established as test-only. A test import does not erase production use.

| Manifest | Package/version | Relationship | Usage | Exact lock license |
| --- | --- | --- | --- | --- |
| examples/rust-expo-jsi/package-lock.json | deepmerge@4.3.1 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-expo-jsi/package-lock.json | image-size@1.2.1 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-expo-jsi/package-lock.json | node-forge@1.4.0 | transitive | example production dependency graph; deployment UNKNOWN | (BSD-3-Clause OR GPL-2.0) |
| examples/rust-expo-jsi/package-lock.json | postcss@8.4.49 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-expo-jsi/package-lock.json | zod@3.25.76 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-expo-turbo/package-lock.json | deepmerge@4.3.1 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-expo-turbo/package-lock.json | image-size@1.2.1 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-expo-turbo/package-lock.json | node-forge@1.4.0 | transitive | example production dependency graph; deployment UNKNOWN | (BSD-3-Clause OR GPL-2.0) |
| examples/rust-expo-turbo/package-lock.json | postcss@8.4.49 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-expo-turbo/package-lock.json | zod@3.25.76 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-python/Cargo.lock | pyo3@0.23.5 | direct | example Python extension production dependency; deployment UNKNOWN | UNKNOWN (not in Cargo.lock) |
| examples/rust-react-native-turbo/package-lock.json | @babel/core@7.25.2 | direct | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-react-native-turbo/package-lock.json | @babel/runtime@7.25.0 | direct | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-react-native-turbo/package-lock.json | body-parser@1.20.8 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-react-native-turbo/package-lock.json | deepmerge@4.3.1 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| examples/rust-react-native-turbo/package-lock.json | fast-xml-parser@4.5.7 | transitive | example production dependency graph; deployment UNKNOWN | MIT |
| windows-host/package-lock.json | adm-zip@0.6.0 | direct | production dependency graph | MIT |
| windows-host/package-lock.json | electron@43.4.1 | direct | dev declaration; shipped production runtime | MIT |
| windows-host/package-lock.json | fast-uri@3.1.8 | transitive | development/build | BSD-3-Clause |
| windows-host/package-lock.json | http-cache-semantics@4.2.0 | transitive | development/build | BSD-2-Clause |
| windows-host/package-lock.json | undici@6.28.1 | transitive | development/build | MIT |
| windows-host/package-lock.json | undici@7.29.1 | transitive | development/build | MIT |

License review is based on exact lock declarations, not artifact notice verification or legal approval. npm affected nodes declare MIT, BSD-2-Clause or BSD-3-Clause as recorded. Preserve copyright/license texts and BSD disclaimers. PyO3 license/distribution closure is UNKNOWN here: Cargo.lock lacks license metadata and verified source license text was not established. Electron MIT metadata does not cover all Chromium/third-party distribution obligations. Replacement-version licenses and recursive notice changes must be reviewed before redistribution.

### Dependency paths

These are manifest graph paths, not dynamic reachability proofs. Development root edges are included.
- `examples/rust-expo-jsi/package-lock.json`: `expo → @expo/config → deepmerge [node_modules/deepmerge]` → `deepmerge@4.3.1`.
- `examples/rust-expo-jsi/package-lock.json`: `expo → @expo/metro → metro → image-size [node_modules/image-size]` → `image-size@1.2.1`.
- `examples/rust-expo-jsi/package-lock.json`: `expo → @expo/cli → node-forge [node_modules/node-forge]` → `node-forge@1.4.0`.
- `examples/rust-expo-jsi/package-lock.json`: `expo → @expo/metro-config → postcss [node_modules/postcss]` → `postcss@8.4.49`.
- `examples/rust-expo-jsi/package-lock.json`: `expo → @expo/cli → @expo/mcp-tunnel → zod [node_modules/zod]` → `zod@3.25.76`.
- `examples/rust-expo-turbo/package-lock.json`: `expo → @expo/config → deepmerge [node_modules/deepmerge]` → `deepmerge@4.3.1`.
- `examples/rust-expo-turbo/package-lock.json`: `expo → @expo/metro → metro → image-size [node_modules/image-size]` → `image-size@1.2.1`.
- `examples/rust-expo-turbo/package-lock.json`: `expo → @expo/cli → node-forge [node_modules/node-forge]` → `node-forge@1.4.0`.
- `examples/rust-expo-turbo/package-lock.json`: `expo → @expo/metro-config → postcss [node_modules/postcss]` → `postcss@8.4.49`.
- `examples/rust-expo-turbo/package-lock.json`: `expo → @expo/cli → @expo/mcp-tunnel → zod [node_modules/zod]` → `zod@3.25.76`.
- `examples/rust-python/Cargo.lock`: `Cargo.toml [dependencies] → pyo3` → `pyo3@0.23.5`.
- `examples/rust-react-native-turbo/package-lock.json`: `@babel/core [node_modules/@babel/core]` → `@babel/core@7.25.2`.
- `examples/rust-react-native-turbo/package-lock.json`: `@babel/runtime [node_modules/@babel/runtime]` → `@babel/runtime@7.25.0`.
- `examples/rust-react-native-turbo/package-lock.json`: `@react-native-community/cli → @react-native-community/cli-server-api → body-parser [node_modules/body-parser]` → `body-parser@1.20.8`.
- `examples/rust-react-native-turbo/package-lock.json`: `@react-native-community/cli → deepmerge [node_modules/deepmerge]` → `deepmerge@4.3.1`.
- `examples/rust-react-native-turbo/package-lock.json`: `@react-native-community/cli-platform-android → @react-native-community/cli-config-android → fast-xml-parser [node_modules/fast-xml-parser]` → `fast-xml-parser@4.5.7`.
- `windows-host/package-lock.json`: `adm-zip [node_modules/adm-zip]` → `adm-zip@0.6.0`.
- `windows-host/package-lock.json`: `electron [node_modules/electron]` → `electron@43.4.1`.
- `windows-host/package-lock.json`: `electron-builder → app-builder-lib → ajv → fast-uri [node_modules/fast-uri]` → `fast-uri@3.1.8`.
- `windows-host/package-lock.json`: `electron-builder → app-builder-lib → @electron/get → got → cacheable-request → http-cache-semantics [node_modules/http-cache-semantics]` → `http-cache-semantics@4.2.0`.
- `windows-host/package-lock.json`: `electron-builder → app-builder-lib → @electron/rebuild → node-gyp → undici [node_modules/node-gyp/node_modules/undici]` → `undici@6.28.1`.
- `windows-host/package-lock.json`: `electron → @electron/get → undici [node_modules/undici]` → `undici@7.29.1`.

## Platform impact and priority

1. **P1 — Windows production ZIP parsing.** `windows-host/package.json` directly declares and explicitly packages adm-zip. `windows-host/payload.cjs:843` constructs AdmZip, enumerates entries and calls synchronous `entry.getData()` before writing. The current 500 MB cap sums declared sizes; it does not prove protection against declared-zero decompression bombs (GHSA-rcw4-f5rp-g42v) or allocation DoS (GHSA-7q85-xj36-vmfc). This is a source-reachable parsing surface; exploit execution is NOT_RUN. Version 0.6.1 is a candidate fixed boundary for six IDs but does not clear GHSA-vwc7-r8mq-g2x9. Async-only flaws require other code paths; reviewed extraction uses synchronous APIs. SUID/SGID preservation is POSIX-oriented and the custom writer avoids bulk extraction; its Windows applicability is NOT_ESTABLISHED. Duplicate-entry and symlink behavior still need explicit review. Keep every finding open.

2. **P1 — Windows shipped Electron runtime.** A direct devDependency supplies the production packaged runtime. Prioritize high GHSA-jr5w-w6x2-84p9 (certificate trust) and GHSA-qmv3-fv6v-rmhq (sandboxed preload cache). Candidate same-major 43.8.1 exceeds the recorded 43.x boundaries, but release availability and compatibility are not verified. `main.cjs` sets nodeIntegration=false, contextIsolation=true, sandbox=true and webSecurity=true. These controls are not proof of immunity. No preload option was seen in the reviewed BrowserWindow block; specific preload exploit reachability remains UNKNOWN. The macOS off-screen advisory GHSA-h3v5-h239-pg29 has no demonstrated Windows path. Extension/HID/USB/pointer/window/frame conditions require individual review. Actual EXE module inventory and physical Windows acceptance are UNKNOWN.

3. **P2 — Windows build-time network clients.** undici 6.28.1/7.29.1, http-cache-semantics and fast-uri are development/build transitives in the Windows toolchain. Their TLS/cache/DoS findings can matter for build/download inputs. They are not explicitly included in packaged files, but dev classification is not proof of safety or absence of embedded runtime copies. Candidate branch boundaries are undici 6.29.1 and 7.30.1, fast-uri 3.1.9; http-cache-semantics has no fixed event. Affected API use and attacker input reachability are UNKNOWN.

4. **P2 — Android-related examples and developer machines.** Expo JSI/Turbo and React Native Turbo locks are affected, not a resolved inventory of the main Android app. Prioritize PostCSS source-map file reads, image-size parser loops, body-parser compressed-body DoS, node-forge signature verification and Zod memory exhaustion where untrusted projects/build inputs/server requests reach them. Shipping and physical Android execution are UNKNOWN. Babel runtime can affect generated application code despite its direct dev declaration. Build/server and device-runtime paths need separate tracing. No Android SDK, AGP, signing or APK modification is needed to retain these findings.

5. **P2 — Python extension example.** Direct PyO3 0.23.5 has five advisory IDs, including RUSTSEC/GHSA alias overlap. Candidate 0.29.0 covers recorded boundaries; API migration, abi3-py312 compatibility and vulnerable iterator/closure/string API use remain UNKNOWN. This example does not prove main Android or Windows runtime exposure.

No CRITICAL label occurs in these 47 IDs. No production exploit was demonstrated. Source-reachable Windows ZIP parsing and the shipped vulnerable Electron runtime justify priority without claiming exploitability. Main Android Gradle/application/plugin transitives remain unresolved because SDK/resolved inventory evidence is missing. Maven example scan findings cannot clear the main app. Generated projects, native runtimes and actual APK/EXE contents remain coverage gaps.

## No fixed event and unresolved findings

| Installed package | Advisory | Status |
| --- | --- | --- |
| adm-zip@0.6.0 | GHSA-vwc7-r8mq-g2x9 | Open; patch availability UNKNOWN in snapshot |
| deepmerge@4.3.1 | GHSA-gcqx-w48m-w2h4 | Open; patch availability UNKNOWN in snapshot |
| http-cache-semantics@4.2.0 | GHSA-ch52-4w7c-c8xp | Open; patch availability UNKNOWN in snapshot |
| node-forge@1.4.0 | GHSA-86w9-cpqp-85rv | Open; patch availability UNKNOWN in snapshot |

All other installed pairs also remain open despite candidate fixed events. No suppression, ignore, risk acceptance or clearance was applied.

## Remediation and update compatibility plan

1. Preserve original evidence and close inventory gaps before claiming clearance. Resolve Android application/plugin dependencies in the separately approved SDK environment, inventory exact Windows bundled runtime modules and establish which examples deploy. Record entry points and input trust boundaries.
2. In a separately authorized dependency-change worktree, review adm-zip and Electron first. Verify releases, changelogs, artifact hashes, platform/engine support and recursive license notices. Add bounded tests for zero-size bombs, actual output-byte limits, malformed ZIPs, duplicates, symlinks and applicable async errors; retain path/payload integrity regressions. Test Electron storage, permissions, sandbox, navigation, portable packaging and physical Windows behavior. Preserve publisher/signing and V9 protections.
3. Update transitives through owning parents. Review supported Expo/React Native/Metro versions rather than force overrides. image-size 1→2, fast-xml-parser 4→5, Zod 3→4 and PyO3 0.23→0.29 need API/migration checks. PostCSS 8.5.23 is the latest listed boundary among its four IDs; compatibility is still UNKNOWN. Babel/core 7.29.6 and Babel/runtime 7.26.10 are separate candidate branches. Do not select prerelease 8.x merely because a fixed event lists it.
4. For no-fixed-event records, review authoritative upstream issues/advisories and safe parent alternatives. Keep records open and document any input limits or feature restrictions with behavioral evidence. A mitigation does not clear the vulnerable package. Review medium, low and unknown findings as well as high ones.
5. After approved changes, rerun the real offline scanner with verified fresh complete exports, original no-ignore policy and detector controls. Compare exact pairs/aliases, replacement dependency/license closure and unresolved records. Run focused/full applicable regressions and separate Android/Windows acceptance. Only actual evidence can close findings; missing coverage remains UNKNOWN.

## Validation and final status

Read-only reconciliation verified all 22 current source hashes, latest scanner advisory set, exact installed coordinates, direct declarations, npm dependency paths and license fields. This documentation-only task did not run builds/tests or change their results. Live upstream release/license state was not checked and is UNKNOWN. Existing security/runtime gates remain authoritative.

```text
OSV_ADVISORIES_TOTAL=47
OSV_AFFECTED_PACKAGE_PAIRS=52
OSV_CRITICAL=0
OSV_HIGH=19
OSV_MEDIUM=18
OSV_LOW=8
OSV_UNKNOWN=2
OSV_DIRECT=23
OSV_TRANSITIVE=29
OSV_FIX_AVAILABLE=48
OSV_UNPATCHED=4
OSV_REPORT=docs/engineering/v10/osv-triage-report.md
V10_OVERALL=BLOCKED
NO_PUSH=YES
```

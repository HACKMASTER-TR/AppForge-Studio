# Download telemetry corrections — 2026-10-19

Status: implemented corrections; overall universal transfer coverage remains PARTIAL.
Base: 111be1994faae7411691305e2c5a4cb027215e3a.
No GitHub writes, CI dispatch, release or phone install.

## Real changes

- Node fetch/undici body bytes are now observed in addition to HTTP/HTTPS response emission. Known Content-Length gives a real total; chunked/unknown bodies retain null total. Node 22 needs its existing onData callback observed because its bundled undici does not emit the newer diagnostic body event. Original callback arguments/return/backpressure remain delegated. No response body is duplicated or proactively consumed. Unsupported observation does not infer completion from a header.
- Node HTTP interrupted bodies emit structured errors.
- Curl uses full Content-Range total when observing resumed files; interrupted curl exits retain finished=false and their actual exit code. HTTPS, existing downloader pin/Range validation and checksum controls are unchanged.
- Kotlin translates structured transfer failures into ERROR without ETA/throughput. APT staged-file samples cannot produce network speed, percentage or ETA. Wire/socket measurements are explicitly distinguished from payload totals in user-facing details.

## Coverage and honest bounds

| Path | Measured evidence |
|---|---|
| Native pinned archives, rootfs, broker-backed SDK/JDK/Gradle/NDK/CMake/APT .deb archives | Existing actual native transfer bytes/known totals and checksum/resume boundaries preserved |
| Gradle/Maven supported typed resource events | Existing body counters/available totals preserved |
| npm/core HTTP | Existing response bytes plus structured interruption |
| Node fetch/undici / Expo clients using those paths | New actual body counters, known/unknown totals |
| Python/pip standard HTTP / Chaquopy supported observers | Existing actual read/readinto measurements preserved |
| SDK JVM sockets | Existing actual socket/wire counts, unknown payload total |
| APT signed index refresh, unobservable native/custom clients | Indeterminate; no invented payload total, network speed or ETA |

No claim of universal APT index byte instrumentation, arbitrary Maven/npm plugin client coverage, whole-module percent, new pause/resume, or physical Android acceptance. These cannot be inferred from per-transfer adapters. Full coverage would require separate package-manager transport integration and compatibility verification; TLS interception or parsing arbitrary console percentages was not introduced.

## Verification record

The first Node 22 fetch test failed (0 observed vs 32000 delivered). Scoped Node 22 callback observation corrected it; retest passed. The first full quality run lacked the required JDK/Kotlin paths and failed environmental checks; rerun uses the existing verified tool installations. Exact final counts and logs are recorded in the delivery verification.json. Physical Android and new GitHub CI remain NOT_RUN.

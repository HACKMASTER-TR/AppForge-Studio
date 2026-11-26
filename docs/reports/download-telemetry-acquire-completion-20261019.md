# Download telemetry — acquire integration completion (2026-10-19)

Base: 934bd4ce3130f9f755668ba53b968da9486d7606. This continues, rather than resets,
the preceding V3 and telemetry commits. All work is local; no GitHub write,
workflow dispatch, release, Play or device installation is authorized or performed.

## Corrections beyond the previous partial delivery

1. APT repository index downloads now have transfer-level payload measurements.
   Original APT HTTP/HTTPS methods are wrapped by a transparent Acquire-protocol
   relay. Start/Done/Failure, Size, Resume-Point and the actual destination are
   correlated before sampling written payload bytes. The old directory-size
   aggregate is not presented as network traffic when this adapter is active.
2. Real-worker loopback tests cover known length, unknown/chunked length, interrupted
   bodies, HTTP 206 resume, HTTP 200 restart after a Range request, and HTTP 304
   cache hits. Original worker behavior and exit status are preserved. Cache hits
   emit no claimed network throughput or download percentage. Completion means
   transfer completion only; APT still verifies signatures/hashes before module READY.
3. Independent atomic channels prevent parallel Gradle/Maven, Node HTTP/fetch,
   Python HTTP, curl and SDK JVM events from overwriting one another. The bounded
   common Kotlin reader is integrated with module installation and explicitly
   approved project dependency preparation. Every channel has its own rate meter.
4. Resume baselines are excluded from average/instantaneous rate and ETA. Old
   module-command channels are cleared without recursive deletion or touching
   source/cache/signing files. Unknown or staged/cached byte metrics cannot
   manufacture percent, rate or ETA.
5. Managed Gradle invocations explicitly disable configuration-cache replay to
   preserve resource events. Task and build caches remain independent. Gradle
   resource failures now carry structured error state instead of finished=true.
6. A scoped child-process curl shim is installed only when /usr/bin/curl actually
   exists. It delegates to that absolute binary and safely forwards without
   telemetry; it cannot recurse through PATH or pretend curl is installed in a
   fresh Node module. Its shell observer now retains an independent channel.

## Sources and security boundaries

APT continues using its original binaries, repository metadata signatures,
HTTPS peer/hostname verification, archive hashes, locking and cache behavior.
The adapter does not implement a substitute HTTP client or change TLS settings.
No proxy interception, insecure flags or console-percent parsing is introduced.
Request URI/configuration stays only in the worker's private temporary mapping;
telemetry contains a sanitized file label and counters, not URL credentials/query.
The _apt sandbox is not disabled to allow reporting: only an operation-private
sticky event spool accepts worker-owned atomic event files. The common reader
rejects symlinks, oversized records and unrelated filenames, and bounds memory.

Installer recipe hashes include both acquire observer assets. The image's mawk
supports explicitly line-buffered protocol forwarding; if it is absent the
original transport remains intact and an unavailable event is emitted. Ubuntu
24.04 real-image tests exercise the actual bundled prerequisite.

## Verification evidence

An initial protocol relay stalled because ordinary awk buffered worker pipes.
Line-oriented mawk interactive forwarding fixed the stall. The standalone APT
method's normal exit-on-command-EOF is 100; direct original-binary comparison
confirmed this before correcting the fixture expectation. Cached HTTP 304 does
not deliver a new body and is tested separately from downloaded-file equality.
An integration retest caught a bare return propagating the observer-disable condition as failure under set -e; explicit success and a fail-fast shell regression corrected it. No failing test was removed to obtain success.

Actual Ubuntu signed-index refresh and actual Gradle/Maven 9.3.1 and 8.14.3
online-resolution/server-stopped offline-rebuild fixtures were executed. The
APT live sample JSON, fixture logs, Android/JUnit XML, complete quality output
and exact final results are delivered in verification.json and the evidence ZIP.
The original V3 tests remain. New tests validate independent channels, resumed
rates, cache-hit semantics, interruption, symlink/foreign-file handling and curl
fallback without PATH recursion.

## Scope and remaining acceptance boundaries

The managed standard paths now have observers: native pinned/rootfs/toolchain
archives and brokered APT .deb files; original APT index HTTP(S); supported Gradle
resource/Maven transfers; core Node HTTP/fetch/undici including the pinned Node22
fallback; Python stdlib HTTP; curl output files; SDK JVM socket reads. For known
payload totals, UI percentage/rate/average/ETA are based on those actual counters.

SDK JVM metadata may provide only wire/socket bytes; unknown HTTP lengths remain
unknown. No total or ETA is invented. This is not a promise to observe arbitrary
third-party native/custom HTTP code or bodies never passing through these APIs.
No whole-module percentage is inferred from the currently active transfer. A
transfer at 100 percent is not a completed installation.

Physical Android 12–16 foreground/background/process-death acceptance is NOT_RUN.
New GitHub CI is NOT_RUN_USER_PROHIBITED. These facts limit overall product/device
acceptance independently of the host transfer integration tests. Existing APKs
are not newly accepted for upgrading T21. No new phone installation is performed.

Small-transfer completion is retained in a terminal channel immediately at the
APT protocol boundary, not only on the periodic timer. A late timer cannot reopen
a terminal transfer. A preliminary full quality run exposed a stale generated
Second Brain snapshot while development continued; the final source snapshot is
regenerated and checked before the final full rerun. That initial failure is
retained in evidence rather than silently discarded.

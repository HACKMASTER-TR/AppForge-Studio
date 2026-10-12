# Fast Web 535 — V15 local static AAB development signing candidate

Status: **CANDIDATE ONLY; Android CI and physical acceptance are mandatory.**

Scope: local static HTML, CSS and JavaScript projects only. The original fast
APK route is left as-is. Explicit AAB or BOTH uses the V14 pinned protobuf
manifest and unsigned AAB writer, then JAR/CMS detached SHA-256/RSA signing.
The independent JarFile verifier consumes every payload and pins the signing
certificate obtained outside the signed archive. No SDK/Gradle/bundletool/Node
is invoked during user-facing fast builds.

**Development key limitation:** `appforge-html-aab-dev-v1` is a separate RSA
AndroidKeyStore key, which cannot be exported/recovered if the device is lost.
Do not claim it is a portable Google Play upload identity. Production-ready
upload-key management requires a separate lifecycle design (backup/import,
rotation and Play Console verification). This implementation must not be used
as evidence of production readiness.

APK+AAB atomicity: the build reports success only after both requested
artifacts pass; failed builds remove only their new, private workspace outputs.
Existing drafts, source projects, installed 534, other build engines, Play
and release configuration remain unchanged.

Required gates before merge: full Android JVM/CI compile and regressions,
signed AAB jar verification and bundletool validation on actual produced
artifact, AAB/APK/BOTH device tests in isolated package, then explicit merge
approval. Tests in a stubbed JVM alone cannot replace Android runtime proof.

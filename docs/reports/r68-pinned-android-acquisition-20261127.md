# R68 / 584 — pinned Android archive acquisition

R67 in-place physical UI evidence: nine installed modules plus bundled Fast HTML displayed READY. This does not establish a fresh install or active animation test.

R68 stages the existing pinned SDK distributions, Gradle zips, NDK, commandline tools, JDK and architecture-specific native tools before rootfs extraction and generation mutation, using existing reviewed hashes and bounded HTTPS redirect admission. No installer shell recipes or existing receipt hashes are changed. Local stage copies revalidate hashes. Android and Python warmup builds are replayed with Gradle --offline before final readiness. No arbitrary project dependency coverage or network-disconnected guest execution is asserted.

Open boundary: dynamic Maven/npm/SDK metadata may still be acquired by online preparation. The full strict download-before-install contract is not satisfied. Physical cold-install, cancellation/restart, storage and cache tests remain required. The candidate must not be treated as a production release until these gates pass.

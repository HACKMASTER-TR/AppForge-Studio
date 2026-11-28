# R67 staged acquisition — candidate 583

R66 / 582 physical UI screenshot showed all nine installed modules and bundled Fast HTML as READY. No fresh install or color-transition physical evidence yet.

R67 moves the pinned Node and Rust archive acquisition before Ubuntu extraction and generation mutation. The original pinned SHA-256 sources and local copy checks are retained. Signed R2 APT acquisition remains before staging. Rootfs byte events are forwarded. Source checksum validation is distinct from final module verification. Completed dependency units are counted only after existing functional and APT checks pass.

Remaining boundary: guest SDK, Maven, Gradle and other dynamic dependencies still have online acquisition inside installer commands (APPFORGE_DEVICE_OFFLINE=0). Do not claim complete download-before-install or offline installation. No changes to tool recipes, pinned signer, package ID or existing receipts. Physical clean install and restart tests required. R65 sizes are sequential measured storage deltas, not independent sizes or download totals.

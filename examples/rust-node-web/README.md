# Node/Web + Rust WASM

Import this as a Node/Web project. Use the Rust preparation card to download locked
Cargo dependencies, turn networks off and explicitly approve the Rust build. Select
the private prepared copy. The normal Node dependency preparation card then handles
npm cache/build consent separately. This fixture has no npm dependencies.

The real Node build emits authored HTML; AppForge independently stages the verified
Rust payload into the output. It injects no JavaScript. Source/receipt or conflicting
binary changes block packaging. Android/Windows physical execution remains NOT_RUN.

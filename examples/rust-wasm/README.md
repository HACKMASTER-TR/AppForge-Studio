# Rust → WASM → local HTML

Use a disposable reviewed COPY of this directory. Cargo build scripts and proc
macros are executable project code: review before granting consent. The tool does
not provide an execution sandbox or permit downloading dependencies.

With verified Rust 1.85.1 + wasm32 standard library installed:

```sh
python3 /workspace/runtime/rust-project.py /absolute/reviewed-copy \
  --prefix /opt/appforge-device/rust-1.85.1 \
  --target wasm32-unknown-unknown --source-execution-consent --export-wasm
```

Import the prepared folder into AppForge, select local HTML and index.html. The
appforge-rust directory contains the real binary and a source/artifact consistency
receipt. APK/AAB packaging includes WASM; HTML explicitly loads it. No JS is
injected into local or remote pages. A changed source or binary invalidates the
receipt. An existing export directory is preserved; rebuild in a fresh copy.

This is an offline artifact-preparation CLI plus a prebuilt-payload packaging
path, not an automatic in-app Rust build wizard. Android WebView execution and
Windows Portable runtime acceptance require physical tests (NOT_RUN). JNI,
PyO3/Chaquopy and JSI adapters are not implemented by this example.

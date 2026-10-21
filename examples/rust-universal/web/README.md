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

## In-app preparation (local continuation)

For an imported root Cargo + local HTML project, open the modular package screen.
Install Rust + Cargo (its internal Python prerequisite uses the signed snapshot,
without Android SDK/Node/JDK). Explicitly approve locked crate downloads. Then
turn all networks off and separately approve Rust source execution. Select the
private prepared copy only after its source/binary hashes pass. Original files
remain unchanged. Completed records are revalidated after reopening; they never
authorize automatic code/network execution. Legacy source receipt schema 1 must
be rebuilt into schema 2. Foreign files in the reserved export directory block
copy cleanup; no recursive deletion is performed.

Node-Web examples retain separate npm preparation after WASM is ready. The app
stages the verified WASM into the bundler output without injecting JavaScript.
Cargo workspaces/path/git dependencies and build inputs in ignored caches/external
locations remain unsupported. Physical Android/Windows acceptance remains NOT_RUN.

## Windows Portable

Select the local WebView/static engine and Windows Portable output after explicit
WASM preparation. The verified host and source/artifact receipt are required.
Generated root Cargo target/, VCS and dependency caches (.git/.gradle/node_modules) are excluded; original files remain unchanged.
The packager checks the web-site WASM and embedded ZIP before publishing and after
optional signing; missing, changed or unsafe payloads fail closed. No SDK/JDK is
needed to package an already prepared WASM project. This example uses
WebAssembly.instantiate(ArrayBuffer), so it does not depend on streaming MIME
behavior. Real Windows execution and Android-device packaging are NOT_RUN until
separately accepted.

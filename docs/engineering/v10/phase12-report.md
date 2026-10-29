# V10 Phase12 — supported runtime hook candidate, local only

Status: CANDIDATE_COMPILE_PREPARED. Branch `feat/v10-oss-unified-20261028`,
starting HEAD `a2c998c67b5b9753dd2773aa86833fe61ee9f1da`. No promotion or
runtime acceptance. Raw evidence and private sources: `.appforge/tmp/v10/phase12/`.
Machine results: `docs/engineering/v10/phase12-evidence.json`.

## Authoritative boundaries and hosted evidence

Read AGENTS.md, governance, Hot Context, Index, generated state handoff, V10 wiki,
Phase10/11 reports and machine records, original native sources, actual installed
RN source, and the Phase11 CI workflow before edits. Initial tracked worktree and
index were clean on the requested branch/HEAD. Four preexisting untracked folders
were preserved without editing: `.t21-publication-U14eyY/`,
`.t21-publication-yfDAmi/`, `.t21-terminal-NEF4Lk/`, `.t21-terminal-li2qUl/`.
Full quality may create additional temporary folders; these are also preserved.
No Git mutation, delivery, APK task, signing, upload, scanner suppression, policy,
ignore, credential, hook or remote change. No global installations.

Fresh read-only GitHub API queries independently confirm runs 40865913157 and
40865913069 are completed/success on this exact HEAD. Both candidate matrix jobs
(rn-cli and sdk54-newarch) are success. Classify the first as
HOSTED_NATIVE_COMPILE; the platform run's Android/Windows/Python/Rust successes
remain immutable historical evidence on this HEAD. Neither accepts the new
private Phase12 candidate or establishes Hermes native registration/device PASS.
Phase11's report describes its earlier uncommitted preparation state; do not
rewrite that immutable historical record. Query exits and log hashes are retained.

## Root cause and supported source contracts

`examples/rust-expo-jsi/native/RustJsiModule.java` has synchronous `install()` using
`getReactApplicationContext().getJavaScriptContextHolder()`, then `holder.get()`.
Its comment explicitly states bridgeless is unsupported. `RustJsi.cpp` casts the
Java long to `jsi::Runtime*`. `RustJsiPackage.java` registers a legacy Java module;
`withRustJsi.cjs` copies and registers exactly that package. Merely enabling New
Architecture preserves this unsupported assumption. SDK54 still contains image-size.

The authenticated isolated SDK54 fixture supplies real RN0.81.5 headers/source:

- `ReactCommon/runtimeexecutor/ReactCommon/RuntimeExecutor.h` encourages a callback
  taking `jsi::Runtime&`, ensuring nonconcurrent access, instead of storing a pointer.
- `ReactCommon/callinvoker/ReactCommon/CallInvoker.h` supports
  `invokeAsync(CallFunc&&)` where CallFunc receives `jsi::Runtime&`; parameterless
  callbacks are explicitly backward compatibility. An invoker is a scheduler,
  not a license to cast a Java handle or retain the callback runtime.
- C++ `TurboModuleWithJSIBindings` exposes virtual
  `installJSIBindingsWithRuntime(jsi::Runtime&)`. `TurboModuleBinding::getModule`
  calls its installer before returning the JS representation; its implementation
  dynamically casts a real TurboModule. No invented proxy global is required.
- Android Java `TurboModuleWithJSIBindings.getBindingsInstaller()` returns a real
  `BindingsInstallerHolder`; JNI `TurboModuleManager.cpp` invokes
  `installer->cthis()->installBindings(runtime, jsCallInvoker_)` for a Turbo Java
  module. The holder's current callback also receives the CallInvoker. A legacy
  ReactPackage alone does not establish this Turbo path; codegen/registration is
  required. `ReactHostImpl.runtimeExecutor` is internal, not a public application
  extension point to assume accessible from an arbitrary Java module.

Choose the C++ Turbo interface plus RN's actual default C++ module provider rather
than inventing a Java runtime-executor wrapper. This is a source-supported hook,
not proof that the Expo host loads the candidate. Source paths and header hashes
are in `inspection.json`. No remote documentation is substituted for package source.

## Private implementation and honest limits

`RustRuntimeModule.cpp` derives from the actual `TurboModule` and
`TurboModuleWithJSIBindings`. It exposes `answer()` and installs appforgeRustAnswer
only when RN supplies its runtime. Both call the real external Rust C symbol
`appforge_jsi_answer`; no stub Rust library, fake header or runtime is provided.
Missing symbol linkage must fail the future native link. No JNI pointer conversion,
JavaScriptContextHolder, runtime pointer storage or delayed runtime capture exists.

Installation is idempotent for the same module/runtime, refuses a foreign existing
global, checks argument counts, and throws JSError on misuse. The function captures
a shared atomic permit instead of `this`; destruction revokes it without accessing
a dead runtime. Runtime-owned JS objects are released by runtime teardown. Copy
and move are deleted. This assumes RN's cached module instance belongs to one
runtime; verify actual reload/destruction ordering and library lifetime on host.
No simulated test establishes teardown safety or exception behavior in Hermes.

`prepare.py` only accepts a fixture below the ignored Phase12 directory, verifies
Expo54.0.38/RN0.81.5, and adapts a prebuilt private SDK54 project. It uses RN's real
default OnLoad.cpp and preserves autolinking fallback, adds the C++ provider,
changes the private CMake entry, and replaces the private legacy package registration
with a comment. It retains earlier generated Java files unused. Production source
is untouched. A prepared `sdk54-runtime/` fixture exists with dependencies symlinked
read-only in practice to the earlier authenticated installation; no npm mutation
was run against that installation. This is not a promoted Expo plugin.

Local clang++ C++20 syntax compilation passes against complete actual RN headers,
including Turbo/bridging imports. Five compiler contracts pass: candidate,
RuntimeExecutor/CallInvoker callback signatures, and expected compiler rejection
of a missing runtime symbol, wrong override signature and copied module. These
are compile contracts, not native symbol linkage or lifetime execution tests.
A Gradle version guard accepts8.14.3 and rejects8.14.2,9.0,missing/ambiguous versions;
it does not run Gradle. No tracked quality tests/scripts were added.

`phase12-native-template.yml` is an ignored, NOT_RUN, branch-only read-only CI
compile template. It embeds the private payload and adapts the existing Phase11
compile script, pins all action SHAs, disables checkout credential persistence,
requires Gradle8.14.3, uses authentic locked Rust, and runs only compile tasks.
It has arm64-v8a and x86_64 legs. Shipped authorship currently filters arm64-v8a;
x86_64 is explicitly an unaccepted private expansion, requiring genuine Rust and
RN link verification. No broad Gradle override or package/sign task is used.
The template is not an installed workflow or CI result and must be reviewed on a
new exact source SHA before separately authorized delivery. Local Android native
compile is NOT_RUN: no verified Android SDK/NDK/Rust ABI toolchain. Local clang
syntax compilation does not alter that status.

## CLI migration and security freeze

Fresh inspection retains React19.1.0/RN0.81.5/CLI20.2.0/parser5.11.2. Full authentic
candidate npm lock SHA256:
`158b8207452fa933a61e1397176b9131765bbc2ee400d510436784aa6df34846`.
Fresh original five-mode behavior tests include actual Android manifest and Apple
scheme XML consumers; the real dev-server/body-parser negative-input probe passes.
Hosted Phase11 provides native compile plus Metro/Hermes bytecode evidence, not
native runtime registration. Candidate manifest and lock remain private.

Carry forward the five real Phase10 findings, 3 HIGH/1 MODERATE/1 LOW,
unsuppressed: GHSA-5p2g-fcmc-qvqq, GHSA-w3rx-r6r6-pgpr,
GHSA-ch52-4w7c-c8xp, GHSA-gcqx-w48m-w2h4, GHSA-gh4j-gqv2-49f6.
No fresh OSV scan ran. Parser4.5.7 remains an open original finding; its bounded
escaping controls do not clear it. image-size, deepmerge and http-cache-semantics
are not silenced. Preserve the historical withdrawn-advisory discrepancy.
SDK55's image-size absence remains an isolated graph observation, not clearance.
The 13-integration inventory, policies, PyO3 `=0.29.0`, original tests and source
freeze remain intact. Rust Cargo.lock SHA256 stays
`a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a`.

## Required next acceptance and promotion checklist

1. Review the ignored payload and run the template on a separately authorized new
   exact SHA. Link authentic Rust and RN libraries for arm64-v8a, then x86_64 if
   explicitly supported by the candidate; inspect symbols/ABI and both task logs.
   No fake libraries, missing-symbol toleration, APK packaging or signing.
2. Future debug-app packaging/install/runtime work requires explicit authorization
   beyond this no-APK task. In actual Hermes verify the module is registered,
   `TurboModuleRegistry.getEnforcing('NativeAppForgeRustRuntime').answer()` returns42,
   and appforgeRustAnswer() returns42. Record runtime thread identity and real hook
   entry, library load and matching binary/source SHA. Bytecode export cannot do this.
3. Verify repeated module lookup and install are idempotent; foreign global and
   absent module/library fail closed. Test reload with old retained references,
   destruction, invalidation, background/foreground, app termination and restart.
   Confirm runtime belongs to the module and no duplicate global or dangling
   callback survives; exercise safe JSError and absent provider negative controls.
4. Complete RN CLI20.2 native Turbo/Hermes/artifact regression, socket/WebSocket
   dev-server checks, real Android and supported Apple host configuration behavior,
   and offline dependency replay. Inspect actual artifact dependency closure.
5. Only after complete required native/runtime acceptance and a genuine fresh OSV
   candidate scan with unchanged scope/controls, review production manifest plus
   complete authentic lock together. Rerun full quality/security/stability and
   applicable device gates. No parser, SDK55 or bridge promotion is authorized here.

Final fresh check exits, counts, hashes, tracked freeze and changed paths are in
the machine record. Second Brain selected-source inventory excludes these
engineering reports and ignored fixtures; its unchanged canonical freshness check
is applicable, generation is unnecessary. Full quality uses the exact original
Phase6 Ubuntu PRoot command, not a reconstructed approximation. Evidence from
older runs is explicitly historical. No native/runtime/physical PASS is claimed.

## Executed local results

| Check | Fresh result |
| --- | --- |
| Original Phase6 PRoot full quality | PASS 1156/1156; fail0/cancelled0/skipped0/todo0 |
| V9 Python / V10 Python | PASS 440/440 / 14/14 |
| Windows Node | PASS 9/9; native Windows execution NOT_RUN locally |
| Existing host/candidate contracts | PASS 7/7 |
| Real RN-header compiler contracts | PASS 5/5, including expected compiler errors |
| Actual XML consumers / server negative inputs | PASS / PASS |
| Stability Gate / wiki audit / wiki secret hygiene | PASS / PASS / PASS |
| Canonical Second Brain freshness | PASS; no selected-source change |
| Gitleaks controls | PASS, positive detection and negative-path coverage |
| Android candidate native link / actual Hermes runtime | NOT_RUN / NOT_RUN |
| Fresh OSV scan | NOT_RUN; original five findings retained |

Only the two Phase12 engineering documents are added to the nonignored project
scope; all implementation, harness and CI template alternatives remain ignored.
No existing tracked file changes. The complete final source scan result and raw
artifact hashes are recorded in the accompanying machine evidence.

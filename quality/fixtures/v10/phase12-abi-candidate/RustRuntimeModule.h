// PRIVATE compile candidate, RN 0.81.5 only. No native/runtime acceptance.
#include <ReactCommon/TurboModule.h>
#include <ReactCommon/TurboModuleWithJSIBindings.h>
#include <atomic>
#include <cstdint>
extern "C" int32_t appforge_jsi_answer();
namespace facebook::react {
class RustRuntimeModule final : public TurboModule, public TurboModuleWithJSIBindings {
  std::shared_ptr<std::atomic<bool>> alive_ = std::make_shared<std::atomic<bool>>(true);
  bool installed_ = false; // RN module instance belongs to one runtime; never process-global.
 public:
  explicit RustRuntimeModule(std::shared_ptr<CallInvoker> invoker)
      : TurboModule("NativeAppForgeRustRuntime", std::move(invoker)) {
    methodMap_["answer"] = {0, [](jsi::Runtime& rt, TurboModule&,
                                  const jsi::Value*, size_t count) -> jsi::Value {
      if (count != 0) throw jsi::JSError(rt, "answer takes no arguments");
      return jsi::Value(static_cast<double>(appforge_jsi_answer()));
    }};
  }
  RustRuntimeModule(const RustRuntimeModule&) = delete;
  RustRuntimeModule& operator=(const RustRuntimeModule&) = delete;
  RustRuntimeModule(RustRuntimeModule&&) = delete;
  RustRuntimeModule& operator=(RustRuntimeModule&&) = delete;
  ~RustRuntimeModule() override { alive_->store(false); }
 private:
  void installJSIBindingsWithRuntime(jsi::Runtime& rt) override {
    // RN TurboModuleBinding::getModule calls this on runtime access. No raw
    // runtime pointer, queued callback, JavaScriptContextHolder or captured this.
    if (installed_) return;
    if (rt.global().hasProperty(rt, "appforgeRustAnswer"))
      throw jsi::JSError(rt, "appforgeRustAnswer already owned; refusing replacement");
    auto permit = alive_;
    auto fn = jsi::Function::createFromHostFunction(rt,
        jsi::PropNameID::forAscii(rt, "appforgeRustAnswer"), 0,
        [permit](jsi::Runtime& runtime, const jsi::Value&, const jsi::Value*, size_t count) {
          if (!permit->load()) throw jsi::JSError(runtime, "Rust module invalidated");
          if (count != 0) throw jsi::JSError(runtime, "answer takes no arguments");
          return jsi::Value(static_cast<double>(appforge_jsi_answer()));
        });
    rt.global().setProperty(rt, "appforgeRustAnswer", std::move(fn));
    installed_ = true;
  }
};
// Integrate through RN DefaultTurboModuleManagerDelegate cxxModuleProvider,
// preserving its existing autolinking fallback. Not wired into shipped code.
std::shared_ptr<TurboModule> appforgeRuntimeProvider(const std::string& name,
    const std::shared_ptr<CallInvoker>& invoker) {
  if (name == "NativeAppForgeRustRuntime") return std::make_shared<RustRuntimeModule>(invoker);
  return nullptr;
}
}

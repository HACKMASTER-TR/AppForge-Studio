#include "RustTurboModule.h"
#include <cstdint>
extern "C" int32_t appforge_jsi_answer();
namespace facebook::react {
RustTurboModule::RustTurboModule(std::shared_ptr<CallInvoker> invoker)
    : TurboModule("NativeAppForgeRust", std::move(invoker)) {
  methodMap_["answer"] = MethodMetadata{0,
      [](jsi::Runtime &, TurboModule &, const jsi::Value *, size_t) -> jsi::Value {
        return jsi::Value(static_cast<double>(appforge_jsi_answer()));
      }};
}
}

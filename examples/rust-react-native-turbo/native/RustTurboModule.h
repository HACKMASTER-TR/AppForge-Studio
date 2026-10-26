#pragma once
#include <ReactCommon/TurboModule.h>
namespace facebook::react {
class RustTurboModule final : public TurboModule {
 public:
  explicit RustTurboModule(std::shared_ptr<CallInvoker> invoker);
};
}

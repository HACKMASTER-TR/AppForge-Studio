#include <jni.h>
#include <jsi/jsi.h>
#include <cstdint>

extern "C" int32_t appforge_jsi_answer();

extern "C" JNIEXPORT jboolean JNICALL
Java_com_appforge_rustjsi_RustJsiModule_installNative(JNIEnv *, jclass, jlong pointer) {
    if (pointer == 0) return JNI_FALSE;
    auto &runtime = *reinterpret_cast<facebook::jsi::Runtime *>(pointer);
    auto function = facebook::jsi::Function::createFromHostFunction(
        runtime, facebook::jsi::PropNameID::forAscii(runtime, "appforgeRustAnswer"), 0,
        [](facebook::jsi::Runtime &, const facebook::jsi::Value &,
           const facebook::jsi::Value *, size_t) -> facebook::jsi::Value {
            return facebook::jsi::Value(static_cast<double>(appforge_jsi_answer()));
        });
    runtime.global().setProperty(runtime, "appforgeRustAnswer", std::move(function));
    return JNI_TRUE;
}

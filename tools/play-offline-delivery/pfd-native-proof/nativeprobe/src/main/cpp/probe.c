#include <jni.h>
JNIEXPORT jstring JNICALL
Java_com_appforge_v155proof_nativeprobe_ProbeActivity_nativeProbe(
    JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, "APPFORGE_V155_PFD_NATIVE_PASS");
}

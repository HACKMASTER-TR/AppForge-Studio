# AppForge Studio v2.3
# Keep only reflection-sensitive JSON / Play classes where necessary.
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.android.play.core.integrity.**

# LiteRT-LM JNI/API
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# VideoForge / sherpa-onnx
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# AppForge Terminal: JSch can discover optional crypto providers at runtime.
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.eddsa.**

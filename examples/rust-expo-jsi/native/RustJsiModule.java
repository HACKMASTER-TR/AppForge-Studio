package com.appforge.rustjsi;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public final class RustJsiModule extends ReactContextBaseJavaModule {
    static { System.loadLibrary("appforge_rust_jsi"); }
    public RustJsiModule(ReactApplicationContext context) { super(context); }
    @Override public String getName() { return "AppForgeRustJsi"; }
    @ReactMethod(isBlockingSynchronousMethod = true)
    public boolean install() {
        // Called synchronously on the JS thread. Bridgeless mode is not supported.
        com.facebook.react.bridge.JavaScriptContextHolder holder =
            getReactApplicationContext().getJavaScriptContextHolder();
        return holder != null && holder.get() != 0 && installNative(holder.get());
    }
    private static native boolean installNative(long pointer);
}

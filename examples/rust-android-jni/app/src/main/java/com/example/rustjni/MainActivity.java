package com.example.rustjni;
public final class MainActivity extends android.app.Activity {
    static { System.loadLibrary("appforge_jni"); }
    private static native int rustAnswer();
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Rust JNI: " + rustAnswer());
        setContentView(text);
    }
}

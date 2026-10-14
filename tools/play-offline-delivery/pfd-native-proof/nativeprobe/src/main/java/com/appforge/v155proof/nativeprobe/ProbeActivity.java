package com.appforge.v155proof.nativeprobe;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Build;
import android.widget.TextView;
import com.google.android.play.core.splitcompat.SplitCompat;

public final class ProbeActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        SplitCompat.installActivity(this);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        try {
            System.loadLibrary("v155proof");
            text.setText("PFD_NATIVE_RESULT=" + nativeProbe() + "\nABI=" +
                java.util.Arrays.toString(Build.SUPPORTED_ABIS));
        } catch (Throwable e) {
            text.setText("PFD_NATIVE_LOAD_FAILED=" + e.getClass().getSimpleName()
                + ": " + e.getMessage());
        }
        text.setTextSize(18f);
        int inset = (int)(24 * getResources().getDisplayMetrics().density);
        text.setPadding(inset,inset,inset,inset);
        setContentView(text);
    }
    private static native String nativeProbe();
}

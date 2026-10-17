package com.appforge.htmlstub;

import android.app.Activity;
import android.content.Context;
import android.os.PowerManager;
import android.view.WindowManager;

/** Opted-in foreground screen/CPU wake protection; never a background execution service. */
final class AppForgeAwake implements AutoCloseable {
    private final Activity activity;
    private PowerManager.WakeLock lock;
    AppForgeAwake(Activity activity) { this.activity=activity; }
    void start() {
        if(lock!=null && lock.isHeld()) return;
        PowerManager manager=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);
        if(manager==null)throw new IllegalStateException("Power service unavailable");
        PowerManager.WakeLock candidate=manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AppForge:foreground");
        candidate.setReferenceCounted(false);
        try {
            candidate.acquire(10*60*1000L); // Hard timeout even if lifecycle delivery fails.
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            lock=candidate;
        } catch(RuntimeException failure) {
            if(candidate.isHeld())candidate.release();
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            throw failure;
        }
    }
    @Override public void close() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(lock!=null) { if(lock.isHeld())lock.release(); lock=null; }
    }
}

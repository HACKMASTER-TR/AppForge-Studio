package com.appforge.v155proof;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

/** No AppForge application logic. This is an independent minimal PFD probe. */
public final class MainActivity extends Activity {
    private static final String MODULE = "nativeprobe";
    private static final String PROBE_ACTIVITY =
        "com.appforge.v155proof.nativeprobe.ProbeActivity";
    private SplitInstallManager manager;
    private TextView status;
    private Button button;
    private final SplitInstallStateUpdatedListener listener = this::onSessionUpdate;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = SplitInstallManagerFactory.create(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int inset = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(inset, inset, inset, inset);
        status = new TextView(this);
        status.setText("V15.5 native PFD proof. No AppForge code or project access.");
        button = new Button(this);
        button.setText("Download / Open ARM64 module");
        button.setOnClickListener(v -> requestModule());
        layout.addView(status);
        layout.addView(button);
        setContentView(layout);
        refresh();
    }
    @Override protected void onStart() {
        super.onStart();
        manager.registerListener(listener);
        refresh();
    }
    @Override protected void onStop() {
        manager.unregisterListener(listener);
        super.onStop();
    }
    private void setStatus(String message) { status.setText(message); }
    private void refresh() {
        boolean installed = manager.getInstalledModules().contains(MODULE);
        setStatus("PFD_MODULE_INSTALLED=" + installed + "\n" +
            "Tap button to download or open. Real Play install required for delivery proof.");
    }
    private void requestModule() {
        if (manager.getInstalledModules().contains(MODULE)) {
            openModule();
            return;
        }
        button.setEnabled(false);
        setStatus("PFD_INSTALL_REQUESTED=" + MODULE);
        SplitInstallRequest request = SplitInstallRequest.newBuilder()
            .addModule(MODULE).build();
        manager.startInstall(request)
            .addOnSuccessListener(id -> setStatus("PFD_SESSION_ID=" + id))
            .addOnFailureListener(e -> {
                button.setEnabled(true);
                setStatus("PFD_INSTALL_REQUEST_FAILED=" + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            });
    }
    private void onSessionUpdate(SplitInstallSessionState state) {
        if (!state.moduleNames().contains(MODULE)) return;
        runOnUiThread(() -> {
            int s = state.status();
            setStatus("PFD_STATUS=" + s + "\nPFD_ERROR_CODE=" + state.errorCode());
            if (s == SplitInstallSessionStatus.INSTALLED) {
                button.setEnabled(true);
                setStatus("PFD_INSTALLED=YES\nOpening native feature activity...");
                openModule();
            } else if (s == SplitInstallSessionStatus.FAILED ||
                       s == SplitInstallSessionStatus.CANCELED ||
                       s == SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) {
                button.setEnabled(true);
                if (s == SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) {
                    setStatus("PFD_CONFIRMATION_REQUIRED=YES; explicit acceptance not implemented in this probe");
                }
            }
        });
    }
    private void openModule() {
        try {
            Intent intent = new Intent().setClassName(getPackageName(), PROBE_ACTIVITY);
            startActivity(intent);
        } catch (RuntimeException e) {
            setStatus("PFD_FEATURE_ACTIVITY_START_FAILED=" + e.getMessage());
        }
    }
}

package com.appforge.htmlstub;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Fixed, no-bridge HTML/HTTPS WebView shell. No device toolchain required at runtime. */
public final class TinyHtmlActivity extends Activity {
    private WebView web;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        // The system WebView already runs CSS and JavaScript; no Node/runtime download.
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setDomStorageEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                return !("https".equalsIgnoreCase(scheme) ||
                        ("file".equalsIgnoreCase(scheme) &&
                         "android_asset".equalsIgnoreCase(uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0))));
            }
        });
        setContentView(web);
        try {
            JSONObject config = new JSONObject(new String(readAsset("appforge-html-config.json"), StandardCharsets.UTF_8));
            if ("URL".equals(config.optString("mode"))) {
                String address = config.getString("url");
                Uri uri = Uri.parse(address);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()) {
                    throw new IllegalArgumentException("HTTPS URL required");
                }
                web.loadUrl(address);
            } else if ("LOCAL".equals(config.optString("mode"))) {
                String entry = config.getString("entry");
                if (entry.isEmpty() || entry.startsWith("/") || entry.contains("..") ||
                    !entry.toLowerCase(java.util.Locale.ROOT).endsWith(".html")) {
                    throw new IllegalArgumentException("Invalid HTML entry path");
                }
                String localPath = "/android_asset/site/" + entry;
                web.loadUrl(new Uri.Builder().scheme("file").path(localPath).build().toString());
            } else {
                throw new IllegalArgumentException("Unknown source type");
            }
        } catch (Exception error) {
            web.loadDataWithBaseURL(null, "<html><body>Unable to open this project.</body></html>", "text/html", "UTF-8", null);
        }
    }

    private byte[] readAsset(String path) throws Exception {
        try (InputStream stream = getAssets().open(path); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}

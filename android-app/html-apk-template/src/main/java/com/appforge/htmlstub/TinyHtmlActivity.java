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

/** Fixed HTML/HTTPS shell with a scoped document message channel. No device toolchain required at runtime. */
public final class TinyHtmlActivity extends Activity {
    private WebView web;
    private AppForgeDocuments documents;
    public final class LocalDocuments {
        @android.webkit.JavascriptInterface public void send(String token, String raw) {
            AppForgeDocuments current=documents;
            if(current==null || raw==null || raw.length()>34000 || !current.matchesCompatToken(token))return;
            runOnUiThread(()->{if(current==documents)current.receiveLocal(token,raw);});
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        // The system WebView already runs CSS and JavaScript; no Node/runtime download.
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return new AppForgeLocalAssets(getAssets(), documents == null ? null : documents.bootstrap()).intercept(request);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (documents != null) documents.pageStarted();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (documents != null) documents.pageFinished(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                return !("https".equalsIgnoreCase(scheme) || AppForgeDocuments.legacyLocalPage(uri.toString()));
            }
        });
        setContentView(web);
        try {
            JSONObject config = new JSONObject(new String(readAsset("appforge-html-config.json"), StandardCharsets.UTF_8));
            web.getSettings().setJavaScriptEnabled(config.optBoolean("webJavaScriptEnabled", true));
            web.getSettings().setDomStorageEnabled(config.optBoolean("webDomStorageEnabled", true));
            web.getSettings().setBuiltInZoomControls(config.optBoolean("webZoomEnabled", true));
            web.getSettings().setDisplayZoomControls(false);
            web.getSettings().setSupportZoom(config.optBoolean("webZoomEnabled", true));
            web.getSettings().setUseWideViewPort(config.optBoolean("webWideViewPortEnabled", true));
            web.getSettings().setLoadWithOverviewMode(config.optBoolean("webOverviewModeEnabled", true));
            web.getSettings().setMediaPlaybackRequiresUserGesture(!config.optBoolean("webMediaAutoplayEnabled", true));
            web.getSettings().setCacheMode(config.optBoolean("offlineCache", true) ?
                android.webkit.WebSettings.LOAD_DEFAULT : android.webkit.WebSettings.LOAD_NO_CACHE);
            web.setBackgroundColor(android.graphics.Color.parseColor(config.optString("backgroundColor", "#07101F")));
            getWindow().setStatusBarColor(android.graphics.Color.parseColor(config.optString("statusBarColor", "#07101F")));
            getWindow().setNavigationBarColor(android.graphics.Color.parseColor(config.optString("navigationBarColor", "#07101F")));
            String origin = "LOCAL".equals(config.optString("mode")) ? "legacy-file"
                : config.optBoolean("nativeBridgeAllowRemote", false) ? AppForgeDocuments.httpsOrigin(config.optString("url")) : null;
            documents = new AppForgeDocuments(this, web, origin, config.optBoolean("downloads", false),
                config.optBoolean("nativeBridge", false) && config.optBoolean("shareBridge", false));
            if ("LOCAL".equals(config.optString("mode")))
                web.addJavascriptInterface(new LocalDocuments(), "AppForgeLocalDocuments");
            if (config.optBoolean("downloads", false)) web.setDownloadListener((url, agent, disposition, mime, length) -> {
                try { documents.enqueueHttp(url, agent, disposition, mime); }
                catch (RuntimeException error) { android.widget.Toast.makeText(this,"Download unavailable",android.widget.Toast.LENGTH_SHORT).show(); }
            });
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

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (documents != null) documents.activityResult(request, result, data);
    }

    @Override protected void onResume() {
        super.onResume();
        if (documents != null) documents.resume();
    }

    @Override protected void onPause() {
        if (documents != null) documents.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (documents != null) documents.close();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}

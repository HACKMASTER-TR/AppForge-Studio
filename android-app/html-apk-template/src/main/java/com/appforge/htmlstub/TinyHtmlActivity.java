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
    private JSONObject config;
    private AppForgeConnectivity connectivity;
    private AppForgeLocation location;
    private AppForgeBilling billing;
    private String billingSnapshot="{\"state\":\"DISABLED\"}";
    private AppForgeAwake awake;
    private AppForgeWebPermissions webPermissions;
    private AppForgeNfc nfc;
    private AppForgeQrScanner scanner;
    private boolean scannerPermissionRequested;
    private String pendingDeepLink;
    private String pickerOrigin;
    private boolean multipleFiles;
    private boolean foreground;
    private boolean notificationPermissionRequested;
    private final java.util.function.Consumer<String> mediaObserver = value -> {
        if (foreground && web != null && documents != null && documents.allowsNativeEvent())
            web.evaluateJavascript("window.dispatchEvent(new CustomEvent('appforge-media',{detail:"+value+"}));", ignored -> {});
    };
    private android.view.View splash;
    private final AppForgeSelection<Uri> selection = new AppForgeSelection<>();
    private static final int PICK_FILES = 7201;

    private void dismissSplash() {
        if (splash != null) {
            ((android.view.ViewGroup)splash.getParent()).removeView(splash);
            splash = null;
        }
    }

    private String nativeAction(JSONObject message) throws Exception {
        if (config == null || !config.optBoolean("nativeBridge", false)) throw new SecurityException("Native bridge disabled");
        org.json.JSONArray args = message.optJSONArray("args");
        String method = message.getString("method");
        if (method.startsWith("billing") && config.optBoolean("billingEnabled", false)) {
            if (billing == null) throw new IllegalStateException("Billing unavailable");
            boolean available = connectivity != null && connectivity.online();
            if (!available) billing.network(false);
            if ("billingState".equals(method)) return billingSnapshot;
            if (!available) throw new IllegalStateException("İnternet yok. Play Billing pasif; bağlantı açıldığında yeniden bağlanır.");
            switch (method) {
                case "billingProducts": return billing.query();
                case "billingRestore": return billing.refresh();
                case "billingPurchase": return billing.purchase(args == null ? "" : args.optString(0, ""), args == null ? "" : args.optString(1, ""));
                default: throw new SecurityException("Unknown Billing action");
            }
        }
        if ("getLocation".equals(method) && config.optBoolean("location", false) && "LOCAL".equals(config.optString("mode"))) {
            if (location == null) throw new IllegalStateException("Location unavailable");
            return location.request(args == null ? "" : args.optString(0, ""));
        }
        if (method.startsWith("media") && config.optBoolean("mediaPlayerBridge", false)) {
            String command;
            switch (method) {
                case "mediaPlay": command = "play"; break;
                case "mediaPause": command = "pause"; break;
                case "mediaResume": command = "resume"; break;
                case "mediaStop": command = "stop"; break;
                case "mediaState": command = "state"; break;
                default: throw new SecurityException("Unknown media action");
            }
            if ("play".equals(command) && android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (notificationPermissionRequested) throw new SecurityException("Media notification consent denied; enable in Android settings");
                notificationPermissionRequested = true;
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7202);
                return "PERMISSION_REQUESTED";
            }
            return AppForgeMediaService.send(this, command, args == null ? "" : args.optString(0, ""), args == null ? "" : args.optString(1, ""));
        }
        if ("copy".equals(method) && config.optBoolean("clipboardBridge", false)) {
            String text = args == null ? "" : args.optString(0, "");
            if (text.length() > 65536) throw new IllegalArgumentException("Clipboard text too large");
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            // Write only: never expose the user's existing clipboard to JavaScript.
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AppForge", text));
            return "OK";
        }
        if ("vibrate".equals(method) && config.optBoolean("vibrationBridge", false)) {
            int ms = args == null ? 100 : args.optInt(0, 100);
            if (ms < 1 || ms > 1000) throw new IllegalArgumentException("Vibration must be 1–1000 ms");
            android.os.Vibrator vibrator = (android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) throw new IllegalStateException("Vibrator unavailable");
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            return "OK";
        }
        if ("notify".equals(method) && config.optBoolean("notifications", false)) {
            if (args == null) throw new IllegalArgumentException("Notification text required");
            String title = args.optString(0, ""), text = args.optString(1, "");
            if (title.isEmpty() || title.length() > 100 || text.length() > 2000)
                throw new IllegalArgumentException("Notification text exceeds limit");
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (!notificationPermissionRequested) {
                    notificationPermissionRequested = true;
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7202);
                    return "PERMISSION_REQUESTED"; // No notification was sent. Retry only after the user's permission decision.
                }
                throw new SecurityException("Notification permission denied; enable in Android settings");
            }
            android.app.NotificationManager manager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager == null || !manager.areNotificationsEnabled()) throw new SecurityException("Notifications disabled");
            String channel = "appforge.local.v2";
            manager.createNotificationChannel(new android.app.NotificationChannel(channel, "Local notifications", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            android.app.NotificationChannel existing = manager.getNotificationChannel(channel);
            if (existing == null || existing.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE)
                throw new SecurityException("Notification channel disabled");
            // Plain text, one replaceable local notification; no intents, remote payloads, alarms or background scheduler.
            manager.notify(1, new android.app.Notification.Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build());
            return "SENT";
        }
        if ("scanCode".equals(method) && config.optBoolean("qrScanner", false)) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (scannerPermissionRequested) throw new SecurityException("Camera permission denied; enable in Android settings");
                scannerPermissionRequested = true;
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 7204);
                return "PERMISSION_REQUESTED";
            }
            if (scanner == null) throw new IllegalStateException("Scanner unavailable");
            return scanner.start();
        }
        if ("scanNfc".equals(method) && config.optBoolean("nfc", false)) {
            if (nfc == null) throw new IllegalStateException("NFC unavailable");
            return nfc.start();
        }
        if ("networkState".equals(method) && config.optBoolean("networkState", false)) {
            if (connectivity == null) throw new IllegalStateException("Connectivity service unavailable");
            return connectivity.online() ? "ONLINE" : "OFFLINE";
        }
        throw new SecurityException("Native action disabled or unsupported");
    }

    private void configureWindow() {
        String orientation = config.optString("orientation", "unspecified");
        setRequestedOrientation("portrait".equals(orientation) ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
            "landscape".equals(orientation) ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (config.optBoolean("fullscreen", false)) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        if (config.optBoolean("splashEnabled", false)) {
            android.widget.TextView label = new android.widget.TextView(this);
            label.setText(config.optString("splashText", "").isEmpty() ? config.optString("appName", "") : config.optString("splashText"));
            label.setGravity(android.view.Gravity.CENTER);
            label.setTextColor(android.graphics.Color.WHITE);
            label.setBackgroundColor(android.graphics.Color.parseColor(config.optString("backgroundColor", "#07101F")));
            splash = label;
            addContentView(label, new android.view.ViewGroup.LayoutParams(-1, -1));
            label.postDelayed(this::dismissSplash, 5000);
        }
    }
    public final class LocalDocuments {
        @android.webkit.JavascriptInterface public void send(String token, String raw) {
            AppForgeDocuments current=documents;
            if(current==null || raw==null || raw.length()>34000 || !current.matchesCompatToken(token))return;
            runOnUiThread(()->{if(current==documents)current.receiveLocal(token,raw);});
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        scannerPermissionRequested = state != null && state.getBoolean("appforge.scannerPermissionRequested", false);
        notificationPermissionRequested = state != null && state.getBoolean("appforge.notificationPermissionRequested", false);
        multipleFiles = state != null && state.getBoolean("appforge.multipleFiles", false);
        selection.restoreOutstanding(state != null && state.getBoolean("appforge.pickerOutstanding", false));
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
                selection.cancel();
                if (webPermissions != null) webPermissions.cancel();
                if (location != null) location.cancel();
                if (nfc != null) nfc.close();
                if (scanner != null) scanner.close();
                if (documents != null) documents.pageStarted();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (documents != null) documents.pageFinished(url);
                dismissSplash();
                deliverDeepLink();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                return !("https".equalsIgnoreCase(scheme) || AppForgeDocuments.legacyLocalPage(uri.toString()));
            }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) {
                runOnUiThread(()->{if(webPermissions!=null)webPermissions.media(request);else if(request!=null)request.deny();});
            }
            @Override public void onPermissionRequestCanceled(android.webkit.PermissionRequest request) {
                runOnUiThread(()->{if(webPermissions!=null)webPermissions.canceled(request);});
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin,android.webkit.GeolocationPermissions.Callback callback) {
                if(webPermissions!=null)webPermissions.location(origin,callback);else if(callback!=null)callback.invoke(origin,false,false);
            }
            @Override public void onGeolocationPermissionsHidePrompt() {if(webPermissions!=null)webPermissions.cancel();}

            @Override public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (config == null || !config.optBoolean("fileUpload", false) || !foreground ||
                    documents == null || !documents.allowsPicker(pickerOrigin)) {
                    callback.onReceiveValue(null); return true;
                }
                // SAF provides temporary read grants; never request broad storage permission or persist grants.
                // Camera capture is deliberately not offered by this shell.
                if (!selection.begin(values -> callback.onReceiveValue(values == null ? null : values.toArray(new Uri[0])))) {
                    callback.onReceiveValue(null); return true;
                }
                multipleFiles = params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, multipleFiles)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    String[] types = AppForgeSelection.mimeTypes(params.getAcceptTypes());
                    if (types.length > 0) intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, types);
                    startActivityForResult(intent, PICK_FILES);
                } catch (RuntimeException error) { selection.finish(null); }
                return true;
            }
        });
        setContentView(web);
        try {
            config = new JSONObject(new String(readAsset("appforge-html-config.json"), StandardCharsets.UTF_8));
            if (state == null) acceptDeepLink(getIntent());
            else pendingDeepLink = config.optBoolean("deepLinkEnabled", false) ? AppForgeDeepLink.accept(
                state.getString("appforge.pendingDeepLink"), config.optString("deepLinkScheme"),
                config.optString("deepLinkHost"), config.optString("deepLinkPathPrefix")) : null;
            configureWindow();
            if (config.optBoolean("wakeLock", false)) awake = new AppForgeAwake(this);
            web.getSettings().setMixedContentMode(config.optBoolean("webMixedContentAllowed", false) ?
                android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE : android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
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
            pickerOrigin = "LOCAL".equals(config.optString("mode")) ? "legacy-file" :
                AppForgeDocuments.httpsOrigin(config.optString("url"));
            String origin = "LOCAL".equals(config.optString("mode")) ? "legacy-file"
                : config.optBoolean("nativeBridgeAllowRemote", false) ? AppForgeDocuments.httpsOrigin(config.optString("url")) : null;
            documents = new AppForgeDocuments(this, web, origin, config.optBoolean("downloads", false),
                config.optBoolean("nativeBridge", false) && config.optBoolean("shareBridge", false));
            documents.setLegacy(this::nativeAction, config.optBoolean("nativeBridge", false));
            boolean localLocation = "LOCAL".equals(config.optString("mode")) && config.optBoolean("location", false) && config.optBoolean("nativeBridge", false);
            documents.setLocalLocation(localLocation);
            if (localLocation) location = new AppForgeLocation(this,
                () -> foreground && documents != null && documents.allowsNativeEvent(),
                value -> {if (foreground && web != null && documents != null && documents.allowsNativeEvent())
                    web.evaluateJavascript("window.dispatchEvent(new CustomEvent('appforge-location',{detail:"+value.toString()+"}));", ignored -> {});},
                state != null && state.getBoolean("appforge.locationOutstanding", false));
            webPermissions = new AppForgeWebPermissions(this, requested -> {
                String expected = "URL".equals(config.optString("mode")) ? AppForgeDocuments.httpsOrigin(config.optString("url")) : null;
                return foreground && expected != null && expected.equals(AppForgeDocuments.httpsOrigin(requested)) &&
                    documents != null && documents.allowsPicker(expected);
            }, config.optBoolean("camera", false), config.optBoolean("microphone", false), config.optBoolean("location", false),
                state != null && state.getBoolean("appforge.webPermissionOutstanding", false));
            if (config.optBoolean("qrScanner", false)) scanner = new AppForgeQrScanner(this,
                () -> foreground && documents != null && documents.allowsNativeEvent(),
                text -> { if (web != null && foreground && documents != null && documents.allowsNativeEvent())
                    web.evaluateJavascript("window.dispatchEvent(new CustomEvent('appforge-scan',{detail:{text:"+JSONObject.quote(text)+"}}));", ignored -> {}); },
                error -> android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_LONG).show());
            if (config.optBoolean("nfc", false)) nfc = new AppForgeNfc(this,
                () -> foreground && documents != null && documents.allowsNativeEvent(),
                id -> { if (web != null && foreground && documents != null && documents.allowsNativeEvent())
                    web.evaluateJavascript("window.dispatchEvent(new CustomEvent('appforge-nfc',{detail:{id:'"+id+"'}}));", ignored -> {}); });
            if (config.optBoolean("billingEnabled", false)) {
                billingSnapshot="{\"state\":\"WAITING_FOR_NETWORK\"}";
                billing = new AppForgeBilling(this, config, () -> foreground && documents != null && documents.allowsNativeEvent(), value -> {
                    billingSnapshot=value.toString();
                    if (foreground && web != null && documents != null && documents.allowsNativeEvent())
                        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('appforge-billing',{detail:"+billingSnapshot+"}));", ignored -> {});
                });
            }
            if (config.optBoolean("networkState", false) || config.optBoolean("billingEnabled", false)) connectivity = new AppForgeConnectivity(this, new AppForgeConnectivity.Sink() {
                @Override public void changed(boolean online, boolean restored) {
                if (!foreground || web == null) return;
                if (billing != null) {
                    billing.network(online);
                    if (!online) android.widget.Toast.makeText(TinyHtmlActivity.this, "İnternet yok. Play Billing pasif; bağlantı açıldığında yeniden bağlanır.", android.widget.Toast.LENGTH_LONG).show();
                }
                if ("URL".equals(config.optString("mode"))) {
                    if (!online) android.widget.Toast.makeText(TinyHtmlActivity.this,
                        "İnternet yok. Çevrimiçi içerik pasif; bağlantı açıldığında yeniden yüklenecek.", android.widget.Toast.LENGTH_LONG).show();
                    String expected = AppForgeDocuments.httpsOrigin(config.optString("url"));
                    if (restored && expected != null && expected.equals(AppForgeDocuments.httpsOrigin(web.getUrl()))) web.reload();
                }
                if (documents != null && documents.allowsNativeEvent()) web.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('appforge-connectivity',{detail:{online:"+online+"}}));", ignored -> {});
                }
                @Override public void unavailable() {
                    if (billing != null) billing.network(false);
                    android.widget.Toast.makeText(TinyHtmlActivity.this, "Ağ durumu izlenemiyor; bağlantı durumu bilinmiyor.", android.widget.Toast.LENGTH_LONG).show();
                }
            });
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
            dismissSplash();
            web.loadDataWithBaseURL(null, "<html><body>Unable to open this project.</body></html>", "text/html", "UTF-8", null);
        }
    }

    private void acceptDeepLink(android.content.Intent intent) {
        pendingDeepLink = null;
        if (config == null || !config.optBoolean("deepLinkEnabled", false) || intent == null ||
            !android.content.Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        pendingDeepLink = AppForgeDeepLink.accept(intent.getData().toString(), config.optString("deepLinkScheme"),
            config.optString("deepLinkHost"), config.optString("deepLinkPathPrefix"));
    }
    private void deliverDeepLink() {
        if (pendingDeepLink == null || !foreground || web == null || documents == null || !documents.allowsNativeEvent()) return;
        String value = pendingDeepLink; pendingDeepLink = null;
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('appforge-deeplink',{detail:{url:"+
            JSONObject.quote(value)+"}}));", ignored -> {});
    }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptDeepLink(intent); deliverDeepLink();
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
        if (request == PICK_FILES) {
            java.util.List<Uri> values = new java.util.ArrayList<>();
            boolean valid = result == RESULT_OK && data != null;
            if (valid && data.getClipData() != null) {
                android.content.ClipData clip = data.getClipData();
                valid = clip.getItemCount() <= 32;
                if (valid) for (int i = 0; i < clip.getItemCount(); i++) values.add(clip.getItemAt(i).getUri());
            } else if (valid && data.getData() != null) values.add(data.getData());
            if (!multipleFiles && values.size() > 1) valid = false;
            for (Uri uri : values) if (uri == null || !AppForgeSelection.safeContentUri(uri.toString()) ||
                (getPackageName() + ".appforge.documents").equals(uri.getAuthority())) valid = false;
            // A navigation or lifecycle cancellation retires the callback, but never reuses this request code
            // while a previous system picker is still outstanding.
            selection.finish(valid && !values.isEmpty() ? values : null);
            return;
        }
        if (documents != null) documents.activityResult(request, result, data);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("appforge.locationOutstanding", location != null && location.outstanding());
        state.putBoolean("appforge.scannerPermissionRequested", scannerPermissionRequested);
        state.putString("appforge.pendingDeepLink", pendingDeepLink);
        state.putBoolean("appforge.webPermissionOutstanding", webPermissions != null && webPermissions.outstanding());
        state.putBoolean("appforge.pickerOutstanding", selection.outstanding());
        state.putBoolean("appforge.multipleFiles", multipleFiles);
        state.putBoolean("appforge.notificationPermissionRequested", notificationPermissionRequested);
        super.onSaveInstanceState(state);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (webPermissions != null) webPermissions.result(request);
        if (location != null) location.permissionsResult(request);
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (web != null) web.onResume();
        if (awake != null) {
            try { awake.start(); }
            catch (RuntimeException failure) { android.widget.Toast.makeText(this, "Uyanık tutma başlatılamadı.", android.widget.Toast.LENGTH_LONG).show(); }
        }
        if (documents != null) documents.resume();
        if (config != null && config.optBoolean("mediaPlayerBridge", false)) AppForgeMediaService.observe(mediaObserver);
        deliverDeepLink();
        if (connectivity != null) {
            try { connectivity.start(); }
            catch (RuntimeException failure) {
                android.widget.Toast.makeText(this, "Ağ durumu izlenemiyor; bağlantı durumu bilinmiyor.", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override protected void onPause() {
        foreground = false;
        if (config != null && config.optBoolean("mediaPlayerBridge", false)) AppForgeMediaService.observe(null);
        if (webPermissions != null) webPermissions.cancel();
        if (location != null) location.cancel();
        if (awake != null) awake.close();
        if (nfc != null) nfc.close();
        if (scanner != null) scanner.close();
        if (connectivity != null) connectivity.close();
        if (web != null) {
            // Foreground media only. Background playback requires a separately validated media service.
            web.evaluateJavascript("document.querySelectorAll('audio,video').forEach(function(media){media.pause();});", ignored -> {});
            web.onPause();
        }
        android.os.Vibrator vibrator = (android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && config != null && config.optBoolean("vibrationBridge", false) && config.optBoolean("nativeBridge", false)) vibrator.cancel();
        if (documents != null) documents.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        selection.cancel();
        if (billing != null) billing.close();
        if (config != null && config.optBoolean("mediaPlayerBridge", false)) AppForgeMediaService.observe(null);
        if (webPermissions != null) webPermissions.cancel();
        if (location != null) location.cancel();
        if (awake != null) awake.close();
        if (nfc != null) nfc.close();
        if (scanner != null) scanner.close();
        if (connectivity != null) connectivity.close();
        dismissSplash();
        if (documents != null) documents.close();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}

package com.appforge.runtime;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.URLUtil;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class FastActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 7101;
    private static final int APP_PERMISSION_REQUEST = 7102;

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private Uri cameraCaptureUri;
    private volatile boolean cameraCaptureRequestedByPage = false;

    private JSONObject config;
    private View splashView;
    private long splashStartedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        config = loadConfig();

        configureWindow();

        FrameLayout root = new FrameLayout(this);

        webView = new WebView(this);

        configureWebView();

        webView.setBackgroundColor(
            parseColor(
                config.optString(
                    "backgroundColor",
                    "#07101F"
                ),
                Color.BLACK
            )
        );

        root.addView(
            webView,
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        );

        if (config.optBoolean("watermark", false)) {
            addWatermark(root);
        }

        if (
            config.optBoolean(
                "splashEnabled",
                false
            )
        ) {
            addSplash(root);
        }

        setContentView(root);

        requestConfiguredPermissions();

        if (splashView != null) {
            splashView.postDelayed(
                this::hideSplash,
                2000L
            );
        }

        loadStartPage();
    }

    private void requestConfiguredPermissions() {
        java.util.ArrayList<String> permissions =
            new java.util.ArrayList<>();

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
            config.optBoolean(
                "notifications",
                false
            ) &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(
                Manifest.permission.POST_NOTIFICATIONS
            );
        }

        if (
            config.optBoolean(
                "camera",
                false
            ) &&
            checkSelfPermission(
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(
                Manifest.permission.CAMERA
            );
        }

        if (
            !permissions.isEmpty()
        ) {
            requestPermissions(
                permissions.toArray(
                    new String[0]
                ),
                APP_PERMISSION_REQUEST
            );
        }
    }

    private JSONObject loadConfig() {
        try {
            InputStream input =
                getAssets().open("appforge-fast.json");

            ByteArrayOutputStream output =
                new ByteArrayOutputStream();

            byte[] buffer =
                new byte[8192];

            while (true) {
                int read =
                    input.read(buffer);

                if (read < 0) {
                    break;
                }

                output.write(
                    buffer,
                    0,
                    read
                );
            }

            input.close();

            return new JSONObject(
                output.toString(
                    StandardCharsets.UTF_8.name()
                )
            );

        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private void configureWindow() {
        Window window =
            getWindow();

        window.setStatusBarColor(
            parseColor(
                config.optString(
                    "statusBarColor",
                    "#07101F"
                ),
                Color.BLACK
            )
        );

        window.setNavigationBarColor(
            parseColor(
                config.optString(
                    "navigationBarColor",
                    "#07101F"
                ),
                Color.BLACK
            )
        );

        if (
            config.optBoolean(
                "fullscreen",
                false
            )
        ) {
            window
                .getDecorView()
                .setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                );
        }
    }

    private int parseColor(
        String value,
        int fallback
    ) {
        try {
            return Color.parseColor(
                value
            );
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void configureWebView() {
        WebSettings settings =
            webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        if (
            config.optBoolean(
                "offlineCache",
                true
            )
        ) {
            settings.setCacheMode(
                WebSettings.LOAD_DEFAULT
            );
        } else {
            settings.setCacheMode(
                WebSettings.LOAD_NO_CACHE
            );
        }

        webView.setWebViewClient(
            new WebViewClient() {
                @Override
                public void onPageFinished(
                    WebView view,
                    String url
                ) {
                    super.onPageFinished(
                        view,
                        url
                    );

                    hideSplash();

                    installDownloadInterceptor(
                        view
                    );

                    installCaptureDetector(
                        view
                    );
                }

                @Override
                public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
                ) {
                    String url =
                        request != null &&
                        request.getUrl() != null
                            ? request.getUrl().toString()
                            : "";

                    return handlePotentialDownload(
                        url
                    );
                }

                @SuppressWarnings("deprecation")
                @Override
                public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
                ) {
                    return handlePotentialDownload(
                        url
                    );
                }
            }
        );

        webView.setWebChromeClient(
            new WebChromeClient() {

                @Override
                public void onPermissionRequest(
                    PermissionRequest request
                ) {
                    if (
                        request == null
                    ) {
                        return;
                    }

                    runOnUiThread(
                        () -> {
                            java.util.ArrayList<String> granted =
                                new java.util.ArrayList<>();

                            for (
                                String resource :
                                request.getResources()
                            ) {
                                if (
                                    PermissionRequest
                                        .RESOURCE_VIDEO_CAPTURE
                                        .equals(resource) &&
                                    config.optBoolean(
                                        "camera",
                                        false
                                    ) &&
                                    checkSelfPermission(
                                        Manifest.permission.CAMERA
                                    ) ==
                                        PackageManager
                                            .PERMISSION_GRANTED
                                ) {
                                    granted.add(
                                        resource
                                    );
                                }
                            }

                            if (
                                granted.isEmpty()
                            ) {
                                request.deny();
                            } else {
                                request.grant(
                                    granted.toArray(
                                        new String[0]
                                    )
                                );
                            }
                        }
                    );
                }

                @Override
                public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
                ) {
                    if (
                        !config.optBoolean(
                            "fileUpload",
                            true
                        )
                    ) {
                        return false;
                    }

                    if (
                        fileChooserCallback != null
                    ) {
                        fileChooserCallback.onReceiveValue(
                            null
                        );
                    }

                    fileChooserCallback =
                        filePathCallback;

                    try {
                        Intent fileIntent =
                            fileChooserParams
                                .createIntent();

                        boolean imageRequested =
                            false;

                        String[] acceptTypes =
                            fileChooserParams
                                .getAcceptTypes();

                        if (
                            acceptTypes != null
                        ) {
                            for (
                                String type :
                                acceptTypes
                            ) {
                                if (
                                    type != null &&
                                    (
                                        type.startsWith(
                                            "image/"
                                        ) ||
                                        "image/*".equals(
                                            type
                                        )
                                    )
                                ) {
                                    imageRequested =
                                        true;

                                    break;
                                }
                            }
                        }

                        boolean cameraAvailable =
                            Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.Q &&
                            imageRequested &&
                            config.optBoolean(
                                "camera",
                                false
                            ) &&
                            checkSelfPermission(
                                Manifest.permission.CAMERA
                            ) ==
                                PackageManager
                                    .PERMISSION_GRANTED;

                        if (
                            cameraAvailable
                        ) {
                            ContentValues values =
                                new ContentValues();

                            values.put(
                                MediaStore.Images.Media
                                    .DISPLAY_NAME,
                                "AppForge_" +
                                    System.currentTimeMillis() +
                                    ".jpg"
                            );

                            values.put(
                                MediaStore.Images.Media
                                    .MIME_TYPE,
                                "image/jpeg"
                            );

                            values.put(
                                MediaStore.Images.Media
                                    .RELATIVE_PATH,
                                Environment
                                    .DIRECTORY_PICTURES +
                                    "/AppForge"
                            );

                            cameraCaptureUri =
                                getContentResolver()
                                    .insert(
                                        MediaStore.Images.Media
                                            .EXTERNAL_CONTENT_URI,
                                        values
                                    );

                            if (
                                cameraCaptureUri != null
                            ) {
                                Intent cameraIntent =
                                    new Intent(
                                        MediaStore
                                            .ACTION_IMAGE_CAPTURE
                                    );

                                cameraIntent.putExtra(
                                    MediaStore.EXTRA_OUTPUT,
                                    cameraCaptureUri
                                );

                                cameraIntent.addFlags(
                                    Intent
                                        .FLAG_GRANT_READ_URI_PERMISSION |
                                    Intent
                                        .FLAG_GRANT_WRITE_URI_PERMISSION
                                );

                                boolean captureRequested =
                                    fileChooserParams
                                        .isCaptureEnabled() ||
                                    cameraCaptureRequestedByPage;

                                cameraCaptureRequestedByPage =
                                    false;

                                if (
                                    captureRequested
                                ) {
                                    startActivityForResult(
                                        cameraIntent,
                                        FILE_CHOOSER_REQUEST
                                    );
                                } else {
                                    Intent chooser =
                                        Intent.createChooser(
                                            fileIntent,
                                            "Dosya seç"
                                        );

                                    chooser.putExtra(
                                        Intent
                                            .EXTRA_INITIAL_INTENTS,
                                        new Intent[] {
                                            cameraIntent
                                        }
                                    );

                                    startActivityForResult(
                                        chooser,
                                        FILE_CHOOSER_REQUEST
                                    );
                                }

                                return true;
                            }
                        }

                        startActivityForResult(
                            fileIntent,
                            FILE_CHOOSER_REQUEST
                        );

                        return true;

                    } catch (Throwable error) {
                        fileChooserCallback =
                            null;

                        cleanupCameraCapture();

                        return false;
                    }
                }
            }
        );
        webView.addJavascriptInterface(
            new DownloadBridge(),
            "AppForgeDownloads"
        );

        webView.addJavascriptInterface(
            new CaptureHintBridge(),
            "AppForgeCaptureHint"
        );

        if (
            config.optBoolean(
                "downloads",
                false
            )
        ) {
            webView.setDownloadListener(
                (
                    url,
                    userAgent,
                    contentDisposition,
                    mimeType,
                    contentLength
                ) ->
                    enqueueDownload(
                        url,
                        userAgent,
                        contentDisposition,
                        mimeType
                    )
            );
        }

    }

    public final class CaptureHintBridge {

        @JavascriptInterface
        public void markCapture() {
            cameraCaptureRequestedByPage =
                true;
        }

        @JavascriptInterface
        public void clearCapture() {
            cameraCaptureRequestedByPage =
                false;
        }
    }

    private void installCaptureDetector(
        WebView view
    ) {
        if (
            view == null ||
            !config.optBoolean(
                "camera",
                false
            )
        ) {
            return;
        }

        String javascript =
            "(function(){" +

            "if(window.__appforgeCaptureDetectorV2)return;" +
            "window.__appforgeCaptureDetectorV2=true;" +

            "function findFileInput(target){" +

            "if(!target)return null;" +

            "if(" +
            "target.tagName==='INPUT'&&" +
            "String(target.type).toLowerCase()==='file'" +
            "){" +
            "return target;" +
            "}" +

            "var label=null;" +

            "try{" +
            "label=target.closest?target.closest('label'):null;" +
            "}catch(e){}" +

            "if(label){" +
            "var inside=label.querySelector(" +
            "'input[type=file]'" +
            ");" +

            "if(inside)return inside;" +

            "var forId=label.getAttribute('for');" +

            "if(forId){" +
            "var byId=document.getElementById(forId);" +

            "if(" +
            "byId&&" +
            "byId.tagName==='INPUT'&&" +
            "String(byId.type).toLowerCase()==='file'" +
            "){" +
            "return byId;" +
            "}" +
            "}" +
            "}" +

            "return null;" +
            "}" +

            "function inspect(target){" +

            "if(!window.AppForgeCaptureHint)return;" +

            "var input=findFileInput(target);" +

            "if(!input)return;" +

            "var accept=String(" +
            "input.getAttribute('accept')||''" +
            ").toLowerCase();" +

            "var capture=" +
            "input.hasAttribute('capture');" +

            "var image=" +
            "accept.indexOf('image')!==-1;" +

            "if(capture&&image){" +
            "window.AppForgeCaptureHint.markCapture();" +
            "}else{" +
            "window.AppForgeCaptureHint.clearCapture();" +
            "}" +
            "}" +

            "document.addEventListener(" +
            "'pointerdown'," +
            "function(e){inspect(e.target);}," +
            "true" +
            ");" +

            "document.addEventListener(" +
            "'click'," +
            "function(e){inspect(e.target);}," +
            "true" +
            ");" +

            "})();";

        view.evaluateJavascript(
            javascript,
            null
        );
    }

    public final class DownloadBridge {

        @JavascriptInterface
        public boolean isEnabled() {
            return config.optBoolean(
                "downloads",
                false
            );
        }

        @JavascriptInterface
        public String download(
            String url
        ) {
            if (
                !config.optBoolean(
                    "downloads",
                    false
                )
            ) {
                return "ERROR|Downloads kapalı";
            }

            if (
                url == null ||
                !(
                    url.startsWith("https://") ||
                    url.startsWith("http://")
                )
            ) {
                return "ERROR|Geçersiz URL";
            }

            try {
                String fileName =
                    URLUtil.guessFileName(
                        url,
                        null,
                        null
                    );

                DownloadManager.Request request =
                    new DownloadManager.Request(
                        Uri.parse(url)
                    );

                request.setTitle(
                    fileName
                );

                request.setDescription(
                    "Dosya indiriliyor..."
                );

                request.setAllowedOverMetered(
                    true
                );

                request.setAllowedOverRoaming(
                    true
                );

                request.setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                );

                DownloadManager manager =
                    (DownloadManager)
                        getSystemService(
                            Context.DOWNLOAD_SERVICE
                        );

                if (manager == null) {
                    return "ERROR|DownloadManager null";
                }

                long id =
                    manager.enqueue(
                        request
                    );

                return (
                    "OK|" +
                    id +
                    "|" +
                    fileName
                );

            } catch (Throwable error) {
                return (
                    "ERROR|" +
                    error.getClass().getSimpleName() +
                    "|" +
                    String.valueOf(
                        error.getMessage()
                    )
                );
            }
        }

        @JavascriptInterface
        public String status(
            String idText
        ) {
            try {
                long id =
                    Long.parseLong(
                        idText
                    );

                DownloadManager manager =
                    (DownloadManager)
                        getSystemService(
                            Context.DOWNLOAD_SERVICE
                        );

                if (manager == null) {
                    return "ERROR|DownloadManager null";
                }

                DownloadManager.Query query =
                    new DownloadManager.Query()
                        .setFilterById(id);

                android.database.Cursor cursor =
                    manager.query(
                        query
                    );

                if (
                    cursor == null ||
                    !cursor.moveToFirst()
                ) {
                    if (cursor != null) {
                        cursor.close();
                    }

                    return "ERROR|Kayıt bulunamadı";
                }

                int status =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS
                        )
                    );

                int reason =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_REASON
                        )
                    );

                long downloaded =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    );

                long total =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                        )
                    );

                String localUri =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_LOCAL_URI
                        )
                    );

                cursor.close();

                return (
                    "STATUS|" +
                    status +
                    "|REASON|" +
                    reason +
                    "|BYTES|" +
                    downloaded +
                    "/" +
                    total +
                    "|URI|" +
                    String.valueOf(
                        localUri
                    )
                );

            } catch (Throwable error) {
                return (
                    "ERROR|" +
                    error.getClass().getSimpleName() +
                    "|" +
                    String.valueOf(
                        error.getMessage()
                    )
                );
            }
        }
    }

    private void installDownloadInterceptor(
        WebView view
    ) {
        if (
            view == null ||
            !config.optBoolean(
                "downloads",
                false
            )
        ) {
            return;
        }

        String javascript =
            "(function(){" +
            "if(window.__appforgeDownloadInterceptor)return;" +
            "window.__appforgeDownloadInterceptor=true;" +
            "document.addEventListener('click',function(e){" +
            "var n=e.target;" +
            "while(n&&n.tagName!=='A'){n=n.parentElement;}" +
            "if(!n||!n.href)return;" +
            "var h=n.href;" +
            "var p='';" +
            "try{p=(new URL(h)).pathname.toLowerCase();}" +
            "catch(x){p=h.toLowerCase();}" +
            "var ok=/\\.(pdf|zip|apk|aab|rar|7z|doc|docx|xls|xlsx|ppt|pptx|csv|txt)$/.test(p);" +
            "if(!ok)return;" +
            "e.preventDefault();" +
            "e.stopPropagation();" +
            "if(window.AppForgeDownloads){" +
            "window.AppForgeDownloads.download(h);" +
            "}" +
            "},true);" +
            "})();";

        view.evaluateJavascript(
            javascript,
            null
        );
    }

    private boolean handlePotentialDownload(
        String url
    ) {
        if (
            !config.optBoolean(
                "downloads",
                false
            )
        ) {
            return false;
        }

        if (
            url == null ||
            url.trim().isEmpty()
        ) {
            return false;
        }

        String path;

        try {
            path =
                Uri.parse(url)
                    .getPath();

        } catch (Throwable ignored) {
            path =
                url;
        }

        if (path == null) {
            path =
                url;
        }

        String lower =
            path
                .toLowerCase()
                .trim();

        boolean downloadable =
            lower.endsWith(".pdf") ||
            lower.endsWith(".zip") ||
            lower.endsWith(".apk") ||
            lower.endsWith(".aab") ||
            lower.endsWith(".rar") ||
            lower.endsWith(".7z") ||
            lower.endsWith(".doc") ||
            lower.endsWith(".docx") ||
            lower.endsWith(".xls") ||
            lower.endsWith(".xlsx") ||
            lower.endsWith(".ppt") ||
            lower.endsWith(".pptx") ||
            lower.endsWith(".csv") ||
            lower.endsWith(".txt");

        if (!downloadable) {
            return false;
        }

        String userAgent =
            webView != null
                ? webView
                    .getSettings()
                    .getUserAgentString()
                : "";

        enqueueDownload(
            url,
            userAgent,
            null,
            null
        );

        return true;
    }

    private void enqueueDownload(
        String url,
        String userAgent,
        String contentDisposition,
        String mimeType
    ) {
        if (
            url == null ||
            !(
                url.startsWith("https://") ||
                url.startsWith("http://")
            )
        ) {
            Toast.makeText(
                this,
                "Bu indirme bağlantısı desteklenmiyor.",
                Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {
            String fileName =
                URLUtil.guessFileName(
                    url,
                    contentDisposition,
                    mimeType
                );

            DownloadManager.Request request =
                new DownloadManager.Request(
                    Uri.parse(url)
                );

            request.setTitle(
                fileName
            );

            request.setDescription(
                "Dosya indiriliyor..."
            );

            request.setAllowedOverMetered(
                true
            );

            request.setAllowedOverRoaming(
                true
            );

            request.setNotificationVisibility(
                DownloadManager.Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            if (
                mimeType != null &&
                !mimeType.trim().isEmpty()
            ) {
                request.setMimeType(
                    mimeType
                );
            }

            if (
                userAgent != null &&
                !userAgent.trim().isEmpty()
            ) {
                request.addRequestHeader(
                    "User-Agent",
                    userAgent
                );
            }

            String cookies =
                CookieManager
                    .getInstance()
                    .getCookie(url);

            if (
                cookies != null &&
                !cookies.trim().isEmpty()
            ) {
                request.addRequestHeader(
                    "Cookie",
                    cookies
                );
            }

            String referer =
                webView != null
                    ? webView.getUrl()
                    : null;

            if (
                referer != null &&
                (
                    referer.startsWith("https://") ||
                    referer.startsWith("http://")
                )
            ) {
                request.addRequestHeader(
                    "Referer",
                    referer
                );
            }

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                );
            } else {
                /*
                 * Android 8/9:
                 * Ek depolama izni istemeden uygulamanın
                 * harici Downloads klasörüne kaydet.
                 */
                request.setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                );
            }

            DownloadManager manager =
                (DownloadManager)
                    getSystemService(
                        Context.DOWNLOAD_SERVICE
                    );

            if (manager == null) {
                throw new IllegalStateException(
                    "DownloadManager kullanılamıyor."
                );
            }

            manager.enqueue(
                request
            );

            Toast.makeText(
                this,
                "İndirme başlatıldı: " + fileName,
                Toast.LENGTH_SHORT
            ).show();

        } catch (Throwable error) {
            String reason =
                error.getMessage();

            Toast.makeText(
                this,
                "İndirme hatası: " +
                error.getClass()
                    .getSimpleName() +
                (
                    reason != null
                    ? " • " + reason
                    : ""
                ),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void loadStartPage() {
        String sourceMode =
            config.optString(
                "sourceMode",
                "LOCAL"
            );

        if (
            "URL".equalsIgnoreCase(
                sourceMode
            )
        ) {
            String url =
                config.optString(
                    "webUrl",
                    ""
                );

            if (
                url.startsWith(
                    "https://"
                )
            ) {
                webView.loadUrl(
                    url
                );

                return;
            }
        }

        webView.loadUrl(
            "file:///android_asset/site/index.html"
        );
    }

    private void addSplash(
        FrameLayout root
    ) {
        FrameLayout splash =
            new FrameLayout(this);

        splash.setBackgroundColor(
            parseColor(
                config.optString(
                    "backgroundColor",
                    "#07101F"
                ),
                Color.BLACK
            )
        );

        LinearLayout content =
            new LinearLayout(this);

        content.setOrientation(
            LinearLayout.VERTICAL
        );

        content.setGravity(
            Gravity.CENTER
        );

        if (
            config.optBoolean(
                "hasCustomIcon",
                false
            )
        ) {
            try {
                ImageView icon =
                    new ImageView(this);

                icon.setImageDrawable(
                    getApplicationInfo()
                        .loadIcon(
                            getPackageManager()
                        )
                );

                icon.setScaleType(
                    ImageView.ScaleType.FIT_CENTER
                );

                LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(
                        dp(112),
                        dp(112)
                    );

                iconParams.bottomMargin =
                    dp(20);

                content.addView(
                    icon,
                    iconParams
                );

            } catch (
                Throwable ignored
            ) {
            }
        }

        String splashText =
            config.optString(
                "splashText",
                ""
            ).trim();

        if (
            splashText.isEmpty()
        ) {
            splashText =
                config.optString(
                    "appName",
                    "AppForge App"
                );
        }

        if (
            !splashText.isEmpty()
        ) {
            TextView title =
                new TextView(this);

            title.setText(
                splashText
            );

            title.setTextColor(
                Color.WHITE
            );

            title.setTextSize(
                21f
            );

            title.setGravity(
                Gravity.CENTER
            );

            title.setPadding(
                dp(20),
                dp(8),
                dp(20),
                dp(8)
            );

            content.addView(
                title,
                new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            );
        }

        FrameLayout.LayoutParams contentParams =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            );

        contentParams.gravity =
            Gravity.CENTER;

        splash.addView(
            content,
            contentParams
        );

        root.addView(
            splash,
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        );

        splashView =
            splash;

        splashStartedAt =
            System.currentTimeMillis();
    }

    private void hideSplash() {
        final View current =
            splashView;

        if (
            current == null
        ) {
            return;
        }

        long elapsed =
            System.currentTimeMillis()
                - splashStartedAt;

        long minimum =
            450L;

        if (
            elapsed < minimum
        ) {
            current.postDelayed(
                this::hideSplash,
                minimum - elapsed
            );

            return;
        }

        splashView =
            null;

        current.setClickable(
            false
        );

        current.setFocusable(
            false
        );

        if (
            current.getParent()
            instanceof android.view.ViewGroup
        ) {
            ((android.view.ViewGroup)
                current.getParent())
                .removeView(
                    current
                );
        }
    }

    private int dp(
        int value
    ) {
        return Math.round(
            value *
            getResources()
                .getDisplayMetrics()
                .density
        );
    }

    private void addWatermark(
        FrameLayout root
    ) {
        TextView watermark =
            new TextView(this);

        watermark.setText(
            "Built with AppForge"
        );

        watermark.setTextColor(
            Color.WHITE
        );

        watermark.setBackgroundColor(
            0xAA111827
        );

        watermark.setPadding(
            24,
            12,
            24,
            12
        );

        FrameLayout.LayoutParams params =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            );

        params.gravity =
            Gravity.BOTTOM
                | Gravity.END;

        params.setMargins(
            16,
            16,
            16,
            32
        );

        root.addView(
            watermark,
            params
        );
    }

    private void cleanupCameraCapture() {
        if (
            cameraCaptureUri == null
        ) {
            return;
        }

        try {
            getContentResolver()
                .delete(
                    cameraCaptureUri,
                    null,
                    null
                );
        } catch (
            Throwable ignored
        ) {
        }

        cameraCaptureUri =
            null;
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        );

        if (
            requestCode !=
            FILE_CHOOSER_REQUEST
        ) {
            return;
        }

        ValueCallback<Uri[]> callback =
            fileChooserCallback;

        fileChooserCallback =
            null;

        if (
            callback == null
        ) {
            return;
        }

        Uri[] result;

        boolean cameraResult =
            resultCode ==
                RESULT_OK &&
            cameraCaptureUri != null &&
            (
                data == null ||
                data.getData() == null
            );

        if (
            cameraResult
        ) {
            result =
                new Uri[] {
                    cameraCaptureUri
                };

            cameraCaptureUri =
                null;

        } else {
            result =
                WebChromeClient
                    .FileChooserParams
                    .parseResult(
                        resultCode,
                        data
                    );

            cleanupCameraCapture();
        }

        callback.onReceiveValue(
            result
        );
    }

    @Override
    public void onBackPressed() {
        if (
            webView != null
            && webView.canGoBack()
        ) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (
            webView != null
        ) {
            webView.destroy();
        }

        super.onDestroy();
    }
}

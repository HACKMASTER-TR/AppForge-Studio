package com.appforge.runtime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class FastActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 7101;

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;

    private JSONObject config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        config = loadConfig();

        configureWindow();

        FrameLayout root = new FrameLayout(this);

        webView = new WebView(this);

        configureWebView();

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

        setContentView(root);

        loadStartPage();
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
            new WebViewClient()
        );

        webView.setWebChromeClient(
            new WebChromeClient() {

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
                        Intent intent =
                            fileChooserParams
                                .createIntent();

                        startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                        );

                        return true;

                    } catch (Throwable error) {
                        fileChooserCallback =
                            null;

                        return false;
                    }
                }
            }
        );
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

        Uri[] result =
            WebChromeClient
                .FileChooserParams
                .parseResult(
                    resultCode,
                    data
                );

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

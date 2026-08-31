package com.appforge.features.qr;

import android.app.Activity;
import android.webkit.WebView;

import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

public final class FastQrRuntime {

    private FastQrRuntime() {
    }

    private static void dispatch(
        Activity activity,
        WebView webView,
        String eventName,
        JSONObject detail
    ) {
        if (
            activity == null ||
            webView == null
        ) {
            return;
        }

        final String script =
            "window.dispatchEvent(" +
            "new CustomEvent(" +
            JSONObject.quote(eventName) +
            ",{detail:" +
            detail.toString() +
            "}" +
            ")" +
            ");";

        activity.runOnUiThread(
            () ->
                webView.evaluateJavascript(
                    script,
                    null
                )
        );
    }


    private static JSONObject messagePayload(
        String message
    ) {
        JSONObject payload =
            new JSONObject();

        try {
            payload.put(
                "message",
                message != null
                    ? message
                    : ""
            );
        } catch (
            Throwable ignored
        ) {
        }

        return payload;
    }


    public static void start(
        Activity activity,
        WebView webView
    ) {
        if (
            activity == null ||
            webView == null
        ) {
            return;
        }

        activity.runOnUiThread(
            () -> {
                try {
                    GmsBarcodeScannerOptions options =
                        new GmsBarcodeScannerOptions
                            .Builder()
                            .enableAutoZoom()
                            .build();

                    GmsBarcodeScanning
                        .getClient(
                            activity,
                            options
                        )
                        .startScan()
                        .addOnSuccessListener(
                            barcode -> {
                                JSONObject payload =
                                    new JSONObject();

                                try {
                                    payload.put(
                                        "rawValue",
                                        barcode.getRawValue() != null
                                            ? barcode
                                                .getRawValue()
                                                .substring(
                                                    0,
                                                    Math.min(
                                                        8192,
                                                        barcode
                                                            .getRawValue()
                                                            .length()
                                                    )
                                                )
                                            : ""
                                    );

                                    payload.put(
                                        "displayValue",
                                        barcode.getDisplayValue() != null
                                            ? barcode
                                                .getDisplayValue()
                                                .substring(
                                                    0,
                                                    Math.min(
                                                        8192,
                                                        barcode
                                                            .getDisplayValue()
                                                            .length()
                                                    )
                                                )
                                            : ""
                                    );

                                    payload.put(
                                        "format",
                                        barcode.getFormat()
                                    );

                                    payload.put(
                                        "valueType",
                                        barcode.getValueType()
                                    );

                                } catch (
                                    Throwable ignored
                                ) {
                                }

                                dispatch(
                                    activity,
                                    webView,
                                    "appforge-scan-result",
                                    payload
                                );
                            }
                        )
                        .addOnCanceledListener(
                            () ->
                                dispatch(
                                    activity,
                                    webView,
                                    "appforge-scan-cancelled",
                                    new JSONObject()
                                )
                        )
                        .addOnFailureListener(
                            error ->
                                dispatch(
                                    activity,
                                    webView,
                                    "appforge-scan-error",
                                    messagePayload(
                                        error != null
                                            ? error.getMessage()
                                            : "Tarama başarısız."
                                    )
                                )
                        );

                } catch (
                    Throwable error
                ) {
                    dispatch(
                        activity,
                        webView,
                        "appforge-scan-error",
                        messagePayload(
                            error.getMessage() != null
                                ? error.getMessage()
                                : error
                                    .getClass()
                                    .getSimpleName()
                        )
                    );
                }
            }
        );
    }
}

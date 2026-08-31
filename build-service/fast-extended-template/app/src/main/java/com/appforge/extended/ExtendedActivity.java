package com.appforge.extended;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

import com.android.billingclient.api.BillingClient;

import com.google.android.gms.ads.MobileAds;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import com.google.firebase.analytics.FirebaseAnalytics;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import com.google.android.ump.UserMessagingPlatform;

public final class ExtendedActivity
    extends Activity {

    @Override
    protected void onCreate(
        Bundle savedInstanceState
    ) {
        super.onCreate(
            savedInstanceState
        );

        /*
         * Bu ilk Extended runtime kontrolüdür.
         *
         * Burada SDK sınıflarına referans verilmesi,
         * Docker image build sırasında tüm SDK'ların
         * gerçekten compile + dex edilebildiğini
         * doğrular.
         */

        Class<?>[] sdkClasses =
            new Class<?>[] {

                BillingClient.class,

                MobileAds.class,

                GmsBarcodeScanning.class,

                FirebaseAnalytics.class,

                FirebaseCrashlytics.class,

                UserMessagingPlatform.class
            };

        WebView web =
            new WebView(this);

        web.getSettings()
            .setJavaScriptEnabled(
                true
            );

        web.loadData(
            "<html>" +
            "<body style='" +
            "background:#07101f;" +
            "color:white;" +
            "font-family:sans-serif;" +
            "text-align:center;" +
            "padding-top:40vh" +
            "'>" +
            "<h2>AppForge FAST EXTENDED</h2>" +
            "<p>SDK runtime hazır: " +
            sdkClasses.length +
            "</p>" +
            "</body>" +
            "</html>",
            "text/html",
            "UTF-8"
        );

        setContentView(
            web
        );
    }
}

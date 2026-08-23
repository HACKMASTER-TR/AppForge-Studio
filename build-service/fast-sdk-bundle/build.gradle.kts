plugins {
    base
}

val fastSdk =
    configurations.create(
        "fastSdk"
    ) {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {

    // ===============================
    // QR / BARCODE
    // ===============================

    add(
        "fastSdk",
        "com.google.android.gms:" +
            "play-services-code-scanner:" +
            "16.1.0"
    )

    // ===============================
    // ADMOB
    // ===============================

    add(
        "fastSdk",
        "com.google.android.gms:" +
            "play-services-ads:" +
            "25.4.0"
    )

    // ===============================
    // UMP CONSENT
    // ===============================

    add(
        "fastSdk",
        "com.google.android.ump:" +
            "user-messaging-platform:" +
            "4.0.0"
    )

    // ===============================
    // GOOGLE PLAY BILLING
    // ===============================

    add(
        "fastSdk",
        "com.android.billingclient:" +
            "billing-ktx:" +
            "9.1.0"
    )

    // ===============================
    // FIREBASE
    // ===============================

    add(
        "fastSdk",
        "com.google.firebase:" +
            "firebase-analytics:" +
            "23.2.0"
    )

    add(
        "fastSdk",
        "com.google.firebase:" +
            "firebase-crashlytics:" +
            "20.1.0"
    )
}

tasks.register<Sync>(
    "prepareFastSdk"
) {

    from(
        fastSdk
    )

    into(
        layout
            .buildDirectory
            .dir(
                "bundle"
            )
    )

    doLast {

        val output =
            layout
                .buildDirectory
                .dir(
                    "bundle"
                )
                .get()
                .asFile

        val files =
            output
                .listFiles()
                ?.map {
                    it.name
                }
                .orEmpty()

        println()
        println(
            "===== APPFORGE FAST SDK ====="
        )

        files
            .sorted()
            .forEach {
                println(it)
            }

        println(
            "============================="
        )

        check(
            files.any {
                it.contains(
                    "play-services-ads"
                )
            }
        ) {
            "AdMob SDK bulunamadı."
        }

        check(
            files.any {
                it.contains(
                    "user-messaging-platform"
                )
            }
        ) {
            "UMP SDK bulunamadı."
        }

        check(
            files.any {
                it.contains(
                    "billing"
                )
            }
        ) {
            "Billing SDK bulunamadı."
        }

        check(
            files.any {
                it.contains(
                    "code-scanner"
                )
            }
        ) {
            "Code Scanner SDK bulunamadı."
        }

        check(
            files.any {
                it.contains(
                    "firebase-analytics"
                )
            }
        ) {
            "Firebase Analytics bulunamadı."
        }

        check(
            files.any {
                it.contains(
                    "firebase-crashlytics"
                )
            }
        ) {
            "Firebase Crashlytics bulunamadı."
        }

        println(
            "✅ Tüm FAST Extended SDK'ları hazır."
        )
    }
}


// APPFORGE FAST QR FEATURE PACK
// ============================================================
// Yalnız QR / Barkod tarayıcı için gereken dependency ağını
// ayrı klasöre çözer.
// ============================================================

val fastQrSdk =
    configurations.create(
        "fastQrSdk"
    ) {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    add(
        "fastQrSdk",
        "com.google.android.gms:" +
            "play-services-code-scanner:" +
            "16.1.0"
    )
}

tasks.register<Sync>(
    "prepareFastQrSdk"
) {
    from(
        fastQrSdk
    )

    into(
        layout
            .buildDirectory
            .dir(
                "qr-bundle"
            )
    )

    doLast {
        val output =
            layout
                .buildDirectory
                .dir(
                    "qr-bundle"
                )
                .get()
                .asFile

        val files =
            output
                .listFiles()
                ?.map {
                    it.name
                }
                .orEmpty()
                .sorted()

        println()
        println(
            "===== APPFORGE FAST QR SDK ====="
        )

        files.forEach {
            println(it)
        }

        println(
            "================================"
        )

        check(
            files.any {
                it.contains(
                    "play-services-code-scanner"
                )
            }
        ) {
            "QR Code Scanner SDK bulunamadı."
        }

        println(
            "✅ FAST QR dependency paketi hazır."
        )
    }
}

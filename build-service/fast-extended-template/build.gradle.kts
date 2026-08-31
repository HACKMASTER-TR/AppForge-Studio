plugins {
    id("com.android.application") version "9.1.1" apply false

    // Firebase kullanılan gerçek AppForge build'leri için
    // plugin dosyalarını Worker image cache'ine önceden al.
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}

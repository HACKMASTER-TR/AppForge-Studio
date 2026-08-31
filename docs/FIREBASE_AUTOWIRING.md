# Firebase Auto-Wiring

When Analytics, Crashlytics, or Firebase Cloud Messaging is enabled and `google-services.json` is uploaded, AppForge generates:

Project-level plugins:
- `com.google.gms.google-services` 4.5.0
- `com.google.firebase.crashlytics` 3.0.7 when Crashlytics is enabled

App-level plugins:
- `com.google.gms.google-services`
- `com.google.firebase.crashlytics` when enabled

Firebase Android BoM:
- `34.17.0`

The uploaded Firebase config is copied to:

`app/google-services.json`

The package name in the Firebase config must match the generated Android application ID.

## Firebase Cloud Messaging

When Cloud Messaging is enabled, AppForge also generates:

- `com.google.firebase:firebase-messaging`
- `AppForgeFirebaseMessagingService`
- `com.google.firebase.MESSAGING_EVENT` service registration
- Android 13+ notification permission flow

The generated service stores the latest FCM registration token in the
app-private `appforge_fcm` SharedPreferences. Notification messages are
handled by Firebase while the app is in the background; foreground and data
messages are handled by `AppForgeFirebaseMessagingService`.

For AppForge Studio itself, `google-services.json` stays out of Git.
CI restores it from the GitHub Actions secret:

`APPFORGE_FIREBASE_GOOGLE_SERVICES_B64`

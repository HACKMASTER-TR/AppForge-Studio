# Firebase Auto-Wiring

When Analytics or Crashlytics is enabled and `google-services.json` is uploaded, AppForge v0.9 generates:

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

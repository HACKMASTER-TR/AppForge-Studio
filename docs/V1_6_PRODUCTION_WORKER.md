# AppForge v1.6 — Production Android Worker

v1.6 adds a separate Android worker image.

## API image

`Dockerfile.api`

Contains Node.js and the Build Service API only.

It intentionally does not pretend to be an Android build image.

## Android worker image

`Dockerfile.worker`

Pinned toolchain:
- JDK 17
- Gradle 9.3.1
- Android command-line tools 15859902
- Android SDK Platform 37
- Android Build Tools 36.0.0
- Platform Tools

The Android command-line tools ZIP is verified using the published SHA-256 checksum.

Gradle is downloaded together with its published `.sha256` file and verified during image creation.

## Android SDK license

The worker image does not silently accept the Android SDK license.

After reviewing/accepting the Android SDK license, build with:

```bash
ANDROID_SDK_LICENSE_ACCEPTED=true docker compose build worker
```

Then:

```bash
docker compose up -d
```

## Toolchain doctor

Inside a configured environment:

```bash
npm run doctor
```

Strict mode:

```bash
npm run doctor:strict
```

Checks:
- JDK major version
- Gradle minimum version
- sdkmanager
- android-37 platform
- Build Tools 36.0.0 / aapt2

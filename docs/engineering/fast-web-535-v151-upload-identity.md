# AppForge V15.1 — explicit upload identity (candidate, no Play publication)

Source: V15 `0b6c754a8625c33e47e89f0e57bd230a64056801`.

- **Debug default unchanged:** V15's AndroidKeyStore development AAB identity is retained when signingMode=DEBUG. APK-only fast build retains its original v2/v3 signer.
- **Custom AAB / APK+AAB:** only an explicit CUSTOM choice with a previously imported `.p12`/`.pfx` in the AppForge Keystore Vault is eligible. Generated private-only vault identities are rejected until a separately backed-up PKCS12 is imported. No silent fallback to debug signer.
- **Key verification:** file must be inside the private vault and match its registered SHA-256; PKCS12 must load using supplied passwords and selected alias; RSA key/certificate must match and certificate be in its validity window. The same key signs both artifacts. APK v2/v3 certificate is compared byte-for-byte to signed AAB certificate.
- **Atomic failure:** an exception, missing key, wrong password, or cancellation must not publish partially successful APK+AAB output. Temporary unsigned candidate is deleted; private workspace is isolated by job.
- **Required acceptance:** full JS contracts, Android JVM tests, Android Debug CI, independent jarsigner/apksig + bundletool checks, physical offline custom-signing and wrong-password/cancellation tests, and manual release review.
- **Not Play ready by inference:** A valid AAB or PKCS12 does not confirm Google Play enrollment. Upload certificate must be verified against the Play Console configuration of each application. Store backups and credentials outside app storage in user custody; this change neither exports private keys nor uploads them to a service.
- **Signing continuity:** do not rotate signing identities, delete old keystores or release to Play without certificate and account review. Main, installed 534, private user projects, Release and Play are out of scope.

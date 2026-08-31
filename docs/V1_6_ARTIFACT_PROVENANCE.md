# Artifact Provenance

APK/AAB output references now contain:
- storage driver
- storage key
- file name
- SHA-256
- size in bytes

Successful build rows also save:
- worker ID
- build duration
- artifact manifest

Endpoint:

`GET /api/builds/:id/artifacts`

This can be used to verify downloaded build files or track which worker produced them.

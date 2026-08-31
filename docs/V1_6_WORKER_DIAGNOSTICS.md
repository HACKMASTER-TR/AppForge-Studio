# Worker Diagnostics

Workers are registered per execution slot.

This fixes an older mismatch where the base worker ID was registered while slot heartbeats used IDs such as `worker#1`.

Each worker slot now stores:
- capabilities
- worker version
- hostname
- toolchain_ok
- diagnostics JSON
- last error
- last seen timestamp

Admin endpoint:

`GET /api/admin/workers`

The Admin page displays current worker toolchain state.

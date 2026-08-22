# v1.5 Upload and ZIP Security

Server-side ZIP extraction now enforces:
- max 5,000 entries
- max 250 MB total uncompressed size
- max 50 MB per entry
- max 240-character path
- max 20 path components
- traversal rejection
- symbolic-link rejection
- declared and actual uncompressed size checks

The Android importer applies comparable entry/path/size limits and imports into a temporary directory before replacing the active project.

Other validation:
- app icon must be a real PNG and <= 5 MB
- google-services.json must be valid JSON
- Firebase Android package name must match the generated application ID

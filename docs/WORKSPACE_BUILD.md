# Workspace Build

Web Studio projects can now be built without manually uploading a ZIP.

Endpoint:

`POST /api/projects/:id/builds`

The server:
1. reads project files from PostgreSQL,
2. generates a ZIP,
3. runs preflight,
4. checks the v1.3 build cache,
5. queues the build or returns an immediate cache hit.

Current Web Studio workspace builds use debug signing by default. Custom release keystore builds remain available through the existing multipart Build API.

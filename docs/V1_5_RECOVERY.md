# Project Recovery

v1.5 can restore a previous project revision.

Endpoint:

`POST /api/projects/:id/revisions/:revisionId/restore`

Before restore, AppForge automatically creates a backup revision of the current workspace.

Web Studio also adds full-project text search:

`GET /api/projects/:id/search?q=...`
